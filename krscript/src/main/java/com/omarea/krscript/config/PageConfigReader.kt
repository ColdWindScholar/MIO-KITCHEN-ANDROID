package com.omarea.krscript.config

import android.content.Context
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.ActionInfo
import com.omarea.krscript.model.ConfigItemBase
import com.omarea.krscript.model.SwitchInfo
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.*

/**
 * Created by Hello on 2018/04/01.
 */
class PageConfigReader(private var context: Context) {
    private val ASSETS_FILE = "file:///android_asset/"
    private fun getConfig(context: Context, filePath: String): InputStream? {
        try {
            if (filePath.startsWith(ASSETS_FILE)) {
                return context.assets.open(filePath.substring(ASSETS_FILE.length))
            } else {
                return context.assets.open(filePath)
            }
        } catch (ex: Exception) {
            return null
        }
    }

    fun readConfigXml(filePath: String): ArrayList<ConfigItemBase>? {
        try {
            val fileInputStream = getConfig(context, filePath) ?: return ArrayList()
            val parser = Xml.newPullParser()// 获取xml解析器
            parser.setInput(fileInputStream, "utf-8")// 参数分别为输入流和字符编码
            var type = parser.eventType
            val actions: ArrayList<ConfigItemBase> = ArrayList<ConfigItemBase>()
            var action: ActionInfo? = null
            var switch: SwitchInfo? = null
            while (type != XmlPullParser.END_DOCUMENT) {// 如果事件不等于文档结束事件就继续循环
                when (type) {
                    XmlPullParser.START_TAG ->
                        if ("separator" == parser.name) {
                            val separator = ActionInfo()
                            separator.separator = parser.nextText()
                            actions.add(separator)
                        }
                        else if ("group" == parser.name) {
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i)
                                if (attrName == "title") {
                                    val separator = ActionInfo()
                                    separator.separator = parser.getAttributeValue(i)
                                    actions.add(separator)
                                    break
                                }
                            }
                        }
                        else if ("action" == parser.name) {
                            action = ActionInfo()
                            for (i in 0 until parser.attributeCount) {
                                if (action == null) {
                                    break
                                }
                                when (parser.getAttributeName(i)) {
                                    "confirm" -> action.confirm = parser.getAttributeValue(i) == "true"
                                    "start" -> action.start = parser.getAttributeValue(i)
                                    "support" -> {
                                        if (executeResultRoot(context, parser.getAttributeValue(i)) != "1") {
                                            action = null
                                        }
                                    }
                                }
                            }
                        }
                        else if ("switch" == parser.name) {
                            switch = SwitchInfo()
                            for (i in 0 until parser.attributeCount) {
                                if (switch == null) {
                                    break
                                }
                                when (parser.getAttributeName(i)) {
                                    "confirm" -> switch.confirm = parser.getAttributeValue(i) == "true"
                                    "start" -> switch.start = parser.getAttributeValue(i)
                                    "support" -> {
                                        if (executeResultRoot(context, parser.getAttributeValue(i)) != "1") {
                                            switch = null
                                        }
                                    }
                                }
                            }
                        }
                        else if (action != null) {
                            tagStartInAction(action, parser)
                        }
                        else if (switch != null) {
                            tagStartInSwitch(switch, parser)
                        }
                        else if ("resource" == parser.name) {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i) == "file") {
                                    val file = parser.getAttributeValue(i).trim()
                                    if (file.startsWith(ASSETS_FILE)) {
                                        ExtractAssets(context).extractResource(file)
                                    }
                                }
                            }
                        }
                    XmlPullParser.END_TAG ->
                        if ("action" == parser.name) {
                            tagEndInAction(action, parser)
                            if (action != null) {
                                actions.add(action)
                            }
                            action = null
                        }
                        else if ("switch" == parser.name) {
                            tagEndInSwitch(switch, parser)
                            if (switch != null) {
                                actions.add(switch)
                            }
                            switch = null
                        }
                }
                type = parser.next()// 继续下一个事件
            }

            return actions
        } catch (ex: Exception) {
            Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
            Log.d("VTools ReadConfig Fail！", ex.message)
        }

        return null
    }

    var actionParamInfos: ArrayList<ActionParamInfo>? = null
    var actionParamInfo: ActionParamInfo? = null
    fun tagStartInAction(action: ActionInfo, parser:XmlPullParser) {
        if ("title" == parser.name) {
            action.title = parser.nextText()
        }
        else if ("desc" == parser.name) {
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "su" || attrName == "sh") {
                    action.descPollingShell = parser.getAttributeValue(i)
                    action.desc = executeResultRoot(context, action.descPollingShell)
                }
            }
            if (action.desc == null || action.desc.isEmpty())
                action.desc = parser.nextText()
        }
        else if ("script" == parser.name) {
            action.script = parser.nextText()
        }
        else if ("param" == parser.name) {
            if (actionParamInfos == null) {
                actionParamInfos = ArrayList()
            }
            actionParamInfo = ActionParamInfo()
            val actionParamInfo = actionParamInfo!!
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                when {
                    attrName == "name" -> actionParamInfo.name = parser.getAttributeValue(i)
                    attrName == "desc" -> actionParamInfo.desc = parser.getAttributeValue(i)
                    attrName == "value" -> actionParamInfo.value = parser.getAttributeValue(i)
                    attrName == "type" -> actionParamInfo.type = parser.getAttributeValue(i).toLowerCase().trim { it <= ' ' }
                    attrName == "readonly" -> actionParamInfo.readonly = parser.getAttributeValue(i).toLowerCase().trim { it <= ' ' } == "readonly"
                    attrName == "maxlength" -> actionParamInfo.maxLength = Integer.parseInt(parser.getAttributeValue(i))
                    attrName == "value-sh" || attrName == "value-su" -> {
                        val script = parser.getAttributeValue(i)
                        actionParamInfo.valueShell = script
                    }
                    attrName == "options-sh" || attrName == "options-su" -> {
                        if (actionParamInfo.options == null)
                            actionParamInfo.options = ArrayList<ActionParamInfo.ActionParamOption>()
                        val script = parser.getAttributeValue(i)
                        actionParamInfo.optionsSh = script
                    }
                }
            }
            if (actionParamInfo.name != null && actionParamInfo.name.trim { it <= ' ' } != "") {
                actionParamInfos!!.add(actionParamInfo)
            }
        }
        else if (actionParamInfo != null && "option" == parser.name) {
            val actionParamInfo = actionParamInfo!!
            if (actionParamInfo.options == null) {
                actionParamInfo.options = ArrayList<ActionParamInfo.ActionParamOption>()
            }
            val option = ActionParamInfo.ActionParamOption()
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "val" || attrName == "value") {
                    option.value = parser.getAttributeValue(i)
                }
            }
            option.desc = parser.nextText()
            if (option.value == null)
                option.value = option.desc
            actionParamInfo.options.add(option)
        }
        else if ("resource" == parser.name) {
            for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i) == "file") {
                    val file = parser.getAttributeValue(i).trim()
                    if (file.startsWith(ASSETS_FILE)) {
                        ExtractAssets(context).extractResource(file)
                    }
                }
            }
        }
    }

    fun tagEndInAction(action: ActionInfo?, parser:XmlPullParser) {
        if (action != null) {
            if (action.title == null)
                action.title = ""
            if (action.desc == null)
                action.desc = ""
            if (action.script == null)
                action.script = ""
            action.params = actionParamInfos

            actionParamInfos = null
        }
    }

    fun tagStartInSwitch(switchInfo: SwitchInfo, parser:XmlPullParser) {
        if ("title" == parser.name) {
            switchInfo.title = parser.nextText()
        }
        else if ("desc" == parser.name) {
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "su" || attrName == "sh") {
                    switchInfo.descPollingShell = parser.getAttributeValue(i)
                    switchInfo.desc = executeResultRoot(context, switchInfo.descPollingShell)
                }
            }
            if (switchInfo.desc == null || switchInfo.desc.isEmpty())
                switchInfo.desc = parser.nextText()
        }
        else if ("getstate" == parser.name) {
            switchInfo.getState = parser.nextText()
        }
        else if ("setstate" == parser.name) {
            switchInfo.setState = parser.nextText()
        }
        else if ("resource" == parser.name) {
            for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i) == "file") {
                    val file = parser.getAttributeValue(i).trim()
                    if (file.startsWith(ASSETS_FILE)) {
                        ExtractAssets(context).extractResource(file)
                    }
                }
            }
        }
    }

    fun tagEndInSwitch(switchInfo: SwitchInfo?, parser:XmlPullParser) {
        if (switchInfo != null) {
            if (switchInfo.title == null) {
                switchInfo.title = ""
            }
            if (switchInfo.desc == null) {
                switchInfo.desc = ""
            }
            if (switchInfo.getState == null) {
                switchInfo.getState = ""
            } else {
                val shellResult = executeResultRoot(context, switchInfo.getState)
                switchInfo.selected = shellResult != "error" && (shellResult == "1" || shellResult.toLowerCase() == "true")
            }
            if (switchInfo.setState == null) {
                switchInfo.setState = ""
            }
        }
    }

    private fun executeResultRoot(context: Context, scriptIn: String): String {
        return ScriptEnvironmen.executeResultRoot(context, scriptIn);
    }
}
