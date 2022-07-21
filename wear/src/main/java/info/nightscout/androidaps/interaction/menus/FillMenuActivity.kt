package info.nightscout.androidaps.interaction.menus

import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventWearToMobile
import info.nightscout.androidaps.interaction.actions.FillActivity
import info.nightscout.androidaps.interaction.utils.MenuListActivity
import info.nightscout.shared.weardata.EventData

class FillMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.menu_prime_fill)
        super.onCreate(savedInstanceState)
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_1)))
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_2)))
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_3)))
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_free_amount)))
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.action_preset_1)    -> rxBus.send(EventWearToMobile(EventData.ActionFillPresetPreCheck(1)))
            getString(R.string.action_preset_2)    -> rxBus.send(EventWearToMobile(EventData.ActionFillPresetPreCheck(2)))
            getString(R.string.action_preset_3)    -> rxBus.send(EventWearToMobile(EventData.ActionFillPresetPreCheck(3)))
            getString(R.string.action_free_amount) -> startActivity(Intent(this, FillActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
