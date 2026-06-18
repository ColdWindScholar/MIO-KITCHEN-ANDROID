package com.omarea.krscript.ui

import android.content.Context
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode

class PageMenuLoader(private val applicationContext: Context, private val pageNode: PageNode) {
    private var menuOptions: ArrayList<PageMenuOption>? = null

    fun load(): ArrayList<PageMenuOption>? {
        if (menuOptions != null) {
            return menuOptions
        }

        menuOptions = ArrayList()

        pageNode.run {
            if (pageMenuOptionsSh.isNotEmpty()) {
                val result = ScriptEnvironmen.executeResultRoot(applicationContext, pageMenuOptionsSh, this)
                if (result != "error") {
                    result.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { item ->
                                val option = PageMenuOption(pageConfigPath)
                                if (item.contains("|")) {
                                    val parts = item.split("|", limit = 2)
                                    option.key = parts[0].trim()
                                    option.title = parts.getOrElse(1) { parts[0] }.trim()
                                } else {
                                    option.key = item
                                    option.title = item
                                }
                                menuOptions?.add(option)
                            }
                }
            } else if (pageMenuOptions != null) {
                menuOptions = pageMenuOptions
            }
        }

        return menuOptions
    }
}
