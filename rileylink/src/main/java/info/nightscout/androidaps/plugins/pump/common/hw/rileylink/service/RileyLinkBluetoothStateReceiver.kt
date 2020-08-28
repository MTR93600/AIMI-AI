package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import javax.inject.Inject

class RileyLinkBluetoothStateReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != null) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_TURNING_ON -> {
                }

                BluetoothAdapter.STATE_ON                                                                         -> {
                    aapsLogger.debug("RileyLinkBluetoothStateReceiver: Bluetooth back on. Sending broadcast to RileyLink Framework")
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothReconnected, context)
                }
            }
        }
    }

    fun unregisterBroadcasts(context: Context) {
        context.unregisterReceiver(this)
    }

    fun registerBroadcasts(context: Context) {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(this, filter)
    }
}