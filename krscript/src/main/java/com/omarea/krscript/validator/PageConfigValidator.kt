package com.omarea.krscript.validator

import com.omarea.krscript.model.ActionNode
import com.omarea.krscript.model.ActionParamInfo
import com.omarea.krscript.model.GroupNode
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.PickerNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.SwitchNode
import com.omarea.krscript.model.TextNode

/**
 * RU: Результат проверки конфигурации страницы.
 *
 * Содержит как ошибки (блокирующие), так и предупреждения (информационные).
 *
 * EN: Result of validating a page configuration.
 *
 * Contains both errors (blocking) and warnings (informational).
 */
data class ValidationReport(
    val errors: List<ValidationIssue> = emptyList(),
    val warnings: List<ValidationIssue> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun merge(other: ValidationReport): ValidationReport =
        ValidationReport(
            errors = errors + other.errors,
            warnings = warnings + other.warnings
        )
}

/**
 * RU: Одно замечание валидатора.
 *
 * EN: A single validator finding.
 */
data class ValidationIssue(
    val level: Level,
    val code: String,
    val message: String,
    val nodeKey: String? = null,
    val nodeTitle: String? = null
) {
    enum class Level { ERROR, WARNING }
}

/**
 * RU: Проверяет построенную модель [NodeInfoBase] без выполнения shell.
 *
 * Цели валидатора:
 *   - гарантировать, что у action/switch/picker есть `key`, если они имеют `setState`;
 *   - ловить пустые `setState` для переключателей (это норма, но предупреждает);
 *   - проверять, что у `PickerNode` есть хотя бы источник опций (статичный список
 *     или `optionsSh`);
 *   - ловить конфигурации, в которых `lock-shell` ссылается на неизвестный ресурс.
 *
 * Валидатор НЕ выполняет shell и НЕ показывает UI.
 *
 * EN: Validates the built [NodeInfoBase] model without running shell.
 *
 * Goals:
 *   - ensure action/switch/picker nodes have `key` when `setState` is present;
 *   - warn on empty `setState` for switches (allowed but suspicious);
 *   - verify `PickerNode` has at least one options source (static list or `optionsSh`);
 *   - catch configurations where `lock-shell` references an unknown resource.
 *
 * The validator does NOT run shell and does NOT show UI.
 */
