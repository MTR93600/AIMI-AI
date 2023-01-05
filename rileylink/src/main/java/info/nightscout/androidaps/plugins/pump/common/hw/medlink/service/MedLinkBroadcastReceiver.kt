package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service

import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject
import dagger.android.HasAndroidInjector
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError
import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.InitializeMedLinkPumpManagerTask
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.extensions.getHoursFromStart
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.WakeAndTuneTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import java.util.*

/**
 * Created by Dirceu on 29/09/20.
 */
class MedLinkBroadcastReceiver(val medLinkService: MedLinkService) : DaggerBroadcastReceiver() {

    @JvmField @Inject
    var medLinkServiceData: MedLinkServiceData? = null

    @JvmField @Inject
    var injector: HasAndroidInjector? = null

    @JvmField @Inject
    var sp: SP? = null

    @JvmField @Inject
    var aapsLogger: AAPSLogger? = null

    @JvmField @Inject
    var serviceTaskExecutor: ServiceTaskExecutor? = null

    @Inject
    lateinit var activePlugin: ActivePlugin
    private val broadcastIdentifiers: MutableMap<String, List<String>> = HashMap()
    private fun createBroadcastIdentifiers() {

        // Bluetooth
        broadcastIdentifiers["Bluetooth"] = listOf( //
            MedLinkConst.Intents.BluetoothConnected,  //
            MedLinkConst.Intents.BluetoothReconnected)

        // TuneUp
        broadcastIdentifiers["TuneUp"] = listOf( //
            RileyLinkConst.IPC.MSG_PUMP_tunePump,  //
            RileyLinkConst.IPC.MSG_PUMP_quickTune)

        // MedLink
        broadcastIdentifiers["MedLink"] = listOf( //
            MedLinkConst.Intents.MedLinkDisconnected,  //
            MedLinkConst.Intents.MedLinkReady,  //
            MedLinkConst.Intents.MedLinkNewAddressSet,  //
            MedLinkConst.Intents.MedLinkDisconnect)
    }

    fun getServiceInstance(): MedLinkService? {
        val pump = activePlugin!!.activePump
        return if (pump is MedLinkPumpDevice) {
            val pumpDevice = pump as MedLinkPumpDevice
            pumpDevice.getRileyLinkService()
        } else {
            null
        }
    }

    fun processBluetoothBroadcasts(action: String): Boolean {
        return if (action == MedLinkConst.Intents.BluetoothConnected) {
            aapsLogger!!.info(LTag.PUMPBTCOMM, "Bluetooth - Connected")
            serviceTaskExecutor!!.startTask(DiscoverGattServicesTask(injector!!, true))
            true
        } else if (action == MedLinkConst.Intents.BluetoothReconnected) {
            aapsLogger!!.info(LTag.PUMPBTCOMM, "Bluetooth - Reconnecting")
            getServiceInstance()!!.bluetoothInit()
            serviceTaskExecutor!!.startTask(DiscoverGattServicesTask(injector!!, true))
            true
        } else {
            false
        }
    }

