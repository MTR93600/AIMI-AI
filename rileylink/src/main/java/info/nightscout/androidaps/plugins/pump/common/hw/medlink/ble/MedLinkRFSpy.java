package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import android.os.SystemClock;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.CC111XRegister;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RXFilterMode;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by Dirceu on 06/10/20.
 * copied from @class{RFSpy}
 */
@Singleton
public class MedLinkRFSpy {
    @Inject AAPSLogger aapsLogger;
    @Inject ResourceHelper resourceHelper;
    @Inject SP sp;
    @Inject MedLinkServiceData medLinkServiceData;
    @Inject MedLinkUtil medLinkUtil;

    private final HasAndroidInjector injector;

    private static final long RILEYLINK_FREQ_XTAL = 24000000;
    private static final int EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 7500; // 1500
    public int notConnectedCount = 0;
    private MedLinkBLE medLinkBle;
    private MedLinkRFSpyReader reader;
    private UUID radioServiceUUID = UUID.fromString(GattAttributes.SERVICE_UUID);
    private UUID radioDataUUID = UUID.fromString(GattAttributes.GATT_UUID);
    private UUID radioVersionUUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION);
    //private UUID responseCountUUID = UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT);
    private RileyLinkFirmwareVersion firmwareVersion;
    private String bleVersion; // We don't use it so no need of sofisticated logic
    private Double currentFrequencyMHz;


    @Inject
    public MedLinkRFSpy(HasAndroidInjector injector, MedLinkBLE medLinkBle) {
        this.injector = injector;
        this.medLinkBle = medLinkBle;
    }

    @Inject
    public void onInit() {
        aapsLogger.debug("RileyLinkServiceData:" + medLinkServiceData);
        reader = new MedLinkRFSpyReader(aapsLogger, medLinkBle);
    }


    public RileyLinkFirmwareVersion getRLVersionCached() {
        return firmwareVersion;
    }


    public String getBLEVersionCached() {
        return bleVersion;
    }


    // Call this after the RL services are discovered.
    // Starts an async task to read when data is available
    public void startReader() {
//        aapsLogger.debug("RFSpy start reader");
//        medLinkBle.registerRadioResponseCountNotification(this::newDataIsAvailable);

//        aapsLogger.debug("RFSpy radio registered");
//        reader.start();
    }


    // Here should go generic RL initialisation + protocol adjustments depending on
    // firmware version
    public void initializeRileyLink() {
//        if(bleVersion==null) {
//            bleVersion = getVersion();
//        }
        if(medLinkServiceData.firmwareVersion ==null) {
            medLinkServiceData.firmwareVersion = RileyLinkFirmwareVersion.Version_4_x;
        }

        //getFirmwareVersion();
    }


    // Call this from the "response count" notification handler.
    private void newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        aapsLogger.debug("Nes Data is available");
        reader.newDataIsAvailable();
    }


    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    public String getVersion() {
        BLECommOperationResult result = medLinkBle.readCharacteristic_blocking(radioServiceUUID, radioDataUUID);
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            String version = ByteUtil.shortHexString(result.value);
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE Version: " + version);
            return version;
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "getVersion failed with code: " + result.resultCode);
            return "(null)";
        }
    }

//    public boolean isRileyLinkStillAvailable() {
//        RileyLinkFirmwareVersion firmwareVersion = getFirmwareVersion();
//
//        return (firmwareVersion != RileyLinkFirmwareVersion.UnknownVersion);
//    }


//    private RileyLinkFirmwareVersion getFirmwareVersion() {
//
//        aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version. Get Version - Start");
//
//        for (int i = 0; i < 5; i++) {
//            // We have to call raw version of communication to get firmware version
//            // So that we can adjust other commands accordingly afterwords
//
//            byte[] getVersionRaw = getByteArray(RileyLinkCommandType.GetVersion.code);
//            byte[] response = writeToDataRaw(getVersionRaw, 5000);
//
//            aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version. GetVersion [response={}]", ByteUtil.shortHexString(response));
//
//            if (response != null) { // && response[0] == (byte) 0xDD) {
//
//                String versionString = StringUtil.fromBytes(response);
//
//                RileyLinkFirmwareVersion version = RileyLinkFirmwareVersion.getByVersionString(StringUtil
//                        .fromBytes(response));
//
//                aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version string: {}, resolved to {}.", versionString, version);
//
//                if (version != RileyLinkFirmwareVersion.UnknownVersion)
//                    return version;
//
//                SystemClock.sleep(1000);
//            }
//        }
//
//        aapsLogger.error(LTag.PUMPBTCOMM, "Firmware Version can't be determined. Checking with BLE Version [{}].", bleVersion);
//
//        if (bleVersion.contains(" 2.")) {
//            return RileyLinkFirmwareVersion.Version_2_0;
//        }
//
//        return RileyLinkFirmwareVersion.UnknownVersion;
//    }


    private void writeToDataRaw(MedLinkPumpMessage msg, int responseTimeout_ms) {
        SystemClock.sleep(100);
        // FIXME drain read queue?
        aapsLogger.debug("writeToDataRaw " + msg.getCommandType().code);
        byte[] junkInBuffer = reader.poll(0);

        while (junkInBuffer != null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, ThreadUtil.sig() + "writeToData: draining read queue, found this: "
                    + ByteUtil.shortHexString(junkInBuffer));
            junkInBuffer = reader.poll(0);
        }

        // prepend length, and send it.
