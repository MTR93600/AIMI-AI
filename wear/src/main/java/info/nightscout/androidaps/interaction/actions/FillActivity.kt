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
import info.nightscout.shared.SafeParse.stringToDouble
import info.nightscout.shared.weardata.EventData.ActionFillPreCheck
import java.text.DecimalFormat

class FillActivity : ViewSelectorActivity() {

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

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                var initValue = stringToDouble(editInsulin?.editText?.text.toString(), 0.0)
                editInsulin = PlusMinusEditText(viewAdapter, initValue, 0.0, 30.0, 0.1, DecimalFormat("#0.0"), false, getString(R.string.action_insulin))
                container.addView(view)
                view.requestFocus()
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    // check if it can happen that the fragment is never created that hold data?
                    // (you have to swipe past them anyways - but still)
                    rxBus.send(EventWearToMobile(ActionFillPreCheck(stringToDouble(editInsulin?.editText?.text.toString()))))
                    showToast(this@FillActivity, R.string.action_fill_confirmation)
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
