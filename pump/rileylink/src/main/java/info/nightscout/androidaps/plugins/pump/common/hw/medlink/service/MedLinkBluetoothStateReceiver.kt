package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.rx.logging.AAPSLogger
import javax.inject.Inject

class MedLinkBluetoothStateReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var medLinkUtil: MedLinkUtil

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != null) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_TURNING_ON -> {
                }

                BluetoothAdapter.STATE_ON                                                                         -> {
                    aapsLogger.debug("RileyLinkBluetoothStateReceiver: Bluetooth back on. Sending broadcast to RileyLink Framework")
                    medLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothReconnected, context)
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