//        byte[] prepended = ByteUtil.concat(new byte[]{(byte) (bytes.length)}, bytes);

//        aapsLogger.debug(LTag.PUMPBTCOMM, "writeToData (raw={})", ByteUtil.shortHexString(prepended));

        medLinkBle.writeCharacteristic_blocking(radioServiceUUID, radioDataUUID,
                msg);
//        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
//            aapsLogger.error(LTag.PUMPBTCOMM, "BLE Write operation failed, code=" + writeCheck.resultCode);
//            return null; // will be a null (invalid) response
//        }
//        SystemClock.sleep(100);
        // Log.i(TAG,ThreadUtil.sig()+String.format(" writeToData:(timeout %d) %s",(responseTimeout_ms),ByteUtil.shortHexString(prepended)));
//        byte[] rawResponse = reader.poll(responseTimeout_ms);
//        return rawResponse;

    }


    // The caller has to know how long the RFSpy will be busy with what was sent to it.
    private <B> void writeToData(MedLinkPumpMessage<B> msg, int responseTimeout_ms) {

        Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> resultActivity = msg.getBaseCallback();

        Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> andThen = resultActivity.andThen(f -> {
            Supplier<Stream<String>> answer = () -> f.getAnswer();
            if (answer == null || answer.get().findFirst().isPresent()) {
                aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: No response from RileyLink");
                notConnectedCount++;
            } else {
                String[] answers = answer.get().toArray(String[]::new);

                if (!answers[answers.length-1].equals("eomeomeom") ||
                        !answers[answers.length-1].equals("ready")) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink was interrupted");
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "writeToData: RileyLink reports OK");
                    resetNotConnectedCount();
                }
            }
            return f;
        });
        msg.setBaseCallback(andThen);
        writeToDataRaw(msg, responseTimeout_ms);

//        RFSpyResponse resp = new RFSpyResponse(msg.getCommandData(), rawResponse);

//        return resp;
    }


    private void resetNotConnectedCount() {
        this.notConnectedCount = 0;
    }


    private byte[] getByteArray(byte... input) {
        return input;
    }


    private byte[] getCommandArray(RileyLinkCommandType command, byte[] body) {
        int bodyLength = body == null ? 0 : body.length;

        byte[] output = new byte[bodyLength + 1];

        output[0] = command.code;

        if (body != null) {
            for (int i = 0; i < body.length; i++) {
                output[i + 1] = body[i];
            }
        }

        return output;
    }


    public RFSpyResponse transmitThenReceive(MedLinkPumpMessage msg, byte sendChannel, byte repeatCount, byte delay_ms,
                                             byte listenChannel, int timeout_ms, byte retryCount) {
        return transmitThenReceive(msg, sendChannel, repeatCount, delay_ms, listenChannel, timeout_ms, retryCount);
    }

