package com.omarea.krscript.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.omarea.krscript.R
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.model.ClickableNode

open class ListItemClickable(context: Context,
                             layoutId: Int,
                             config: ClickableNode) : ListItemView(context, layoutId, config) {
    private var mOnClickListener: OnClickListener? = null
    private var mOnLongClickListener: OnLongClickListener? = null
    private var shortcutIconView = layout.findViewById<View?>(R.id.kr_shortcut_icon)
    private var iconView = layout.findViewById<ImageView?>(R.id.kr_icon)

    fun setOnClickListener(onClickListener: OnClickListener): ListItemClickable {
        this.mOnClickListener = onClickListener

        return this
    }

    fun triggerAction() {
        this.mOnClickListener?.onClick(this)
    }

    init {
        title = config.title
        desc = config.desc
        summary = config.summary

        this.layout.setOnClickListener {
            this.mOnClickListener?.onClick(this)
        }
        if (this.key.isNotEmpty() && config.allowShortcut != false) {
            this.layout.setOnLongClickListener {
                this.mOnLongClickListener?.onLongClick(this)
                true
            }
            shortcutIconView?.visibility = View.VISIBLE
        } else {
            shortcutIconView?.visibility = View.GONE
        }
        if (iconView != null) {
            iconView?.visibility = View.GONE
            if (config.iconPath.isNotEmpty()) {
                IconPathAnalysis().loadIcon(context, config)?.run {
                    iconView?.setImageDrawable(this)
                    iconView?.visibility = View.VISIBLE
                }
            }
        }
    }

    interface OnClickListener {
        fun onClick(listItemView: ListItemClickable)
    }

    interface OnLongClickListener {
        fun onLongClick(listItemView: ListItemClickable)
    }
}
