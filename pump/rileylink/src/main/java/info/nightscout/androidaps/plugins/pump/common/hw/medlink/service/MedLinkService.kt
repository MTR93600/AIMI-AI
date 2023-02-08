package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.pump.core.defs.PumpDeviceState
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

/**
 * Created by Dirceu on 30/09/20.
 */
abstract class MedLinkService : DaggerService() {

    @Inject lateinit var rfSpy: MedLinkRFSpy

    @Inject
    lateinit var aapsLogger: AAPSLogger

    @Inject
    lateinit var sp: SP

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var rxBus: RxBus

    @Inject
    lateinit var medLinkUtil: MedLinkUtil

    @Inject
    lateinit var injector: HasAndroidInjector

    @Inject
    lateinit var resourceHelper: ResourceHelper

    @Inject
    lateinit var medLinkServiceData: MedLinkServiceData

    @Inject
    lateinit var activePlugin: ActivePlugin

    @Inject
    lateinit var medLinkBLE: MedLinkBLE

    protected var bluetoothAdapter: BluetoothAdapter? = null
    protected var mBroadcastReceiver: MedLinkBroadcastReceiver? = null
    protected var bluetoothStateReceiver: MedLinkBluetoothStateReceiver? = null
    override fun onCreate() {
        super.onCreate()
        aapsLogger.info(LTag.EVENTS, "OnCreate medlinkservice")
        medLinkUtil.encoding = encoding
        initRileyLinkServiceData()
        mBroadcastReceiver = MedLinkBroadcastReceiver(this)
        mBroadcastReceiver!!.registerBroadcasts(this)
        bluetoothStateReceiver = MedLinkBluetoothStateReceiver()
        bluetoothStateReceiver!!.registerBroadcasts(this)
    }

    /**
     * Get Encoding for RileyLink communication
     */
    abstract val encoding: MedLinkEncodingType?

    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    abstract fun initRileyLinkServiceData()
    override fun onUnbind(intent: Intent): Boolean {
        //aapsLogger.warn(LTag.PUMPBTCOMM, "onUnbind");
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent) {
        //aapsLogger.warn(LTag.PUMPBTCOMM, "onRebind");
        super.onRebind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        //LOG.error("I die! I die!");

        // xyz rfspy.stopReader();
        medLinkBLE.disconnect() // dispose of Gatt (disconnect and close)
        mBroadcastReceiver?.unregisterBroadcasts(this)
        bluetoothStateReceiver?.unregisterBroadcasts(this)
    }

    abstract val deviceCommunicationManager: MedLinkCommunicationManager

    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun bluetoothInit(): Boolean {
        aapsLogger.debug(LTag.PUMPBTCOMM, "bluetoothInit: attempting to get an adapter")
        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.BluetoothInitializing)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            medLinkServiceData.setServiceState(MedLinkServiceState.BluetoothError,
                                                 MedLinkError.NoBluetoothAdapter)
        } else {
            if (!bluetoothAdapter!!.isEnabled) {
                aapsLogger.error("Bluetooth is not enabled.")
                medLinkServiceData.setServiceState(MedLinkServiceState.BluetoothError,
                                                     MedLinkError.BluetoothDisabled)
            } else {
                medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.BluetoothReady)
                return true
            }
        }
        return false
    }

    // returns true if our Rileylink configuration changed
    fun reconfigureCommunicator(deviceAddress: String): Boolean {
        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkInitializing)
        return if (medLinkBLE.isConnected()) {
            if (deviceAddress == medLinkServiceData.rileylinkAddress) {
                aapsLogger.info(LTag.PUMPBTCOMM, "No change to RL address.  Not reconnecting.")
                false
            } else {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Disconnecting from old RL (" + medLinkServiceData.rileylinkAddress
                    + "), reconnecting to new: " + deviceAddress)
                medLinkBLE.disconnect()
                // prolly need to shut down listening thread too?
                // SP.putString(MedtronicConst.Prefs.RileyLinkAddress, deviceAddress);
                medLinkServiceData.rileylinkAddress = deviceAddress
                medLinkBLE.findMedLink(medLinkServiceData.rileylinkAddress)
                true
            }
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using ML $deviceAddress")
            if (medLinkServiceData.getMedLinkServiceState() == MedLinkServiceState.NotStarted) {
                if (!bluetoothInit()) {
                    aapsLogger.error("MedLink can't get activated, Bluetooth is not functioning correctly. {}",
                                       if (error != null) error!!.name else "Unknown error (null)")
                    return false
                }
            }
            medLinkBLE.findMedLink(deviceAddress)
            true
        }
    }

    // FIXME: This needs to be run in a session so that is interruptable, has a separate thread, etc.
    fun doTuneUpDevice() {
        aapsLogger.debug("DoTuneIp")
        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.TuneUpDevice)
        setPumpDeviceState(PumpDeviceState.Sleeping)
        var lastGoodFrequency = 0.0
        lastGoodFrequency = if (medLinkServiceData.lastGoodFrequency == null) {
            sp.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0)
        } else {
            medLinkServiceData.lastGoodFrequency
        }
        val newFrequency: Double
        newFrequency = deviceCommunicationManager.tuneForDevice()
        if (newFrequency != 0.0 && newFrequency != lastGoodFrequency) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Saving new pump frequency of {} MHz", newFrequency)
            sp.putDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, newFrequency)
            medLinkServiceData.lastGoodFrequency = newFrequency
            medLinkServiceData.tuneUpDone = true
            medLinkServiceData.lastTuneUpTime = System.currentTimeMillis()
        }
        aapsLogger.debug("New pump frequency $newFrequency")
        if (newFrequency == 0.0) {
            // error tuning pump, pump not present ??
            medLinkServiceData.setServiceState(MedLinkServiceState.PumpConnectorError,
                                                 MedLinkError.TuneUpOfDeviceFailed)
        } else {
            deviceCommunicationManager.clearNotConnectedCount()
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady)
        }
    }

    abstract fun setPumpDeviceState(pumpDeviceState: PumpDeviceState?)
    fun disconnectRileyLink() {
        if (medLinkBLE.isConnected()) {
            aapsLogger.info(LTag.PUMPBTCOMM, "disconnectingMedlink")
            medLinkBLE.disconnect()
            medLinkServiceData.rileylinkAddress = null
        }
        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.BluetoothReady)
    }


    /**
     * Get Target Device for Service
     */
    val rileyLinkTargetDevice: RileyLinkTargetDevice
        get() = medLinkServiceData.targetDevice

    fun changeMedLinkEncoding(encodingType: MedLinkEncodingType?) {}
    val error: MedLinkError?
        get() = medLinkServiceData.medLinkError

    abstract fun verifyConfiguration(): Boolean
}