//
//    public RFSpyResponse transmitThenReceive(RadioPacket pkt, int timeout_ms) {
//        return transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, timeout_ms, (byte) 0);
//    }


    public void transmitThenReceive(MedLinkPumpMessage pumpMessage) {
        medLinkBle.writeCharacteristic_blocking(radioServiceUUID, radioDataUUID, pumpMessage);
    }

    private RFSpyResponse updateRegister(CC111XRegister reg, int val) {
//        BaseResultActivity resultActivity = null;
        RFSpyResponse resp = null;
//        writeToData(new UpdateRegister(reg, (byte) val), EXPECTED_MAX_BLUETOOTH_LATENCY_MS, resultActivity);
        return resp;
    }

    public void setBaseFrequency(double freqMHz) {
        int value = (int) (freqMHz * 1000000 / ((double) (RILEYLINK_FREQ_XTAL) / Math.pow(2.0, 16.0)));
//        updateRegister(CC111XRegister.freq0, (byte) (value & 0xff));
//        updateRegister(CC111XRegister.freq1, (byte) ((value >> 8) & 0xff));
//        updateRegister(CC111XRegister.freq2, (byte) ((value >> 16) & 0xff));
        aapsLogger.info(LTag.PUMPBTCOMM, "Set frequency to {} MHz", freqMHz);

        this.currentFrequencyMHz = freqMHz;

        configureRadioForRegion(medLinkServiceData.rileyLinkTargetFrequency);
    }

    private void configureRadioForRegion(RileyLinkTargetFrequency frequency) {
        // we update registers only on first run, or if region changed
        aapsLogger.error(LTag.PUMPBTCOMM, "RileyLinkTargetFrequency: " + frequency);
        switch (frequency) {
            case Medtronic_WorldWide: {
                // updateRegister(CC111X_MDMCFG4, (byte) 0x59);
                setRXFilterMode(RXFilterMode.Wide);
                // updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                // updateRegister(CC111X_MDMCFG2, (byte) 0x33);
//                updateRegister(CC111XRegister.mdmcfg1, 0x62);
//                updateRegister(CC111XRegister.mdmcfg0, 0x1A);
//                updateRegister(CC111XRegister.deviatn, 0x13);
                setMedtronicEncoding();
            }
            break;

            case Medtronic_US: {
                // updateRegister(CC111X_MDMCFG4, (byte) 0x99);
                setRXFilterMode(RXFilterMode.Narrow);
                // updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                // updateRegister(CC111X_MDMCFG2, (byte) 0x33);
//                updateRegister(CC111XRegister.mdmcfg1, 0x61);
//                updateRegister(CC111XRegister.mdmcfg0, 0x7E);
//                updateRegister(CC111XRegister.deviatn, 0x15);
                setMedtronicEncoding();
            }
            break;
            default:
                aapsLogger.warn(LTag.PUMPBTCOMM, "No region configuration for RfSpy and {}", frequency.name());
                break;

        }
    }


    private void setMedtronicEncoding() {
        MedLinkEncodingType encoding = MedLinkEncodingType.FourByteSixByteLocal;
//        if (RileyLinkFirmwareVersion.isSameVersion(medLinkServiceData.firmwareVersion, RileyLinkFirmwareVersion.Version2AndHigher)) {
//            if (sp.getString(RileyLinkConst.Prefs.Encoding, "None")
//                    .equals(resourceHelper.gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink))) {
//                encoding = RileyLinkEncodingType.FourByteSixByteRileyLink;
//            }
//        }
        setRileyLinkEncoding(encoding);
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set Encoding for Medtronic: " + encoding.name());
    }


    private RFSpyResponse setPreamble(int preamble) {
        RFSpyResponse resp = null;
        try {
            BaseCallback resultActivity= null;
//            resp = writeToData(new SetPreamble(injector, preamble), EXPECTED_MAX_BLUETOOTH_LATENCY_MS, resultActivity);
        } catch (Exception e) {
            aapsLogger.error("Failed to set preamble", e);
        }
        return resp;
    }

    public RFSpyResponse setRileyLinkEncoding(MedLinkEncodingType encoding) {
        aapsLogger.debug("MedlinkRFSpy encoding call");
//        RFSpyResponse resp = writeToData(new SetHardwareEncoding(encoding), EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
//
//        if (resp.isOK()) {
//            reader.setMedLinkEncodingType(encoding);
//            medLinkUtil.setEncoding(encoding);
//        }
        return null;
    }


    private void setRXFilterMode(RXFilterMode mode) {
        byte drate_e = (byte) 0x9; // exponent of symbol rate (16kbps)
        byte chanbw = mode.value;
        updateRegister(CC111XRegister.mdmcfg4, (byte) (chanbw | drate_e));
    }

    /**
     * Reset RileyLink Configuration (set all updateRegisters)
     */
    public void resetRileyLinkConfiguration() {
        if (this.currentFrequencyMHz != null)
            this.setBaseFrequency(this.currentFrequencyMHz);
    }


    public void stopReader() {
        reader.stop();
    }

    public void initializeMedLink() {

    }
}
