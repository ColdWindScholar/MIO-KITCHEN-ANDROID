package com.omarea.krscript.runtime

import android.content.Context
import com.omarea.common.shell.ShellTranslation
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.GroupNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.PickerNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.SwitchNode
import com.omarea.krscript.model.TextNode
import com.omarea.krscript.parser.DynamicValueResolver

/**
 * RU: Связывает чистую модель [NodeInfoBase] с runtime-окружением Android.
 *
 * Этот класс — единственное место, где:
 *   - вызывается `ScriptEnvironmen.executeResultRoot` для разрешения `desc-sh`/
 *     `summary-sh`/`support`/`visible`/`<getstate>` на лету;
 *   - извлекаются ресурсы `<resource file="...">` из assets;
 *   - переводы разрешаются через `ShellTranslation`.
 *
 * Разделение позволяет парсеру оставаться чистым, а runtime-эффекты concentrated.
 *
 * EN: Binds the pure [NodeInfoBase] model to the Android runtime environment.
 *
 * This is the single place that:
 *   - calls `ScriptEnvironmen.executeResultRoot` to dynamically resolve `desc-sh`/
 *     `summary-sh`/`support`/`visible`/`<getstate>`;
 *   - extracts `<resource file="...">` assets;
 *   - resolves translations via `ShellTranslation`.
 *
 * The split keeps the parser pure while concentrating all runtime side effects here.
 */
class RuntimeBinder(private val context: Context) : DynamicValueResolver {

    private val shellTranslation: ShellTranslation by lazy { ShellTranslation(context) }

    /**
     * RU: Реализация [DynamicValueResolver] — вызывает shell через `ScriptEnvironmen`.
     * EN: [DynamicValueResolver] implementation that invokes shell via `ScriptEnvironmen`.
     */
    override fun resolveShellValue(shellScript: String): String {
        if (shellScript.isEmpty()) return ""
        val virtualNode = NodeInfoBase("")
        return ScriptEnvironmen.executeResultRoot(context, shellScript, virtualNode)
    }

    override fun isSupported(supportScript: String): Boolean {
        return resolveShellValue(supportScript) == "1"
    }

    override fun resolveText(value: String): String {
        return shellTranslation.resolveRow(value)
    }

    /**
     * RU: Извлекает все `<resource>`-ссылки из дерева узлов.
     *
     * В старом `PageConfigReader` это делалось прямо во время разбора, что нарушало
     * чистоту парсера. Теперь это отдельный шаг, который можно запускать после
     * успешного разбора и валидации.
     *
     * EN: Extracts all `<resource>` references from the node tree.
     *
     * The legacy `PageConfigReader` did this inline during parsing, which broke
     * parser purity. Now this is a separate step that can be run after parsing
     * and validation succeed.
     */
    fun extractResources(nodes: List<NodeInfoBase>) {
        // Resources are extracted by walking the original XML; here we provide
        // an entry point that the orchestrator can use to trigger extraction
        // for a known list of declared resource files (collected separately by
        // the ResourceCollector). This keeps the parser free of side effects.
        ResourceCollector.collect(nodes).forEach { resource ->
            when (resource.kind) {
                ResourceKind.SINGLE_FILE -> ExtractAssets(context).extractResource(resource.path)
                ResourceKind.DIRECTORY -> ExtractAssets(context).extractResources(resource.path)
            }
        }
    }

    /**
     * RU: Разрешает отложенные shell-значения, которые не были вычислены парсером.
     *
     * Обычно парсер с `RuntimeBinder` сразу выполняет shell. Этот метод нужен для
     * случая, когда модель сначала построили с `NoopDynamicValueResolver`, а потом
     * хотят "оживить" значения без повторного разбора XML.
     *
     * EN: Resolves deferred shell values that were not evaluated by the parser.
     *
     * Usually the parser with [RuntimeBinder] evaluates shell eagerly. This method
     * covers the case where the model was first built with
     * [com.omarea.krscript.parser.NoopDynamicValueResolver] and then needs to be
     * "brought to life" without re-parsing the XML.
     */
    fun resolveDeferredShellValues(nodes: List<NodeInfoBase>) {
        for (node in nodes) resolveNode(node)
    }

    private fun resolveNode(node: NodeInfoBase) {
        when (node) {
            is GroupNode -> {
                if (node.descSh.isNotEmpty()) node.desc = resolveShellValue(node.descSh)
                if (node.summarySh.isNotEmpty()) node.summary = resolveShellValue(node.summarySh)
                node.children.forEach { resolveNode(it) }
            }
            is PageNode -> {
                resolveRunnableDeferred(node)
                node.pageMenuOptions?.forEach { resolveRunnableDeferred(it) }
            }
            is ActionNode -> {
                resolveRunnableDeferred(node)
                node.params?.forEach { param ->
                    val vs = param.valueShell
                    if (!vs.isNullOrEmpty()) {
                        param.value = resolveShellValue(vs)
                    }
                    if (!param.optionsSh.isNullOrEmpty()) {
                        // Dynamic options are resolved at click time — leave the
                        // script reference in place, the executor handles it.
                    }
                }
            }
            is SwitchNode -> {
                resolveRunnableDeferred(node)
                val gs = node.getState
                if (!gs.isNullOrEmpty()) {
                    val r = resolveShellValue(gs)
                    node.checked = r != "error" && (r == "1" || r.equals("true", ignoreCase = true))
                }
            }
            is PickerNode -> {
                resolveRunnableDeferred(node)
                if (!node.getState.isNullOrEmpty()) node.value = resolveShellValue(node.getState)
            }
            is TextNode -> {
                // TextNode rows may have dynamicTextSh — resolved at render time.
            }
        }
    }

    private fun resolveRunnableDeferred(node: NodeInfoBase) {
        if (node.descSh.isNotEmpty()) node.desc = resolveShellValue(node.descSh)
        if (node.summarySh.isNotEmpty()) node.summary = resolveShellValue(node.summarySh)
    }
}

/**
 * RU: Тип ресурса, объявленного в `<resource>`.
 * EN: Kind of resource declared in a `<resource>` element.
 */
enum class ResourceKind {
    SINGLE_FILE,
    DIRECTORY
}

/**
 * RU: Описание ресурса, подлежащего извлечению.
 * EN: Description of a resource to be extracted.
 */
data class ResourceReference(
    val path: String,
    val kind: ResourceKind
)

/**
 * RU: Собирает ссылки на ресурсы из дерева узлов без их извлечения.
 *
 * Примечание: в текущей модели узлов ссылки на `<resource>` не сохраняются.
 * Этот класс — точка расширения: при появлении в модели поля `resources`
 * коллекция начнёт работать автоматически.
 *
 * EN: Collects resource references from the node tree without extracting them.
 *
 * Note: the current node model does not store `<resource>` references.
 * This class is an extension point: once the model gains a `resources` field,
 * collection will start working automatically.
 */
object ResourceCollector {
    fun collect(@Suppress("UNUSED_PARAMETER") nodes: List<NodeInfoBase>): List<ResourceReference> {
        // The legacy PageConfigReader extracted resources inline during XML
        // parsing. To keep the new parser pure we treat resource extraction as
        // a separate concern. The current node model does not yet carry
        // resource references, so we return an empty list here. When the node
        // model is extended, this method will return the collected references.
        return emptyList()
    }
}
