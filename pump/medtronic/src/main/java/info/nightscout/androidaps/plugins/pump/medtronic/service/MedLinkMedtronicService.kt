package info.nightscout.androidaps.plugins.pump.medtronic.service

import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import dagger.android.AndroidInjection
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkBluetoothStateReceiver
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkBroadcastReceiver
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUIComm
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUIPostprocessor
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicConst
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst
import info.nightscout.pump.core.defs.PumpDeviceState
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.rx.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
@Singleton
class MedLinkMedtronicService() : MedLinkService() {

    override var encoding: MedLinkEncodingType? = null
    @Inject override lateinit var deviceCommunicationManager: MedLinkMedtronicCommunicationManager

    @Inject
    lateinit var  medtronicPumpPlugin: MedLinkMedtronicPumpPlugin

    @Inject
    lateinit var medtronicUtil: MedLinkMedtronicUtil
    @Inject
    lateinit var  medtronicUIPostprocessor: MedLinkMedtronicUIPostprocessor
    @Inject
    lateinit var medtronicPumpStatus: MedLinkMedtronicPumpStatus

    @Inject
    lateinit var medtronicCommunicationManager: MedLinkMedtronicCommunicationManager

    @Inject lateinit var medLinkRFSpy: MedLinkRFSpy
    var medtronicUIComm: MedLinkMedtronicUIComm? = null
        private set
    private val mBinder: IBinder = LocalBinder()
    private var serialChanged = false
    private lateinit var frequencies: Array<String>
    private var medLinkAddress: String? = null
    private var medLinkAddressChanged = false
    private val encodingType: MedLinkEncodingType? = null
    private var encodingChanged = false
    private var inPreInit = true

    override fun onCreate() {
        AndroidInjection.inject(this)
        //        medLinkUtil.setEncoding(getEncoding());
        aapsLogger.info(LTag.EVENTS, "OnCreate medlinkmedtronicservice")
        initRileyLinkServiceData()
        mBroadcastReceiver = MedLinkBroadcastReceiver(this)
        mBroadcastReceiver!!.registerBroadcasts(this)
        bluetoothStateReceiver = MedLinkBluetoothStateReceiver()
        bluetoothStateReceiver!!.registerBroadcasts(this)
        aapsLogger.debug(LTag.PUMPCOMM, "MedLinkMedtronicService newly created")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        aapsLogger.warn(LTag.PUMPCOMM, "onConfigurationChanged")
        super.onConfigurationChanged(newConfig)
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }



    val isInitialized: Boolean
        get() = medLinkServiceData.medLinkServiceState.isReady

    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    override fun initRileyLinkServiceData() {

//        frequencies = new String[2];
//        frequencies[0] = resourceHelper.gs(R.string.key_medtronic_pump_frequency_us_ca);
//        frequencies[1] = resourceHelper.gs(R.string.key_medtronic_pump_frequency_worldwide);
        medLinkServiceData.targetDevice = RileyLinkTargetDevice.MedtronicPump
        setPumpIDString(sp.getString(MedLinkMedtronicConst.Prefs.PumpSerial, "000000"))

        // get most recently used RileyLink address
        medLinkServiceData.rileylinkAddress = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "")