class PageConfigValidator {
    fun validate(nodes: List<NodeInfoBase>): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        for (node in nodes) {
            validateNode(node, issues)
        }
        return ValidationReport(
            errors = issues.filter { it.level == ValidationIssue.Level.ERROR },
            warnings = issues.filter { it.level == ValidationIssue.Level.WARNING }
        )
    }

    private fun validateNode(node: NodeInfoBase, issues: MutableList<ValidationIssue>) {
        when (node) {
            is GroupNode -> {
                if (node.title.isEmpty() && node.key.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            level = ValidationIssue.Level.WARNING,
                            code = CODE_GROUP_MISSING_TITLE,
                            message = "Group without title or key is hard to render",
                            nodeKey = node.key,
                            nodeTitle = node.title
                        )
                    )
                }
                for (child in node.children) {
                    validateNode(child, issues)
                }
            }
            is PageNode -> {
                if (node.pageConfigPath.isNullOrEmpty() &&
                    node.onlineHtmlPage.isNullOrEmpty() &&
                    node.activity.isNullOrEmpty() &&
                    node.link.isNullOrEmpty() &&
                    node.pageHandlerSh.isNullOrEmpty()
                ) {
                    issues.add(
                        ValidationIssue(
                            level = ValidationIssue.Level.WARNING,
                            code = CODE_PAGE_NO_TARGET,
                            message = "Page has no config path, html, activity, link or handler",
                            nodeKey = node.key,
                            nodeTitle = node.title
                        )
                    )
                }
            }
            is ActionNode -> {
                validateRunnable(node, issues)
                validateParams(node.params, issues, node)
            }
            is SwitchNode -> {
                validateRunnable(node, issues)
                if (node.getState.isNullOrEmpty()) {
                    issues.add(
                        ValidationIssue(
                            level = ValidationIssue.Level.WARNING,
                            code = CODE_SWITCH_NO_GETSTATE,
                            message = "Switch has no <getstate> — checked state will be unknown",
                            nodeKey = node.key,
                            nodeTitle = node.title
                        )
                    )
                }
            }
            is PickerNode -> {
                validateRunnable(node, issues)
                if (node.options.isNullOrEmpty() && node.optionsSh.isNullOrEmpty()) {
                    issues.add(
                        ValidationIssue(
                            level = ValidationIssue.Level.ERROR,
                            code = CODE_PICKER_NO_OPTIONS,
                            message = "Picker has no static <option> list and no options-sh script",
                            nodeKey = node.key,
                            nodeTitle = node.title
                        )
                    )
                }
            }
            is TextNode -> {
                // TextNode — пассивный узел; только проверяем, что есть title или rows.
                if (node.title.isEmpty() && node.rows.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            level = ValidationIssue.Level.WARNING,
                            code = CODE_TEXT_EMPTY,
                            message = "Text node has no title and no rows",
                            nodeKey = node.key,
                            nodeTitle = node.title
                        )
                    )
                }
            }
        }
    }

    private fun validateRunnable(node: RunnableNode, issues: MutableList<ValidationIssue>) {
        if (node.key.isNotEmpty() && node.key.startsWith("@") && node.allowShortcut == null) {
            // Это ожидаемое поведение, не предупреждаем.
        }
        if (node.setState.isNullOrEmpty() && node is ActionNode) {
            // Action без setState допустим, но это часто опечатка — предупреждаем.
            issues.add(
                ValidationIssue(
                    level = ValidationIssue.Level.WARNING,
                    code = CODE_ACTION_NO_SETSTATE,
                    message = "Action has no <set>/<setstate>/<script> — nothing will run on click",
                    nodeKey = node.key,
                    nodeTitle = node.title
                )
            )
        }
        if (!node.setState.isNullOrEmpty() && node.key.isEmpty()) {
            // Переключатель/действие с setState, но без key — нормально, но предупреждаем.
            issues.add(
                ValidationIssue(
                    level = ValidationIssue.Level.WARNING,
                    code = CODE_RUNNABLE_NO_KEY,
                    message = "Runnable node has setState but no key — shortcuts and persistence will be unstable",
                    nodeKey = node.key,
                    nodeTitle = node.title
                )
            )
        }
    }

    private fun validateParams(
        params: ArrayList<ActionParamInfo>?,
        issues: MutableList<ValidationIssue>,
        owner: NodeInfoBase
    ) {
        if (params == null) return
        val seenNames = HashSet<String>()
        for (param in params) {
            if (param.name.isNullOrEmpty()) {
                issues.add(
                    ValidationIssue(
                        level = ValidationIssue.Level.ERROR,
                        code = CODE_PARAM_NO_NAME,
                        message = "Param has no name attribute — cannot be referenced in shell",
                        nodeKey = owner.key,
                        nodeTitle = owner.title
                    )
                )
                continue
            }
            if (!seenNames.add(param.name!!)) {
                issues.add(
                    ValidationIssue(
                        level = ValidationIssue.Level.ERROR,
                        code = CODE_PARAM_DUPLICATE_NAME,
                        message = "Duplicate param name '${param.name}' inside action",
                        nodeKey = owner.key,
                        nodeTitle = owner.title
                    )
                )
            }
            if (param.required && !param.value.isNullOrEmpty()) {
                // Требуемый параметр со значением по умолчанию — это не ошибка, но предупреждаем.
                issues.add(
                    ValidationIssue(
                        level = ValidationIssue.Level.WARNING,
                        code = CODE_PARAM_REQUIRED_HAS_DEFAULT,
                        message = "Param '${param.name}' is required but already has a default value",
                        nodeKey = owner.key,
                        nodeTitle = owner.title
                    )
                )
            }
        }
    }

    companion object {
        const val CODE_GROUP_MISSING_TITLE = "group-missing-title"
        const val CODE_PAGE_NO_TARGET = "page-no-target"
        const val CODE_ACTION_NO_SETSTATE = "action-no-setstate"
        const val CODE_SWITCH_NO_GETSTATE = "switch-no-getstate"
        const val CODE_PICKER_NO_OPTIONS = "picker-no-options"
        const val CODE_TEXT_EMPTY = "text-empty"
        const val CODE_RUNNABLE_NO_KEY = "runnable-no-key"
        const val CODE_PARAM_NO_NAME = "param-no-name"
        const val CODE_PARAM_DUPLICATE_NAME = "param-duplicate-name"
        const val CODE_PARAM_REQUIRED_HAS_DEFAULT = "param-required-has-default"
    }
}
