package info.nightscout.androidaps.setupwizard.elements

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.setupwizard.SWValidator

class SWBreak(injector: HasAndroidInjector) : SWItem(injector, Type.TEXT) {
    private var l: TextView? = null
    private var visibilityValidator: SWValidator? = null

    fun visibility(visibilityValidator: SWValidator): SWBreak {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        layout.context
        l = TextView(layout.context)
        l?.id = View.generateViewId()
        l?.text = "\n"
        layout.addView(l)
    }

    override fun processVisibility() {
        if (visibilityValidator != null && !visibilityValidator!!.isValid)
            l?.visibility = View.GONE
        else l?.visibility = View.VISIBLE
    }
}