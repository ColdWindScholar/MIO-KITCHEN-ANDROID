package com.omarea.krscript.config

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.omarea.krscript.R
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import java.io.ByteArrayInputStream

class PageConfigSh(private var activity: Activity, private var pageConfigSh: String, private var parentConfig: PageNode?) {
    private var handler = Handler(Looper.getMainLooper())

    private fun pageConfigShError(content: String) {
        handler.post {
            Toast.makeText(activity, activity.getString(R.string.kr_page_sh_invalid) + "\n" + content, Toast.LENGTH_LONG).show()
        }
    }

    private fun noReadPermission() {
        handler.post {
            Toast.makeText(activity, activity.getString(R.string.kr_page_sh_file_permission), Toast.LENGTH_LONG).show()
        }
    }

    fun execute(): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        val result = ScriptEnvironmen.executeResultRoot(activity, pageConfigSh, parentConfig)?.trim()
        if (result != null) {
            if (result.endsWith(".xml")) {
                // RU: Stage 22 — заменяем legacy PageConfigReader на новый
                //     PageConfigLoader (PageConfigRepository + RuntimeBinder).
                // EN: Stage 22 — replace legacy PageConfigReader with the new
                //     PageConfigLoader (PageConfigRepository + RuntimeBinder).
                items = PageConfigLoader.load(activity, result, parentConfig?.pageConfigDir)
                if (items == null) {
                    noReadPermission()
                }
            } else if (result.startsWith("<?xml") && result.endsWith(">")) {
                val inputStream = ByteArrayInputStream(result.toByteArray())
                // RU: Stage 22 — то же самое для stream-варианта.
                // EN: Stage 22 — same for the stream variant.
                items = PageConfigLoader.loadFromStream(activity, inputStream)
            } else if (result.isNotEmpty()) {
                pageConfigShError(result)
            }
        }
        return items
    }
}
