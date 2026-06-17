package com.omarea.krscript.parser

import android.graphics.Color
import android.text.Layout
import android.util.Xml
import com.omarea.krscript.config.Suffix2Mime
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ActionParamInfo
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.GroupNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.PickerNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.common.model.SelectItem
import com.omarea.krscript.model.SwitchNode
import com.omarea.krscript.model.TextNode
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Locale

/**
 * RU: Чистый XML-парсер конфигурации страницы KrScript.
 *
 * Отличия от старого `PageConfigReader`:
 *   - НЕ выполняет shell-команды (всё, что требовало shell, делегируется в [resolver]);
 *   - НЕ показывает Toast и не пишет в Log;
 *   - НЕ извлекает ресурсы (это делает отдельный шаг `RuntimeBinder`);
 *   - НЕ зависит от `Context`, `Handler`, `Looper` или `Toast`;
 *   - использует только `DynamicValueResolver` для разрешения `desc-sh`/`summary-sh`/
 *     `support`/`visible`/`<getstate>`/`<setstate>`.
 *
 * Это позволяет запускать парсер в unit-тестах JVM без Robolectric.
 *
 * EN: Pure XML parser for KrScript page configuration.
 *
 * Differences from the legacy `PageConfigReader`:
 *   - does NOT execute shell (everything shell-related is delegated to [resolver]);
 *   - does NOT show Toast or log;
 *   - does NOT extract resources (that is a separate `RuntimeBinder` step);
 *   - does NOT depend on `Context`, `Handler`, `Looper` or `Toast`;
 *   - uses only [DynamicValueResolver] for `desc-sh`/`summary-sh`/`support`/`visible`/
 *     `<getstate>`/`<setstate>` resolution.
 *
 * This makes the parser runnable in plain JVM unit tests without Robolectric.
 */