        medLinkRFSpy.startReader();
        // init rileyLinkCommunicationManager
        medtronicUIComm = MedLinkMedtronicUIComm(injector, aapsLogger, medtronicUtil, medtronicUIPostprocessor, medtronicCommunicationManager)
        aapsLogger.debug(LTag.PUMPCOMM, "MedLinkMedtronicService newly constructed")
    }




    override fun setPumpDeviceState(pumpDeviceState: PumpDeviceState?) {
        medtronicPumpStatus.pumpDeviceState = pumpDeviceState
    }

    fun setPumpIDString(pumpID: String) {
        if (pumpID.length != 6) {
            aapsLogger.error("setPumpIDString: invalid pump id string: $pumpID")
            return
        }
        val pumpIDBytes = ByteUtil.fromHexString(pumpID)
        if (pumpIDBytes == null) {
            aapsLogger.error("Invalid pump ID? - PumpID is null.")
            medLinkServiceData.setPumpID("000000", byteArrayOf(0, 0, 0))
        } else if (pumpIDBytes.size != 3) {
            aapsLogger.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes))
            medLinkServiceData.setPumpID("000000", byteArrayOf(0, 0, 0))
        } else if (pumpID == "000000") {
            aapsLogger.error("Using pump ID $pumpID")
            medLinkServiceData.setPumpID(pumpID, byteArrayOf(0, 0, 0))
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "Using pump ID " + pumpID + "old pumpID is" + medLinkServiceData.pumpID)
            val oldId = medLinkServiceData.pumpID
            medLinkServiceData.setPumpID(pumpID, pumpIDBytes)
            if (oldId != null && oldId != pumpID) {
                medtronicUtil.medtronicPumpModel = null // if we change pumpId, model probably changed too
            }
            return
        }
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.InvalidConfiguration

        // LOG.info("setPumpIDString: saved pumpID " + idString);
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: MedLinkMedtronicService
            get() = this@MedLinkMedtronicService
    }

    /* private functions */ // PumpInterface - REMOVE
    //    public boolean isInitialized() {
    //        return RileyLinkServiceState.isReady(rileyLinkServiceData.rileyLinkServiceState);
    //    }
    //
    //
    //    @Override
    //    public String getDeviceSpecificBroadcastsIdentifierPrefix() {
    //        return null;
    //    }
    //    public boolean handleDeviceSpecificBroadcasts(Intent intent) {
    //        return false;
    //    }
    //    @Override
    //    public void registerDeviceSpecificBroadcasts(IntentFilter intentFilter) {
    //    }
    override fun verifyConfiguration(): Boolean {
        return try {
            val regexSN = "[0-9]{6}"
            val regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}"
            medtronicPumpStatus.errorDescription = "-"
            val serialNr = sp.getStringOrNull(MedtronicConst.Prefs.PumpSerial, null)
            if (serialNr == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "SerialNr is null")
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_serial_not_set)
                return false
            } else {
                if (!serialNr.matches(regexSN.toRegex())) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "SerialNr is invalid $serialNr")
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_serial_invalid)
                    return false
                } else {
                    if (serialNr != medtronicPumpStatus.serialNumber) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "SerialNr is  $serialNr")
                        medtronicPumpStatus.serialNumber = serialNr
                        serialChanged = true
                    }
                }
            }
            val pumpTypePref = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.PumpType, null)
            if (pumpTypePref == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Pump type not set")
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_type_not_set)
                return false
            } else {
                val pumpTypePart = pumpTypePref.substring(0, 3)
                if (!pumpTypePart.matches("[0-9]{3}".toRegex())) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Pump type unsupported $pumpTypePart")
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_type_invalid)
                    return false
                } else {
                    val pumpType = medtronicPumpStatus.medtronicPumpMap[pumpTypePart]
                    medtronicPumpStatus.medtronicDeviceType = medtronicPumpStatus.medtronicDeviceTypeMap[pumpTypePart]
                    medtronicPumpStatus.pumpType = pumpType!!
                    medtronicPumpPlugin.pumpType = pumpType!!
                    aapsLogger.debug(LTag.PUMPBTCOMM, "PumpTypePart $pumpTypePart")
                    if (pumpTypePart.startsWith("7")) medtronicPumpStatus.reservoirFullUnits = 300 else medtronicPumpStatus.reservoirFullUnits = 176
                }
            }
            val pumpFrequency = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.PumpFrequency, null)

