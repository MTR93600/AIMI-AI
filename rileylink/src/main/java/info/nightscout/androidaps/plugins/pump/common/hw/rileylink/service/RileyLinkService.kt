package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

/**
 * Created by andy on 5/6/18.
 * Split from original file and renamed.
 */
abstract class RileyLinkService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rileyLinkBLE: RileyLinkBLE     // android-bluetooth management
    @Inject lateinit var rfspy: RFSpy // interface for RL xxx Mhz radio.

    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    protected var mBroadcastReceiver: RileyLinkBroadcastReceiver? = null
    private var bluetoothStateReceiver: RileyLinkBluetoothStateReceiver? = null

    override fun onCreate() {
        super.onCreate()
        rileyLinkUtil.encoding = encoding
        initRileyLinkServiceData()
        mBroadcastReceiver = RileyLinkBroadcastReceiver(this)
        mBroadcastReceiver?.registerBroadcasts(this)
        bluetoothStateReceiver = RileyLinkBluetoothStateReceiver()
        bluetoothStateReceiver?.registerBroadcasts(this)
    }

    /**
     * Get Encoding for RileyLink communication
     */
    abstract val encoding: RileyLinkEncodingType

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

        rileyLinkBLE.disconnect() // dispose of Gatt (disconnect and close)
        mBroadcastReceiver?.unregisterBroadcasts(this)
        bluetoothStateReceiver?.unregisterBroadcasts(this)
    }

    abstract val deviceCommunicationManager: RileyLinkCommunicationManager<*>
    val rileyLinkServiceState: RileyLinkServiceState?
        get() = rileyLinkServiceData.rileyLinkServiceState

    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int = START_STICKY

    fun bluetoothInit(): Boolean {
        aapsLogger.debug(LTag.PUMPBTCOMM, "bluetoothInit: attempting to get an adapter")
        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.BluetoothInitializing)
        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter)
        } else {
            if (bluetoothAdapter?.isEnabled != true) {
                aapsLogger.error("Bluetooth is not enabled.")
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled)
            } else {
                rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.BluetoothReady)
                return true
            }
        }
        return false
    }

    // returns true if our Rileylink configuration changed
    fun reconfigureRileyLink(deviceAddress: String): Boolean {
        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkInitializing)
        return if (rileyLinkBLE.isConnected) {
            if (deviceAddress == rileyLinkServiceData.rileyLinkAddress) {
                aapsLogger.info(LTag.PUMPBTCOMM, "No change to RL address.  Not reconnecting.")
                false
            } else {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Disconnecting from old RL (${rileyLinkServiceData.rileyLinkAddress}), reconnecting to new: $deviceAddress")
                rileyLinkBLE.disconnect()
                // need to shut down listening thread too?
                // SP.putString(MedtronicConst.Prefs.RileyLinkAddress, deviceAddress);
                rileyLinkServiceData.rileyLinkAddress = deviceAddress
                rileyLinkBLE.findRileyLink(deviceAddress)
                true
            }
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using RL $deviceAddress")
            if (rileyLinkServiceData.getRileyLinkServiceState() == RileyLinkServiceState.NotStarted) {
                if (!bluetoothInit()) {
                    aapsLogger.error("RileyLink can't get activated, Bluetooth is not functioning correctly. ${error?.name ?: "Unknown error (null)"}")
                    return false
                }
            }
            rileyLinkBLE.findRileyLink(deviceAddress)
            true
        }
    }

    // FIXME: This needs to be run in a session so that is interruptible, has a separate thread, etc.
    fun doTuneUpDevice() {
        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.TuneUpDevice)
        setPumpDeviceState(PumpDeviceState.Sleeping)
        val lastGoodFrequency =  rileyLinkServiceData.lastGoodFrequency ?: sp.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0)
        val newFrequency = deviceCommunicationManager.tuneForDevice()
        if (newFrequency != 0.0 && newFrequency != lastGoodFrequency) {
            aapsLogger.info(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Saving new pump frequency of %.3f MHz", newFrequency))
            sp.putDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, newFrequency)
            rileyLinkServiceData.lastGoodFrequency = newFrequency
            rileyLinkServiceData.tuneUpDone = true
            rileyLinkServiceData.lastTuneUpTime = System.currentTimeMillis()
        }
        if (newFrequency == 0.0) {
            // error tuning pump, pump not present ??
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError, RileyLinkError.TuneUpOfDeviceFailed)
        } else {
            deviceCommunicationManager.clearNotConnectedCount()
            rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady)
        }
    }

    abstract fun setPumpDeviceState(pumpDeviceState: PumpDeviceState)
    fun disconnectRileyLink() {
        if (rileyLinkBLE.isConnected) {
            rileyLinkBLE.disconnect()
            rileyLinkServiceData.rileyLinkAddress = null
            rileyLinkServiceData.rileyLinkName = null
        }
        rileyLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.BluetoothReady)
    }

    /**
     * Get Target Device for Service
     */
    val rileyLinkTargetDevice: RileyLinkTargetDevice
        get() = rileyLinkServiceData.targetDevice

    fun changeRileyLinkEncoding(encodingType: RileyLinkEncodingType?) {
        rfspy.setRileyLinkEncoding(encodingType)
    }

    val error: RileyLinkError?
        get() = rileyLinkServiceData.rileyLinkError

    fun verifyConfiguration(): Boolean {
        return verifyConfiguration(false)
    }

    abstract fun verifyConfiguration(forceRileyLinkAddressRenewal: Boolean): Boolean
}