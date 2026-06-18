package com.omarea.common.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.widget.TextView
import com.omarea.common.R

open class ProgressBarDialog(private var context: Activity, private var uniqueId: String? = null) {
    private var alert: DialogHelper.DialogWrap? = null
    private var textView: TextView? = null

    companion object {
        private val dialogs = LinkedHashMap<String, DialogHelper.DialogWrap>()
    }

    init {
        hideDialog()
    }

    fun hideDialog() {
        try {
            if (alert != null) {
                alert!!.dismiss()
                alert!!.hide()
                alert = null
            }
        } catch (_: Exception) {
        }

        uniqueId?.run {
            if (dialogs.containsKey(this)) {
                dialogs.remove(this)
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showDialog(text: String = "正在加载，请稍等..."): ProgressBarDialog {
        if (textView != null && alert != null) {
            textView!!.text = text
        } else {
            hideDialog()
            val layoutInflater = LayoutInflater.from(context)
            val dialog = layoutInflater.inflate(R.layout.dialog_loading, null)
            textView = (dialog.findViewById(R.id.dialog_text)!!)
            textView!!.text = text
            alert = DialogHelper.customDialog(context, dialog, false)
        }

        uniqueId?.run {
            if (dialogs.containsKey(this)) {
                dialogs.remove(this)
            }
            if (alert != null) {
                dialogs[this] = alert!!
            }
        }

        return this
    }
}