    private fun processTuneUpBroadcasts(action: String): Boolean {
        return if (broadcastIdentifiers!!["TuneUp"]!!.contains(action)) {
            if (medLinkService.rileyLinkTargetDevice.isTuneUpEnabled) {
                serviceTaskExecutor!!.startTask(WakeAndTuneTask(injector))
            }
            true
        } else {
            false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent == null) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "onReceive: received null intent")
        } else {
            val action = intent.action
            if (action == null) {
                aapsLogger!!.error("onReceive: null action")
            } else {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "Received Broadcast: $action")
                if (!processBluetoothBroadcasts(action) &&  //
                    !processMedLinkBroadcasts(intent, context) &&  //
                    !processTuneUpBroadcasts(action) &&  //
                    !processApplicationSpecificBroadcasts(action, intent) //
                ) {
                    aapsLogger!!.error(LTag.PUMPBTCOMM, "Unhandled broadcast: action=$action")
                }
            }
        }
    }

    fun processApplicationSpecificBroadcasts(action: String, intent: Intent?): Boolean {
        aapsLogger?.debug("Applicationspecificbroadcasts $action")
        return false
    }

    protected fun processMedLinkBroadcasts(message: Intent, context: Context?): Boolean {
        val action = message.action
        aapsLogger!!.debug("processMedLinkBroadcasts $action")
        return if (action == MedLinkConst.Intents.MedLinkDisconnected) {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
                medLinkServiceData!!.setServiceState(MedLinkServiceState.BluetoothError,
                                                     MedLinkError.MedLinkUnreachable)
                //                medLinkServiceData.activePlugin
                //TODO schedule bg reading
            } else {
                medLinkServiceData!!.setServiceState(MedLinkServiceState.BluetoothError,
                                                     MedLinkError.BluetoothDisabled)
            }
            true
        } else if (action!!.startsWith(MedLinkConst.Intents.MedLinkReady)) {
            aapsLogger!!.warn(LTag.PUMPCOMM, "MedtronicConst.Intents.MedLinkReady")
            // sendIPCNotification(RT2Const.IPC.MSG_note_WakingPump);

//            medLinkService.getMedLinkBLE().enableNotifications();
//            medLinkService.getMedLinkRFSpy().startReader(); // call startReader from outside?
            val actions = action.split("\n").toTypedArray()
            getServiceInstance()!!.rfSpy.initializeMedLink()
            //            String bleVersion = medLinkService.getMedLinkRFSpy().getBLEVersionCached();
            val rlVersion = medLinkServiceData!!.firmwareVersion

//            if (isLoggingEnabled())
//            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy version (BLE113): " + bleVersion);
            if (message.getIntExtra("BatteryLevel", 0) != 0) {
                val medLinkService = getServiceInstance()
                if (medLinkService!!.activePlugin.activePump is MedLinkPumpPluginAbstract) {
                    val pump = medLinkService.activePlugin.activePump as MedLinkPumpPluginAbstract
                    pump.setBatteryLevel(message.getIntExtra("BatteryLevel", 0))
                }
                medLinkService.medLinkServiceData.versionBLE113 = message.getStringExtra("FirmwareVersion")
                medLinkServiceData!!.batteryLevel = message.getIntExtra("BatteryLevel", 0)
            } else {
                medLinkServiceData!!.versionBLE113 = ""
            }
            aapsLogger!!.debug(LTag.PUMPCOMM, "RfSpy Radio version (CC110): " + rlVersion.name)
            medLinkServiceData!!.versionCC110 = rlVersion.name
            val task: ServiceTask = InitializeMedLinkPumpManagerTask(injector!!, context!!)
            serviceTaskExecutor!!.startTask(task)
            aapsLogger!!.info(LTag.PUMPCOMM, "Announcing MedLink open For business")
            true
        } else if (action == MedLinkConst.Intents.MedLinkNewAddressSet) {
            val medLinkBLEAddress = sp!!.getString(MedLinkConst.Prefs.MedLinkAddress, "")
            if (medLinkBLEAddress == "") {
                aapsLogger!!.error("No MedLink BLE Address saved in app")
                aapsLogger!!.error(sp.toString())
                aapsLogger!!.error("ERROR" + sp!!.getString(RileyLinkConst.Prefs.RileyLinkAddress, ""))
            } else {
                aapsLogger!!.error("MedLink BLE Address saved in app")
                // showBusy("Configuring Service", 50);
//                medlink.findRileyLink(medLinkBLEAddress);
                val medLinkService = getServiceInstance()
                medLinkService?.reconfigureCommunicator(medLinkBLEAddress)
                //                MainApp.getServiceClientConnection().setThisRileylink(medLinkBLEAddress);
            }
            true
        } else if (action == RileyLinkConst.Intents.RileyLinkDisconnect) {
            val medLinkService = getServiceInstance()
            medLinkService!!.disconnectRileyLink()
            true
        } else if (action == MedLinkConst.Intents.MedLinkConnected) {
            medLinkServiceData!!.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady)
            true
        } else if (action == MedLinkConst.Intents.MedLinkConnectionError) {
            val pumpPlugin = activePlugin

            if (pumpPlugin is MedLinkPumpPluginAbstract) {
                    val batteryEvent = pumpPlugin.pumpSync.lastTherapyEvent(DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE)
                    batteryEvent.map {
                        if (it > 4 * 24 * 60.0 && pumpPlugin.getBatteryType() == "LiPo") {
                            pumpPlugin.rxBus.send(EventNewNotification(Notification(Notification.PUMP_UNREACHABLE, pumpPlugin.rh.gs(R.string.pump_unreachable), Notification.URGENT).also { msg -> msg.soundId = R.raw.alarm }))
                        }
                }
            }
            aapsLogger!!.info(LTag.PUMP, "pump unreachable")
            medLinkServiceData!!.setServiceState(MedLinkServiceState.PumpConnectorError,
                                                 MedLinkError.NoContactWithDevice)
            val tuneUpMessage = Intent()
            tuneUpMessage.action = RileyLinkConst.IPC.MSG_PUMP_tunePump
            processMedLinkBroadcasts(tuneUpMessage, context)
            true
        } else {
            false
        }
    }

    fun registerBroadcasts(context: Context?) {
        val intentFilter = IntentFilter()
        for ((_, value) in broadcastIdentifiers!!) {
            for (intentKey in value) {
                intentFilter.addAction(intentKey)
            }
        }
        LocalBroadcastManager.getInstance(context!!).registerReceiver(this, intentFilter)
    }

    fun unregisterBroadcasts(context: Context?) {
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(this)
    }

    init {
        createBroadcastIdentifiers()
    }
}