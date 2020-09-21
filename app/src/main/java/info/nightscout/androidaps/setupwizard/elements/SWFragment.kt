package info.nightscout.androidaps.setupwizard.elements

import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.setupwizard.SWDefinition

class SWFragment(injector:HasAndroidInjector, private var definition: SWDefinition) : SWItem(injector, Type.FRAGMENT) {
    lateinit var fragment: Fragment

    fun add(fragment: Fragment): SWFragment {
        this.fragment = fragment
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        definition.activity.supportFragmentManager.beginTransaction().add(layout.id, fragment, fragment.tag).commit()
    }
}