//            if (pumpFrequency == null) {
//                aapsLogger.debug(LTag.PUMPBTCOMM,"Pump frequency is not set");
//                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_frequency_not_set);
//                return false;
//            } else {
//
//
//
//            }
            val rileyLinkAddress = sp.getStringOrNull(MedLinkConst.Prefs.MedLinkAddress, null)
            if (rileyLinkAddress == null) {
                aapsLogger.debug(LTag.PUMP, "MedLink address invalid: null")
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_medlink_address_invalid)
                return false
            } else {
                if (!rileyLinkAddress.matches(regexMac.toRegex())) {
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_medlink_address_invalid)
                    aapsLogger.debug(LTag.PUMP, "MedLink address invalid: {}", rileyLinkAddress)
                } else {
                    aapsLogger.debug(LTag.PUMP, "MedLink address : {}", rileyLinkAddress)
                    if (rileyLinkAddress != medLinkAddress) {
                        medLinkAddress = rileyLinkAddress
                        medLinkAddressChanged = true
                    }
                }
            }
            val maxBolusLcl = checkParameterValue(MedLinkMedtronicConst.Prefs.MaxBolus, "25.0", 25.0)
            if (medtronicPumpStatus.maxBolus == null || medtronicPumpStatus.maxBolus != maxBolusLcl) {
                medtronicPumpStatus.maxBolus = maxBolusLcl
                aapsLogger.debug("Max Bolus from AAPS settings is " + medtronicPumpStatus.maxBolus)
            }
            val maxBasalLcl = checkParameterValue(MedLinkMedtronicConst.Prefs.MaxBasal, "35.0", 35.0)
            if (medtronicPumpStatus.maxBasal == null || medtronicPumpStatus.maxBasal != maxBasalLcl) {
                medtronicPumpStatus.maxBasal = maxBasalLcl
                aapsLogger.debug("Max Basal from AAPS settings is " + medtronicPumpStatus.maxBasal)
            }
            val encodingTypeStr = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.Encoding, null)

//            if (encodingTypeStr == null) {
//                aapsLogger.debug(LTag.PUMPBTCOMM,"Encoding type is null");
//                return false;
//            }

//            RileyLinkEncodingType newEncodingType = RileyLinkEncodingType.getByDescription(encodingTypeStr, resourceHelper);
//
//            if (encodingType == null) {
//                encodingType = newEncodingType;
//            } else if (encodingType != newEncodingType) {
//                encodingType = newEncodingType;
//                encodingChanged = true;
//            }
            val batteryTypeStr = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.BatteryType, null)
            if (batteryTypeStr == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "MedlinkL BatteryTypeStr is null")
                return false
            }
            val batteryType = medtronicPumpStatus.getBatteryTypeByDescription(batteryTypeStr)
            if (medtronicPumpStatus.batteryType !== batteryType) {
                medtronicPumpStatus.batteryType = batteryType
            }

            //String bolusDebugEnabled = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.BolusDebugEnabled, null);
            //boolean bolusDebug = bolusDebugEnabled != null && bolusDebugEnabled.equals(resourceHelper.gs(R.string.common_on));
            //MedtronicHistoryData.doubleBolusDebug = bolusDebug;
            aapsLogger.debug("MedlinkL before reconfigure")
            reconfigureService()
            aapsLogger.debug("MedlinkL reconfigured")
            true
        } catch (ex: Exception) {
            medtronicPumpStatus.errorDescription = ex.message
            aapsLogger.error(LTag.PUMP, "MedlinkL Error on Verification: " + ex.message, ex)
            false
        }
    }

    private fun reconfigureService(): Boolean {
        if (!inPreInit) {
            if (serialChanged) {
                setPumpIDString(medtronicPumpStatus.serialNumber) // short operation
                serialChanged = false
            }
            if (medLinkAddressChanged) {
                medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkNewAddressSet, this)
                medLinkAddressChanged = false
            }
            if (encodingChanged) {
                changeMedLinkEncoding(encodingType)
                encodingChanged = false
            }
        }

        // if (targetFrequencyChanged && !inPreInit && MedtronicUtil.getMedtronicService() != null) {
        // RileyLinkUtil.setRileyLinkTargetFrequency(targetFrequency);
        // // RileyLinkUtil.getRileyLinkCommunicationManager().refreshRileyLinkTargetFrequency();
        // targetFrequencyChanged = false;
        // }
        return !medLinkAddressChanged && !serialChanged && !encodingChanged // && !targetFrequencyChanged);
    }

    private fun checkParameterValue(key: Int, defaultValue: String, defaultValueDouble: Double): Double {
        var `val`: Double
        val value = sp.getString(key, defaultValue)
        `val` = try {
            value.toDouble()
        } catch (ex: Exception) {
            aapsLogger.error("Error parsing setting: {}, value found {}", key, value)
            defaultValueDouble
        }
        if (`val` > defaultValueDouble) {
            sp.putString(key, defaultValue)
            `val` = defaultValueDouble
        }
        return `val`
    }

    fun setNotInPreInit(): Boolean {
        inPreInit = false
        return reconfigureService()
    }
}