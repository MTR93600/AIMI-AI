package info.nightscout.androidaps.plugins.pump.common.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.google.gson.Gson
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.extensions.safeGetParcelableExtra
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.events.EventPumpConnectionParametersChanged
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class BondStateReceiver(
    @StringRes var deviceAddress: Int,
    @StringRes var bondedFlag: Int,
    private var targetDevice: String,
    private var targetState: Int
) : DaggerBroadcastReceiver() {

    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    var gson = Gson()
    var applicationContext: Context? = null

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val device = intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        aapsLogger.info(LTag.PUMPBTCOMM, "in onReceive:  INTENT" + gson.toJson(intent))
        if (device == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "onReceive. Device is null. Exiting.")
            return
        } else {
            if (device.address != targetDevice) {
                aapsLogger.error(LTag.PUMPBTCOMM, "onReceive. Device is not the same as targetDevice. Exiting.")
                return
            }
        }

        // Check if action is valid
        if (action == null) return

        // Take action depending on new bond state
        if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
            aapsLogger.info(LTag.PUMPBTCOMM, "in onReceive: bondState=$bondState, previousBondState=$previousBondState")
            if (bondState == targetState) {
                aapsLogger.info(LTag.PUMPBTCOMM, "onReceive:  found targeted state: $targetState")
                val currentDeviceSettings = sp.getString(deviceAddress, "")
                if (currentDeviceSettings == targetDevice) {
                    if (targetState == 12) {
                        sp.putBoolean(bondedFlag, true)
                        rxBus.send(EventPumpConnectionParametersChanged())
                    } else if (targetState == 10) {
                        sp.putBoolean(bondedFlag, false)
                        rxBus.send(EventPumpConnectionParametersChanged())
                    }
                    context.unregisterReceiver(this)
                } else {
                    aapsLogger.error(LTag.PUMPBTCOMM, "onReceive:  Device stored in SP is not the same as target device, process interrupted")
                }
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "onReceive:  currentBondState=$bondState, targetBondState=$targetState")
            }
        }
    }
}