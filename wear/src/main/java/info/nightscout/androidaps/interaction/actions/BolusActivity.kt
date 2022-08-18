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
import info.nightscout.shared.weardata.EventData.ActionBolusPreCheck
import java.text.DecimalFormat
import kotlin.math.roundToInt

class BolusActivity : ViewSelectorActivity() {

    var editInsulin: PlusMinusEditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyGridViewPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapter() {

        override fun getColumnCount(arg0: Int): Int = 2
        override fun getRowCount(): Int = 1

        val increment1 = (sp.getDouble(R.string.key_insulin_button_increment_1, 0.5) * 10).roundToInt() / 10.0
        val increment2 = (sp.getDouble(R.string.key_insulin_button_increment_2, 1.0) * 10).roundToInt() / 10.0
        val stepValues = listOf(0.1, increment1, increment2)

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, true)
                val initValue = SafeParse.stringToDouble(editInsulin?.editText?.text.toString(), 0.0)
                val maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)
                val title = getString(R.string.action_insulin)
                editInsulin = PlusMinusEditText(viewAdapter, initValue, 0.0, maxBolus, stepValues, DecimalFormat("#0.0"), false, title)
                val view = viewAdapter.root
                container.addView(view)
                view.requestFocus()
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    rxBus.send(EventWearToMobile(ActionBolusPreCheck(SafeParse.stringToDouble(editInsulin?.editText?.text.toString()), 0)))
                    showToast(this@BolusActivity, R.string.action_bolus_confirmation)
                    finishAffinity()
                }
                container.addView(view)
                view
            }
        }

        override fun destroyItem(container: ViewGroup, row: Int, col: Int, view: Any) {
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
    }
}
