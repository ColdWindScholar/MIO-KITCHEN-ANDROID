package com.mio.kitchen

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.omarea.krscript.model.PageNode

class OpenPageHelper(private var activity: Activity) {

    fun openPage(pageNode: PageNode) {
        try {
            var intent: Intent? = null
            if (pageNode.onlineHtmlPage.isNotEmpty()) {
                intent = Intent(activity, ActionPageOnline::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra("config", pageNode.onlineHtmlPage)
            }

            if (pageNode.pageConfigSh.isNotEmpty()) {
                if (intent == null) {
                    intent = Intent(activity, ActionPage::class.java)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (pageNode.pageConfigPath.isNotEmpty()) {
                if (intent == null) {
                    intent = Intent(activity, ActionPage::class.java)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            intent?.run {
                intent.putExtra("page", pageNode)
                activity.startActivity(intent)
            }
        } catch (ex: Exception) {
            Toast.makeText(activity, "" + ex.message, Toast.LENGTH_SHORT).show()
        }
    }
}