class PageConfigParser(
    private val resolver: DynamicValueResolver = NoopDynamicValueResolver()
) {

    /**
     * RU: Разбирает XML из [stream] и возвращает дерево узлов.
     *
     * @param pageConfigAbsPath абсолютный путь к конфигурации — используется только
     *   как метаданные узлов (для последующего resolution ресурсных путей).
     *
     * EN: Parses the XML from [stream] and returns the node tree.
     */
    fun parse(stream: InputStream, pageConfigAbsPath: String = ""): List<NodeInfoBase> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "utf-8")
        return parseWith(parser, pageConfigAbsPath)
    }

    // Visible for testing — allows injecting a fake parser.
    internal fun parseWith(parser: XmlPullParser, pageConfigAbsPath: String): List<NodeInfoBase> {
        val mainList = ArrayList<NodeInfoBase>()
        var action: ActionNode? = null
        var switch: SwitchNode? = null
        var picker: PickerNode? = null
        var group: GroupNode? = null
        var page: PageNode? = null
        var text: TextNode? = null
        var actionParamInfos: ArrayList<ActionParamInfo>? = null
        var actionParamInfo: ActionParamInfo? = null
        var isRootNode = true

        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            when (type) {
                XmlPullParser.START_TAG -> {
                    if ("group" == parser.name) {
                        if (group != null && group!!.supported) {
                            mainList.add(group!!)
                        }
                        group = groupNode(parser, pageConfigAbsPath)
                    } else if (group != null && !group!!.supported) {
                        // Skip everything inside an unsupported group.
                    } else {
                        when (parser.name) {
                            "page" -> {
                                if (!isRootNode) {
                                    page = clickableNode(PageNode(pageConfigAbsPath), parser) as PageNode?
                                    if (page != null) {
                                        page = pageNode(page, parser)
                                    }
                                }
                            }
                            "action" -> {
                                action = runnableNode(ActionNode(pageConfigAbsPath), parser) as ActionNode?
                            }
                            "switch" -> {
                                switch = runnableNode(SwitchNode(pageConfigAbsPath), parser) as SwitchNode?
                            }
                            "picker" -> {
                                picker = runnableNode(PickerNode(pageConfigAbsPath), parser) as PickerNode?
                                if (picker != null) {
                                    pickerNode(picker!!, parser)
                                }
                            }
                            "text" -> {
                                text = mainNode(TextNode(pageConfigAbsPath), parser) as TextNode?
                            }
                            else -> {
                                if (page != null) {
                                    tagStartInPage(page, parser)
                                } else if (action != null) {
                                    tagStartInAction(
                                        action,
                                        parser,
                                        actionParamInfos,
                                        actionParamInfo
                                    ) { newInfos, newInfo ->
                                        actionParamInfos = newInfos
                                        actionParamInfo = newInfo
                                    }
                                } else if (switch != null) {
                                    tagStartInSwitch(switch, parser)
                                } else if (picker != null) {
                                    tagStartInPicker(picker, parser)
                                } else if (text != null) {
                                    tagStartInText(text, parser)
                                }
                                // NOTE: `<resource>` extraction is intentionally NOT
                                // done here — it is a runtime side effect and belongs
                                // to the RuntimeBinder step.
                            }
                        }
                    }
                    isRootNode = false
                }
                XmlPullParser.END_TAG -> {
                    if ("group" == parser.name) {
                        if (group != null && group!!.supported) {
                            mainList.add(group!!)
                        }
                        group = null
                    } else if (group != null) {
                        when (parser.name) {
                            "page" -> {
                                page?.let { group!!.children.add(it) }
                                page = null
                            }
                            "action" -> {
                                tagEndInAction(action, actionParamInfos) { actionParamInfos = null }
                                action?.let { group!!.children.add(it) }
                                action = null
                            }
                            "switch" -> {
                                tagEndInSwitch(switch)
                                switch?.let { group!!.children.add(it) }
                                switch = null
                            }
                            "picker" -> {
                                tagEndInPicker(picker)
                                picker?.let { group!!.children.add(it) }
                                picker = null
                            }
                            "text" -> {
                                text?.let { group!!.children.add(it) }
                                text = null
                            }
                        }
                    } else {
                        when (parser.name) {
                            "page" -> {
                                page?.let { mainList.add(it) }
                                page = null
                            }
                            "action" -> {
                                tagEndInAction(action, actionParamInfos) { actionParamInfos = null }
                                action?.let { mainList.add(it) }
                                action = null
                            }
                            "switch" -> {
                                tagEndInSwitch(switch)
                                switch?.let { mainList.add(it) }
                                switch = null
                            }
                            "picker" -> {
                                tagEndInPicker(picker)
                                picker?.let { mainList.add(it) }
                                picker = null
                            }
                            "text" -> {
                                text?.let { mainList.add(it) }
                                text = null
                            }
                        }
                    }
                }
            }
            type = parser.next()
        }

        return mainList
    }

    private fun resolveText(value: String): String = resolver.resolveText(value)

    private fun executeShell(script: String): String = resolver.resolveShellValue(script)

    private fun isSupported(script: String): Boolean = resolver.isSupported(script)

    private fun tagStartInAction(
        action: ActionNode,
        parser: XmlPullParser,
        currentInfos: ArrayList<ActionParamInfo>?,
        currentInfo: ActionParamInfo?,
        updateState: (ArrayList<ActionParamInfo>?, ActionParamInfo?) -> Unit
    ) {
        var actionParamInfos = currentInfos
        var actionParamInfo = currentInfo
        when (parser.name) {
            "title" -> action.title = resolveText(parser.nextText())
            "desc" -> descNode(action, parser)
            "summary" -> summaryNode(action, parser)
            "script", "set", "setstate" -> action.setState = parser.nextText().trim()
            "lock", "lock-state" -> action.lockShell = parser.nextText()
            "param" -> {
                if (actionParamInfos == null) {
                    actionParamInfos = ArrayList()
                }
                actionParamInfo = ActionParamInfo()
                val local = actionParamInfo!!
                for (i in 0 until parser.attributeCount) {
                    val attrName = parser.getAttributeName(i)
                    val attrValue = parser.getAttributeValue(i)
                    when (attrName) {
                        "name" -> local.name = attrValue
                        "label" -> local.label = resolveText(attrValue)
                        "placeholder" -> local.placeholder = resolveText(attrValue)
                        "title" -> local.title = resolveText(attrValue)
                        "desc" -> local.desc = resolveText(attrValue)
                        "value" -> local.value = resolveText(attrValue)
                        "type" -> local.type = attrValue.toLowerCase(Locale.ROOT).trim { it <= ' ' }
                        "suffix" -> {
                            val suffix = attrValue.toLowerCase(Locale.ROOT).trim { it <= ' ' }
                            if (local.mime.isEmpty()) {
                                local.mime = Suffix2Mime().toMime(suffix)
                            }
                            local.suffix = suffix
                        }
                        "mime" -> local.mime = attrValue.toLowerCase(Locale.ROOT)
                        "readonly" -> {
                            val v = attrValue.toLowerCase(Locale.ROOT).trim { it <= ' ' }
                            local.readonly = (v == "readonly" || v == "true" || v == "1")
                        }
                        "maxlength" -> local.maxLength = Integer.parseInt(attrValue)
                        "min" -> local.min = Integer.parseInt(attrValue)
                        "max" -> local.max = Integer.parseInt(attrValue)
                        "required" -> local.required =
                            attrValue == "true" || attrValue == "1" || attrValue == "required"
                        "value-sh", "value-su" -> local.valueShell = attrValue
                        "options-sh", "option-sh", "options-su" -> {
                            if (local.options == null) local.options = ArrayList()
                            local.optionsSh = attrValue
                        }
                        "support", "visible" -> {
                            if (!isSupported(attrValue)) {
                                local.supported = false
                            }
                        }
                        "multiple" -> local.multiple =
                            attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                        "editable" -> local.editable =
                            attrValue == "editable" || attrValue == "true" || attrValue == "1"
                        "separator" -> local.separator = attrValue
                    }
                }
                if (actionParamInfo.supported &&
                    !actionParamInfo.name.isNullOrEmpty()
                ) {
                    actionParamInfos!!.add(actionParamInfo)
                }
            }
            "option" -> {
                if (actionParamInfo != null) {
                    if (actionParamInfo.options == null) {
                        actionParamInfo.options = ArrayList()
                    }
                    val option = SelectItem()
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "val" || parser.getAttributeName(i) == "value") {
                            option.value = parser.getAttributeValue(i)
                        }
                    }
                    option.title = resolveText(parser.nextText())
                    if (option.value == null) option.value = option.title
                    actionParamInfo.options!!.add(option)
                }
            }
            // `<resource>` intentionally ignored — handled by RuntimeBinder.
        }
        updateState(actionParamInfos, actionParamInfo)
    }

    private fun tagEndInAction(
        action: ActionNode?,
        infos: ArrayList<ActionParamInfo>?,
        cleanup: () -> Unit
    ) {
        if (action != null) {
            if (action.setState == null) action.setState = ""
            action.params = infos
            cleanup()
        }
    }

    private fun tagStartInPage(node: PageNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> node.title = resolveText(parser.nextText())
            "desc" -> descNode(node, parser)
            "summary" -> summaryNode(node, parser)
            "html" -> node.onlineHtmlPage = parser.nextText()
            "config" -> node.pageConfigPath = parser.nextText()
            "handler-sh", "handler", "set", "getstate", "script" -> node.pageHandlerSh = parser.nextText()
            "lock", "lock-state" -> node.lockShell = parser.nextText()
            "option", "page-option", "menu", "menu-item" -> {
                val option = runnableNode(PageMenuOption(node.currentPageConfigPath), parser) as PageMenuOption?
                if (option != null) {
                    for (i in 0 until parser.attributeCount) {
                        when (parser.getAttributeName(i)) {
                            "type" -> option.type = parser.getAttributeValue(i)
                            "style" -> option.isFab = parser.getAttributeValue(i) == "fab"
                            "suffix" -> {
                                val suffix = parser.getAttributeValue(i).toLowerCase(Locale.ROOT).trim { it <= ' ' }
                                if (option.mime.isEmpty()) {
                                    option.mime = Suffix2Mime().toMime(suffix)
                                }
                                option.suffix = suffix
                            }
                            "mime" -> option.mime = parser.getAttributeValue(i).toLowerCase(Locale.ROOT)
                        }
                    }
                    option.title = resolveText(parser.nextText())
                    if (option.key.isEmpty()) option.key = option.title
                    if (node.pageMenuOptions == null) node.pageMenuOptions = ArrayList()
                    node.pageMenuOptions?.add(option)
                }
            }
            // `<resource>` intentionally ignored — handled by RuntimeBinder.
        }
    }

    private fun tagStartInSwitch(switchNode: SwitchNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> switchNode.title = resolveText(parser.nextText())
            "desc" -> descNode(switchNode, parser)
            "summary" -> summaryNode(switchNode, parser)
            "get", "getstate" -> switchNode.getState = parser.nextText()
            "set", "setstate" -> switchNode.setState = parser.nextText()
            "lock", "lock-state" -> switchNode.lockShell = parser.nextText()
            // `<resource>` intentionally ignored — handled by RuntimeBinder.
        }
    }

    private fun groupNode(parser: XmlPullParser, pageConfigAbsPath: String): GroupNode {
        val groupInfo = GroupNode(pageConfigAbsPath)
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            when (attrName) {
                "key", "index", "id" -> groupInfo.key = attrValue.trim()
                "title" -> groupInfo.title = resolveText(attrValue)
                "support", "visible" -> groupInfo.supported = isSupported(attrValue)
            }
        }
        return groupInfo
    }

    private fun clickableNode(clickableNode: ClickableNode, parser: XmlPullParser): ClickableNode? {
        val node = (mainNode(clickableNode, parser) as ClickableNode?) ?: return null
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "lock", "lock-state", "locked" -> node.locked =
                    (attrValue == "1" || attrValue == "true" || attrValue == "locked")
                "min-sdk", "sdk-min" -> node.minSdkVersion = attrValue.trim().toInt()
                "max-sdk", "sdk-max" -> node.maxSdkVersion = attrValue.trim().toInt()
                "target-sdk", "sdk-target" -> node.targetSdkVersion = attrValue.trim().toInt()
                "icon", "icon-path" -> node.iconPath = attrValue.trim()
                "logo", "logo-path" -> node.logoPath = attrValue.trim()
                "allow-shortcut" -> node.allowShortcut =
                    attrValue == "allow" ||
                    attrValue == "allow-shortcut" ||
                    attrValue == "true" ||
                    attrValue == "1"
            }
        }
        if (node.key.isNotEmpty() && node.key.startsWith("@") && node.allowShortcut == null) {
            node.allowShortcut = false
        }
        return node
    }

    private fun runnableNode(node: RunnableNode, parser: XmlPullParser): RunnableNode? {
        val clickable = clickableNode(node, parser) as RunnableNode? ?: return null
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "confirm" -> clickable.confirm =
                    (attrValue == "confirm" || attrValue == "true" || attrValue == "1")
                "warn", "warning" -> clickable.warning = attrValue
                "auto-off", "auto-close" -> clickable.autoOff =
                    (attrValue == "auto-close" || attrValue == "auto-off" ||
                        attrValue == "true" || attrValue == "1")
                "auto-finish" -> clickable.autoFinish =
                    (attrValue == "auto-finish" || attrValue == "true" || attrValue == "1")
                "interruptible", "interruptable" -> clickable.interruptable = (
                    attrValue.isEmpty() || attrValue == "interruptable" ||
                    attrValue == "true" || attrValue == "1"
                    )
                "reload-page" -> {
                    if (attrValue == "reload-page" || attrValue == "reload" ||
                        attrValue == "page" || attrValue == "true" || attrValue == "1"
                    ) {
                        clickable.reloadPage = true
                    }
                }
                "reload" -> {
                    if (attrValue == "reload-page" || attrValue == "reload" ||
                        attrValue == "page" || attrValue == "true" || attrValue == "1"
                    ) {
                        clickable.reloadPage = true
                    } else if (attrValue.isNotEmpty()) {
                        clickable.updateBlocks = attrValue.split(",").map { it.trim() }
                            .dropLastWhile { it.isEmpty() }.toTypedArray()
                    }
                }
                "shell" -> clickable.shell = attrValue
                "bg-task", "background-task", "async-task" -> {
                    if (attrValue == "async-task" || attrValue == "async" ||
                        attrValue == "bg-task" || attrValue == "background" ||
                        attrValue == "background-task" || attrValue == "true" || attrValue == "1"
                    ) {
                        clickable.shell = RunnableNode.shellModeBgTask
                    }
                }
            }
        }
        return clickable
    }

    private fun mainNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser): NodeInfoBase? {
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "key", "index", "id" -> nodeInfoBase.key = attrValue.trim()
                "title" -> nodeInfoBase.title = resolveText(attrValue)
                "desc" -> nodeInfoBase.desc = resolveText(attrValue)
                "support", "visible" -> {
                    if (!isSupported(attrValue)) return null
                }
                "desc-sh" -> {
                    nodeInfoBase.descSh = attrValue
                    nodeInfoBase.desc = executeShell(attrValue)
                }
                "summary" -> nodeInfoBase.summary = resolveText(attrValue)
                "summary-sh" -> {
                    nodeInfoBase.summarySh = attrValue
                    nodeInfoBase.summary = executeShell(attrValue)
                }
            }
        }
        return nodeInfoBase
    }

    private fun pageNode(page: PageNode, parser: XmlPullParser): PageNode {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "config" -> page.pageConfigPath = attrValue
                "html" -> page.onlineHtmlPage = attrValue
                "before-load", "before-read" -> page.beforeRead = attrValue
                "after-load", "after-read" -> page.afterRead = attrValue
                "load-ok", "load-success" -> page.loadSuccess = attrValue
                "load-fail", "load-error" -> page.loadFail = attrValue
                "config-sh" -> page.pageConfigSh = attrValue
                "link", "href" -> page.link = attrValue
                "activity", "a", "intent" -> page.activity = attrValue
                "option-sh", "option-su", "options-sh" -> page.pageMenuOptionsSh = attrValue
                "handler-sh", "handler", "set", "getstate", "script" -> page.pageHandlerSh = attrValue
            }
        }
        return page
    }

    private fun pickerNode(pickerNode: PickerNode, parser: XmlPullParser) {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "option-sh", "options-sh", "options-su" -> {
                    if (pickerNode.options == null) pickerNode.options = ArrayList()
                    pickerNode.optionsSh = attrValue
                }
                "multiple" -> pickerNode.multiple =
                    attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                "separator" -> pickerNode.separator = attrValue
            }
        }
    }

    private fun descNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == "su" ||
                parser.getAttributeName(i) == "sh" ||
                parser.getAttributeName(i) == "desc-sh"
            ) {
                nodeInfoBase.descSh = parser.getAttributeValue(i)
                nodeInfoBase.desc = executeShell(nodeInfoBase.descSh)
            }
        }
        if (nodeInfoBase.desc.isEmpty()) {
            nodeInfoBase.desc = resolveText(parser.nextText())
        }
    }

    private fun summaryNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == "su" ||
                parser.getAttributeName(i) == "sh" ||
                parser.getAttributeName(i) == "summary-sh"
            ) {
                nodeInfoBase.summarySh = parser.getAttributeValue(i)
                nodeInfoBase.summary = executeShell(nodeInfoBase.summarySh)
            }
        }
        if (nodeInfoBase.summary.isEmpty()) {
            nodeInfoBase.summary = resolveText(parser.nextText())
        }
    }

    private fun tagEndInSwitch(switchNode: SwitchNode?) {
        if (switchNode != null) {
            val shellResult = executeShell(switchNode.getState ?: "")
            switchNode.checked = shellResult != "error" &&
                (shellResult == "1" || shellResult.toLowerCase(Locale.ROOT) == "true")
            if (switchNode.setState == null) switchNode.setState = ""
        }
    }

    private fun tagStartInText(textNode: TextNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> textNode.title = resolveText(parser.nextText())
            "desc" -> descNode(textNode, parser)
            "summary" -> summaryNode(textNode, parser)
            "slice" -> rowNode(textNode, parser)
            // `<resource>` intentionally ignored — handled by RuntimeBinder.
        }
    }

    private fun rowNode(textNode: TextNode, parser: XmlPullParser) {
        val textRow = TextNode.TextRow()
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i).toLowerCase(Locale.ROOT)
            val attrValue = parser.getAttributeValue(i)
            try {
                when (attrName) {
                    "bold", "b" -> textRow.bold =
                        (attrValue == "1" || attrValue == "true" || attrValue == "bold")
                    "italic", "i" -> textRow.italic =
                        (attrValue == "1" || attrValue == "true" || attrValue == "italic")
                    "underline", "u" -> textRow.underline =
                        (attrValue == "1" || attrValue == "true" || attrValue == "underline")
                    "foreground", "color" -> textRow.color = Color.parseColor(attrValue)
                    "bg", "background", "bgcolor" -> textRow.bgColor = Color.parseColor(attrValue)
                    "size" -> textRow.size = attrValue.toInt()
                    "break" -> textRow.breakRow =
                        (attrValue == "1" || attrValue == "true" || attrValue == "break")
                    "link", "href" -> textRow.link = attrValue
                    "activity", "a", "intent" -> textRow.activity = attrValue
                    "script", "run" -> textRow.onClickScript = attrValue
                    "sh" -> textRow.dynamicTextSh = attrValue
                    "align" -> {
                        when (attrValue) {
                            "left" -> textRow.align = Layout.Alignment.ALIGN_LEFT
                            "right" -> textRow.align = Layout.Alignment.ALIGN_RIGHT
                            "center" -> textRow.align = Layout.Alignment.ALIGN_CENTER
                            "normal" -> textRow.align = Layout.Alignment.ALIGN_NORMAL
                        }
                    }
                }
            } catch (_: Exception) {
                // Per-row parse failures must not break the whole page.
            }
        }
        textRow.text = resolveText("" + parser.nextText())
        textNode.rows.add(textRow)
    }

    private fun tagStartInPicker(pickerNode: PickerNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> pickerNode.title = resolveText(parser.nextText())
            "desc" -> descNode(pickerNode, parser)
            "summary" -> summaryNode(pickerNode, parser)
            "option" -> {
                if (pickerNode.options == null) pickerNode.options = ArrayList()
                val option = SelectItem()
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "val" || parser.getAttributeName(i) == "value") {
                        option.value = parser.getAttributeValue(i)
                    }
                }
                option.title = resolveText(parser.nextText())
                if (option.value == null) option.value = option.title
                pickerNode.options!!.add(option)
            }
            "getstate", "get" -> pickerNode.getState = parser.nextText()
            "setstate", "set" -> pickerNode.setState = parser.nextText()
            "lock", "lock-state" -> pickerNode.lockShell = parser.nextText()
            // `<resource>` intentionally ignored — handled by RuntimeBinder.
        }
    }

    private fun tagEndInPicker(pickerNode: PickerNode?) {
        if (pickerNode != null) {
            if (pickerNode.getState == null) {
                pickerNode.getState = ""
            } else {
                pickerNode.value = executeShell("" + pickerNode.getState)
            }
            if (pickerNode.setState == null) pickerNode.setState = ""
        }
    }
}
