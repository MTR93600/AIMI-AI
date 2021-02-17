package info.nightscout.androidaps.plugins.pump.medtronic.service;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkBluetoothStateReceiver;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkBroadcastReceiver;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.R;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUIComm;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUIPostprocessor;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
public class MedLinkMedtronicService extends MedLinkService {

    @Inject MedLinkMedtronicPumpPlugin medtronicPumpPlugin;
    @Inject MedLinkMedtronicUtil medtronicUtil;
    @Inject MedLinkMedtronicUIPostprocessor medtronicUIPostprocessor;
    @Inject MedLinkMedtronicPumpStatus medtronicPumpStatus;
    @Inject MedLinkBLE medlinkBLE;
    @Inject MedLinkMedtronicCommunicationManager medtronicCommunicationManager;
    private MedLinkMedtronicUIComm medtronicUIComm;

    private IBinder mBinder = new LocalBinder();

    private boolean serialChanged = false;
    private String[] frequencies;
    private String medLinkAddress = null;
    private boolean medLinkAddressChanged = false;
    private MedLinkEncodingType encodingType;
    private boolean encodingChanged = false;
    private boolean inPreInit = true;


    public MedLinkMedtronicService() {
        super();
    }


    @Override public void onCreate() {
        AndroidInjection.inject(this);
//        medLinkUtil.setEncoding(getEncoding());
        initRileyLinkServiceData();

        mBroadcastReceiver = new MedLinkBroadcastReceiver(this);
        mBroadcastReceiver.registerBroadcasts(this);

        bluetoothStateReceiver = new MedLinkBluetoothStateReceiver();
        bluetoothStateReceiver.registerBroadcasts(this);
        aapsLogger.debug(LTag.PUMPCOMM, "MedLinkMedtronicService newly created");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        aapsLogger.warn(LTag.PUMPCOMM, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public MedLinkEncodingType getEncoding() {
        return MedLinkEncodingType.FourByteSixByteLocal;
    }

    public boolean isInitialized() {
        return medLinkServiceData.rileyLinkServiceState.isReady();
    }

    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public void initRileyLinkServiceData() {

        frequencies = new String[2];
        frequencies[0] = resourceHelper.gs(R.string.key_medtronic_pump_frequency_us_ca);
        frequencies[1] = resourceHelper.gs(R.string.key_medtronic_pump_frequency_worldwide);

        medLinkServiceData.targetDevice = RileyLinkTargetDevice.MedtronicPump;

        setPumpIDString(sp.getString(MedLinkMedtronicConst.Prefs.PumpSerial, "000000"));

        // get most recently used RileyLink address
        medLinkServiceData.rileylinkAddress = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "");

//        getMedLinkRFSpy.startReader();

        // init rileyLinkCommunicationManager
        medtronicUIComm = new MedLinkMedtronicUIComm(injector, aapsLogger, medtronicUtil, medtronicUIPostprocessor, medtronicCommunicationManager);

        aapsLogger.debug(LTag.PUMPCOMM, "MedLinkMedtronicService newly constructed");
    }


    public void resetRileyLinkConfiguration() {
        getMedLinkRFSpy.resetRileyLinkConfiguration();
    }


    @Override
    public MedLinkCommunicationManager getDeviceCommunicationManager() {
        return this.medtronicCommunicationManager;
    }


    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.medtronicPumpStatus.setPumpDeviceState(pumpDeviceState);
    }


    public MedLinkMedtronicUIComm getMedtronicUIComm() {
        return medtronicUIComm;
    }

    public void setPumpIDString(String pumpID) {
        if (pumpID.length() != 6) {
            aapsLogger.error("setPumpIDString: invalid pump id string: " + pumpID);
            return;
        }

        byte[] pumpIDBytes = ByteUtil.fromHexString(pumpID);

        if (pumpIDBytes == null) {
            aapsLogger.error("Invalid pump ID? - PumpID is null.");

            medLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpIDBytes.length != 3) {
            aapsLogger.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));

            medLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpID.equals("000000")) {
            aapsLogger.error("Using pump ID " + pumpID);

            medLinkServiceData.setPumpID(pumpID, new byte[]{0, 0, 0});

        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "Using pump ID " + pumpID + "old pumpID is"+ medLinkServiceData.pumpID);

            String oldId = medLinkServiceData.pumpID;

            medLinkServiceData.setPumpID(pumpID, pumpIDBytes);

            if (oldId != null && !oldId.equals(pumpID)) {
                medtronicUtil.setMedtronicPumpModel(null); // if we change pumpId, model probably changed too
            }

            return;
        }

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.InvalidConfiguration);

        // LOG.info("setPumpIDString: saved pumpID " + idString);
    }

    public class LocalBinder extends Binder {

        public MedLinkMedtronicService getServiceInstance() {
            return MedLinkMedtronicService.this;
        }
    }


    /* private functions */

    // PumpInterface - REMOVE

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

    public boolean verifyConfiguration() {
        try {
            String regexSN = "[0-9]{6}";
            String regexMac = "([\\da-fA-F]{1,2}(?:\\:|$)){6}";

            medtronicPumpStatus.errorDescription = "-";

            String serialNr = sp.getStringOrNull(MedtronicConst.Prefs.PumpSerial, null);

            if (serialNr == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM,"SerialNr is null");
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_serial_not_set);
                return false;
            } else {
                if (!serialNr.matches(regexSN)) {
                    aapsLogger.debug(LTag.PUMPBTCOMM,"SerialNr is invalid "+serialNr);
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_serial_invalid);
                    return false;
                } else {
                    if (!serialNr.equals(medtronicPumpStatus.serialNumber)) {
                        aapsLogger.debug(LTag.PUMPBTCOMM,"SerialNr is  "+serialNr);
                        medtronicPumpStatus.serialNumber = serialNr;
                        serialChanged = true;
                    }
                }
            }

            String pumpTypePref = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.PumpType, null);

            if (pumpTypePref == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM,"Pump type not set");
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_type_not_set);
                return false;
            } else {
                String pumpTypePart = pumpTypePref.substring(0, 3);

                if (!pumpTypePart.matches("[0-9]{3}")) {
                    aapsLogger.debug(LTag.PUMPBTCOMM,"Pump type unsupported "+pumpTypePart);
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_type_invalid);
                    return false;
                } else {
                    PumpType pumpType = medtronicPumpStatus.getMedtronicPumpMap().get(pumpTypePart);
                    medtronicPumpStatus.medtronicDeviceType = medtronicPumpStatus.getMedtronicDeviceTypeMap().get(pumpTypePart);
                    medtronicPumpPlugin.setPumpType(pumpType);
                    aapsLogger.debug(LTag.PUMPBTCOMM,"PumpTypePart "+pumpTypePart);
                    if (pumpTypePart.startsWith("7"))
                        medtronicPumpStatus.reservoirFullUnits = 300;
                    else
                        medtronicPumpStatus.reservoirFullUnits = 176;
                }
            }

            String pumpFrequency = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.PumpFrequency, null);

            if (pumpFrequency == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM,"Pump frequency is not set");
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_frequency_not_set);
                return false;
            } else {
                if (!pumpFrequency.equals(frequencies[0]) && !pumpFrequency.equals(frequencies[1])) {
                    aapsLogger.debug(LTag.PUMPBTCOMM,"Unsupported pump frequency "+pumpFrequency);
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_pump_frequency_invalid);
                    return false;
                } else {
                    medtronicPumpStatus.pumpFrequency = pumpFrequency;
                    boolean isFrequencyUS = pumpFrequency.equals(frequencies[0]);
                    aapsLogger.debug(LTag.PUMPBTCOMM,"Pump frequency "+pumpFrequency);
                    RileyLinkTargetFrequency newTargetFrequency = isFrequencyUS ? //
                            RileyLinkTargetFrequency.Medtronic_US
                            : RileyLinkTargetFrequency.Medtronic_WorldWide;

                    if (medLinkServiceData.rileyLinkTargetFrequency != newTargetFrequency) {
                        medLinkServiceData.rileyLinkTargetFrequency = newTargetFrequency;
                    }

                }
            }

            String rileyLinkAddress = sp.getStringOrNull(MedLinkConst.Prefs.MedLinkAddress, null);

            if (rileyLinkAddress == null) {
                aapsLogger.debug(LTag.PUMP, "MedLink address invalid: null");
                medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_medlink_address_invalid);
                return false;
            } else {
                if (!rileyLinkAddress.matches(regexMac)) {
                    medtronicPumpStatus.errorDescription = resourceHelper.gs(R.string.medtronic_error_medlink_address_invalid);
                    aapsLogger.debug(LTag.PUMP, "MedLink address invalid: {}", rileyLinkAddress);
                } else {
                    aapsLogger.debug(LTag.PUMP, "MedLink address : {}", rileyLinkAddress);
                    if (!rileyLinkAddress.equals(this.medLinkAddress)) {
                        this.medLinkAddress = rileyLinkAddress;
                        medLinkAddressChanged = true;
                    }
                }
            }

            double maxBolusLcl = checkParameterValue(MedLinkMedtronicConst.Prefs.MaxBolus, "25.0", 25.0d);

            if (medtronicPumpStatus.maxBolus == null || !medtronicPumpStatus.maxBolus.equals(maxBolusLcl)) {
                medtronicPumpStatus.maxBolus = maxBolusLcl;

                aapsLogger.debug("Max Bolus from AAPS settings is " + medtronicPumpStatus.maxBolus);
            }

            double maxBasalLcl = checkParameterValue(MedLinkMedtronicConst.Prefs.MaxBasal, "35.0", 35.0d);

            if (medtronicPumpStatus.maxBasal == null || !medtronicPumpStatus.maxBasal.equals(maxBasalLcl)) {
                medtronicPumpStatus.maxBasal = maxBasalLcl;

                aapsLogger.debug("Max Basal from AAPS settings is " + medtronicPumpStatus.maxBasal);
            }


            String encodingTypeStr = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.Encoding, null);

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

            String batteryTypeStr = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.BatteryType, null);

            if (batteryTypeStr == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "MedlinkL BatteryTypeStr is null");
                return false;
            }

            BatteryType batteryType = medtronicPumpStatus.getBatteryTypeByDescription(batteryTypeStr);

            if (medtronicPumpStatus.batteryType != batteryType) {
                medtronicPumpStatus.batteryType = batteryType;
            }

            //String bolusDebugEnabled = sp.getStringOrNull(MedLinkMedtronicConst.Prefs.BolusDebugEnabled, null);
            //boolean bolusDebug = bolusDebugEnabled != null && bolusDebugEnabled.equals(resourceHelper.gs(R.string.common_on));
            //MedtronicHistoryData.doubleBolusDebug = bolusDebug;
            aapsLogger.debug("MedlinkL before reconfigure");
            reconfigureService();
            aapsLogger.debug("MedlinkL reconfigured");
            return true;

        } catch (Exception ex) {
            medtronicPumpStatus.errorDescription = ex.getMessage();
            aapsLogger.error(LTag.PUMP, "MedlinkL Error on Verification: " + ex.getMessage(), ex);
            return false;
        }
    }

    private boolean reconfigureService() {

        if (!inPreInit) {

            if (serialChanged) {
                setPumpIDString(medtronicPumpStatus.serialNumber); // short operation
                serialChanged = false;
            }

            if (medLinkAddressChanged) {
                medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.RileyLinkNewAddressSet, this);
                medLinkAddressChanged = false;
            }

            if (encodingChanged) {
                changeMedLinkEncoding(encodingType);
                encodingChanged = false;
            }
        }


        // if (targetFrequencyChanged && !inPreInit && MedtronicUtil.getMedtronicService() != null) {
        // RileyLinkUtil.setRileyLinkTargetFrequency(targetFrequency);
        // // RileyLinkUtil.getRileyLinkCommunicationManager().refreshRileyLinkTargetFrequency();
        // targetFrequencyChanged = false;
        // }

        return (!medLinkAddressChanged && !serialChanged && !encodingChanged); // && !targetFrequencyChanged);
    }

    private double checkParameterValue(int key, String defaultValue, double defaultValueDouble) {
        double val;

        String value = sp.getString(key, defaultValue);

        try {
            val = Double.parseDouble(value);
        } catch (Exception ex) {
            aapsLogger.error("Error parsing setting: {}, value found {}", key, value);
            val = defaultValueDouble;
        }

        if (val > defaultValueDouble) {
            sp.putString(key, defaultValue);
            val = defaultValueDouble;
        }

        return val;
    }

    public boolean setNotInPreInit() {
        this.inPreInit = false;

        return reconfigureService();
    }
}
