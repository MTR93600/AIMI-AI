package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.android.DaggerBroadcastReceiver
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class RileyLinkBroadcastReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var serviceTaskExecutor: ServiceTaskExecutor
    @Inject lateinit var activePlugin: ActivePlugin

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val broadcastIdentifiers: MutableMap<String, List<String>> = HashMap()

    init {
        createBroadcastIdentifiers()
    }

    private val rileyLinkService: RileyLinkService?
        get() = (activePlugin.activePump as RileyLinkPumpDevice).rileyLinkService

    private fun createBroadcastIdentifiers() {

        // Bluetooth
        broadcastIdentifiers["Bluetooth"] = listOf(
            RileyLinkConst.Intents.BluetoothConnected,
            RileyLinkConst.Intents.BluetoothReconnected
        )

        // TuneUp
        broadcastIdentifiers["TuneUp"] = listOf(
            RileyLinkConst.IPC.MSG_PUMP_tunePump,
            RileyLinkConst.IPC.MSG_PUMP_quickTune
        )

        // RileyLink
        broadcastIdentifiers["RileyLink"] = listOf(
            RileyLinkConst.Intents.RileyLinkDisconnected,
            RileyLinkConst.Intents.RileyLinkReady,
            RileyLinkConst.Intents.RileyLinkDisconnected,
            RileyLinkConst.Intents.RileyLinkNewAddressSet,
            RileyLinkConst.Intents.RileyLinkDisconnect
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        handler.post {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Received Broadcast: $action")
            if (!processBluetoothBroadcasts(action) && !processRileyLinkBroadcasts(action, context) && !processTuneUpBroadcasts(action))
                aapsLogger.error(LTag.PUMPBTCOMM, "Unhandled broadcast: action=$action")
        }
    }

    fun registerBroadcasts(context: Context) {
        val intentFilter = IntentFilter()
        for ((_, value) in broadcastIdentifiers)
            for (intentKey in value)
                intentFilter.addAction(intentKey)
        LocalBroadcastManager.getInstance(context).registerReceiver(this, intentFilter)
    }

    fun unregisterBroadcasts(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
    }

    private fun processRileyLinkBroadcasts(action: String, context: Context): Boolean =
        when (action) {
            RileyLinkConst.Intents.RileyLinkDisconnected  -> {
                if ((context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled)
                    rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.RileyLinkUnreachable)
                else
                    rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled)
                true
            }

            RileyLinkConst.Intents.RileyLinkReady         -> {
                aapsLogger.warn(LTag.PUMPBTCOMM, "RileyLinkConst.Intents.RileyLinkReady")
                // sendIPCNotification(RT2Const.IPC.MSG_note_WakingPump);
                rileyLinkService?.rileyLinkBLE?.enableNotifications()
                rileyLinkService?.rfSpy?.startReader() // call startReader from outside?
                rileyLinkService?.rfSpy?.initializeRileyLink()
                val bleVersion = rileyLinkService?.rfSpy?.bleVersionCached
                val rlVersion = rileyLinkServiceData.firmwareVersion
                aapsLogger.debug(LTag.PUMPBTCOMM, "RfSpy version (BLE113): $bleVersion")
                rileyLinkService?.rileyLinkServiceData?.versionBLE113 = bleVersion

                aapsLogger.debug(LTag.PUMPBTCOMM, "RfSpy Radio version (CC110): ${rlVersion?.name}")
                rileyLinkServiceData.firmwareVersion = rlVersion
                val task: ServiceTask = InitializePumpManagerTask(injector, context)
                serviceTaskExecutor.startTask(task)
                aapsLogger.info(LTag.PUMPBTCOMM, "Announcing RileyLink open For business")
                true
            }

            RileyLinkConst.Intents.RileyLinkNewAddressSet -> {
                val rileylinkBLEAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "")
                if (rileylinkBLEAddress == "") aapsLogger.error("No Rileylink BLE Address saved in app")
                else rileyLinkService?.reconfigureRileyLink(rileylinkBLEAddress)
                true
            }

            RileyLinkConst.Intents.RileyLinkDisconnect    -> {
                rileyLinkService?.disconnectRileyLink()
                true
            }

            else                                          -> false
        }

    private fun processBluetoothBroadcasts(action: String): Boolean =
        when (action) {
            RileyLinkConst.Intents.BluetoothConnected   -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Bluetooth - Connected")
                serviceTaskExecutor.startTask(DiscoverGattServicesTask(injector))
                true
            }

            RileyLinkConst.Intents.BluetoothReconnected -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Bluetooth - Reconnecting")
                rileyLinkService?.bluetoothInit()
                serviceTaskExecutor.startTask(DiscoverGattServicesTask(injector, true))
                true
            }

            else                                        -> false
        }

    private fun processTuneUpBroadcasts(action: String): Boolean =
        if (broadcastIdentifiers["TuneUp"]?.contains(action) == true) {
            if (rileyLinkServiceData.targetDevice?.isTuneUpEnabled == true) serviceTaskExecutor.startTask(WakeAndTuneTask(injector))
            true
        } else false
}