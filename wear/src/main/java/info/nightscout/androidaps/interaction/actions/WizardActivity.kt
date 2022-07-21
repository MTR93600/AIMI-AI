@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.interaction.actions

import android.os.Bundle
import android.support.wearable.view.GridPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventWearToMobile
import info.nightscout.androidaps.interaction.utils.EditPlusMinusViewAdapter
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText
import info.nightscout.shared.SafeParse
import info.nightscout.shared.weardata.EventData.ActionWizardPreCheck
import java.text.DecimalFormat

class WizardActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
    var editPercentage: PlusMinusEditText? = null
    var hasPercentage = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyGridViewPagerAdapter())
        hasPercentage = sp.getBoolean(R.string.key_wizard_percentage, false)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapter() {

        override fun getColumnCount(arg0: Int): Int = if (hasPercentage) 3 else 2
        override fun getRowCount(): Int = 1
        private val increment1 = sp.getInt(R.string.key_carbs_button_increment_1, 5).toDouble()
        private val increment2 = sp.getInt(R.string.key_carbs_button_increment_2, 10).toDouble()
        val stepValues = listOf(1.0, increment1, increment2)

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when {
            col == 0                  -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, true)
                val view = viewAdapter.root
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
                val initValue = SafeParse.stringToDouble(editCarbs?.editText?.text.toString(), 0.0)
                editCarbs = PlusMinusEditText(viewAdapter, initValue, 0.0, maxCarbs, stepValues, DecimalFormat("0"), false, getString(R.string.action_carbs))
                container.addView(view)
                view.requestFocus()
                view
            }

            col == 1 && hasPercentage -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                val percentage = sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100).toDouble()
                val initValue = SafeParse.stringToDouble(editPercentage?.editText?.text.toString(), percentage)
                editPercentage = PlusMinusEditText(viewAdapter, initValue, 50.0, 150.0, 1.0, DecimalFormat("0"), false, getString(R.string.action_percentage))
                container.addView(view)
                view
            }

            else                      -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                view.findViewById<ImageView>(R.id.confirmbutton)
                    .setOnClickListener {
                        val percentage = if (hasPercentage) SafeParse.stringToInt(editPercentage?.editText?.text.toString()) else sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100)
                        rxBus.send(EventWearToMobile(ActionWizardPreCheck(SafeParse.stringToInt(editCarbs?.editText?.text.toString()), percentage)))
                        showToast(this@WizardActivity, R.string.action_wizard_confirmation)
                        finishAffinity()
                    }
                container.addView(view)
                view
            }
        }

        override fun destroyItem(container: ViewGroup, row: Int, col: Int, view: Any) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for re-init?
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
    }
}
