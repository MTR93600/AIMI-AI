package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.utils.resources.ResourceHelper

class StaticLabel(private val rh: ResourceHelper) : Element() {

    var label = ""
    var trigger: Trigger? = null

    constructor(rh: ResourceHelper, label: String, trigger: Trigger) : this(rh) {
        this.label = label
        this.trigger = trigger
    }

    constructor(rh: ResourceHelper, resourceId: Int, trigger: Trigger) : this(rh) {
        label = rh.gs(resourceId)
        this.trigger = trigger
    }

    override fun addToLayout(root: LinearLayout) {
        val px = rh.dpToPx(10)
        root.addView(
            LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(rh.gc(android.R.color.black))
                addView(
                    TextView(root.context).apply {
                        text = label
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            weight = 1.0f
                        }
                        setPadding(px, px, px, px)
                        setTypeface(typeface, Typeface.BOLD)
                    })
                trigger?.let {
                    addView(it.createDeleteButton(root.context, it))
                    addView(it.createCloneButton(root.context, it))
                }
            })
    }
}