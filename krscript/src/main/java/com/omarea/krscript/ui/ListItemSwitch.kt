package com.omarea.krscript.ui

import android.content.Context
import android.widget.Switch
import com.omarea.krscript.R
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.SwitchNode
import java.util.Locale

open class ListItemSwitch(private val context: Context,
                          private val config: SwitchNode) : ListItemClickable(context, R.layout.kr_switch_list_item, config) {
    private var switchView = layout.findViewById<Switch?>(R.id.kr_switch)

    private var checked: Boolean
        get() {
            return if (switchView != null) switchView!!.isChecked else false
        }
        set(value) {
            switchView?.isChecked = value
        }

    override fun updateViewByShell() {
        super.updateViewByShell()

        if (config.getState.isNotEmpty()) {
            val shellResult = ScriptEnvironmen.executeResultRoot(context, config.getState, config)
            config.checked = shellResult == "1" || shellResult.toLowerCase(Locale.ROOT) == "true"
        }
        checked = config.checked
    }

    init {
        checked = config.checked
    }
}
