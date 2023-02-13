package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RFSpyResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.CC111XRegister
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RXFilterMode
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Dirceu on 06/10/20.
 * copied from @class{RFSpy}
 */
@Singleton
class MedLinkRFSpy @Inject constructor(private val injector: HasAndroidInjector, private val medLinkBle: MedLinkBLE) {

     @Inject
    lateinit var aapsLogger: AAPSLogger

    @JvmField @Inject
    var resourceHelper: ResourceHelper? = null

    @JvmField @Inject
    var sp: SP? = null

    @JvmField @Inject
    var medLinkServiceData: MedLinkServiceData? = null

    @JvmField @Inject
    var medLinkUtil: MedLinkUtil? = null
    @JvmField var notConnectedCount = 0
    private var reader: MedLinkRFSpyReader? = null
    private val radioServiceUUID = UUID.fromString(GattAttributes.SERVICE_UUID)
    private val radioDataUUID = UUID.fromString(GattAttributes.GATT_UUID)
    private val radioVersionUUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION)

    //private UUID responseCountUUID = UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT);
    val rLVersionCached: RileyLinkFirmwareVersion? = null
    val bLEVersionCached // We don't use it so no need of sofisticated logic
        : String? = null
    private var currentFrequencyMHz: Double? = null
    @Inject fun onInit() {
        aapsLogger.debug("RileyLinkServiceData:$medLinkServiceData")
        reader = MedLinkRFSpyReader(aapsLogger, medLinkBle)
    }

    // Call this after the RL services are discovered.
    // Starts an async task to read when data is available
    fun startReader() {
       aapsLogger.debug("RFSpy start reader");
       medLinkBle.registerRadioResponseCountNotification(this::newDataIsAvailable);

       aapsLogger.debug("RFSpy radio registered");
       // reader?.start();
    }

    // Here should go generic RL initialisation + protocol adjustments depending on
    // firmware version


    // Call this from the "response count" notification handler.
    private fun newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        aapsLogger!!.debug("Nes Data is available")
        // reader!!.newDataIsAvailable()
    }

    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    val version: String
        get() {
            val result = medLinkBle.readCharacteristicBlocking(radioServiceUUID, radioDataUUID)
            return if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
                val version = ByteUtil.shortHexString(result.value)
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "BLE Version: $version")
                version
            } else {
                aapsLogger!!.error(LTag.PUMPBTCOMM, "getVersion failed with code: " + result.resultCode)
                "(null)"
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
//     private fun writeToDataRaw(msg: MedLinkPumpMessage<*>, responseTimeout_ms: Int) {
//         SystemClock.sleep(100)
//         // FIXME drain read queue?
//         aapsLogger!!.debug("writeToDataRaw " + msg.firstCommand().code)
//         var junkInBuffer = reader!!.poll(0)
//         while (junkInBuffer != null) {
//             aapsLogger!!.warn(
//                 LTag.PUMPBTCOMM, ThreadUtil.sig() + "writeToData: draining read queue, found this: "
//                     + ByteUtil.shortHexString(junkInBuffer)
//             )
//             junkInBuffer = reader!!.poll(0)
//         }
//
//         // prepend length, and send it.
// //        byte[] prepended = ByteUtil.concat(new byte[]{(byte) (bytes.length)}, bytes);
//
// //        aapsLogger.debug(LTag.PUMPBTCOMM, "writeToData (raw={})", ByteUtil.shortHexString(prepended));
//         medLinkBle.addWriteCharacteristic<Any>(
//             radioServiceUUID, radioDataUUID,
//             msg
//         )
        //        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
//            aapsLogger.error(LTag.PUMPBTCOMM, "BLE Write operation failed, code=" + writeCheck.resultCode);
//            return null; // will be a null (invalid) response
//        }
//        SystemClock.sleep(100);
        // Log.i(TAG,ThreadUtil.sig()+String.format(" writeToData:(timeout %d) %s",(responseTimeout_ms),ByteUtil.shortHexString(prepended)));
//        byte[] rawResponse = reader.poll(responseTimeout_ms);
//        return rawResponse;
//     }

    // The caller has to know how long the RFSpy will be busy with what was sent to it.
//     private fun <B> writeToData(msg: MedLinkPumpMessage<B>, responseTimeout_ms: Int) {
//         val resultActivity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> = msg.getBaseCallback()
//         val andThen = resultActivity.andThen { f: MedLinkStandardReturn<B> ->
//             val answer = Supplier { f.getAnswer() }
//             if (answer == null || answer.get().findFirst().isPresent) {
//                 aapsLogger!!.error(LTag.PUMPBTCOMM, "writeToData: No response from RileyLink")
//                 notConnectedCount++
//             } else {
//                 val answers = answer.get().toArray<String> { _Dummy_.__Array__() }
//                 if (answers[answers.size - 1] != "eomeomeom" ||
//                     answers[answers.size - 1] != "ready"
//                 ) {
//                     aapsLogger!!.error(LTag.PUMPBTCOMM, "writeToData: RileyLink was interrupted")
//                 } else {
//                     aapsLogger!!.warn(LTag.PUMPBTCOMM, "writeToData: RileyLink reports OK")
//                     resetNotConnectedCount()
//                 }
//             }
//             f
//         }
//         msg.setBaseCallback(andThen)
//         writeToDataRaw(msg, responseTimeout_ms)
//
// //        RFSpyResponse resp = new RFSpyResponse(msg.getCommandData(), rawResponse);
//
// //        return resp;
//     }

    private fun resetNotConnectedCount() {
        notConnectedCount = 0
    }

    private fun getByteArray(vararg input: Byte): ByteArray {
        return input
    }

    private fun getCommandArray(command: RileyLinkCommandType, body: ByteArray?): ByteArray {
        val bodyLength = body?.size ?: 0
        val output = ByteArray(bodyLength + 1)
        output[0] = command.code
        if (body != null) {
            for (i in body.indices) {
                output[i + 1] = body[i]
            }
        }
        return output
    }

    fun transmitThenReceive(
        msg: MedLinkPumpMessage<*,*>?, sendChannel: Byte, repeatCount: Byte, delay_ms: Byte,
        listenChannel: Byte, timeout_ms: Int, retryCount: Byte
    ): RFSpyResponse {
        return transmitThenReceive(msg, sendChannel, repeatCount, delay_ms, listenChannel, timeout_ms, retryCount)
    }

    //
    //    public RFSpyResponse transmitThenReceive(RadioPacket pkt, int timeout_ms) {
    //        return transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, timeout_ms, (byte) 0);
    //    }
    fun <B,C>transmitThenReceive(pumpMessage: MedLinkPumpMessage<B,C>) {
        medLinkBle.addWriteCharacteristic(radioServiceUUID, radioDataUUID, pumpMessage)
    }

    private fun updateRegister(reg: CC111XRegister, `val`: Int): RFSpyResponse? {
//        BaseResultActivity resultActivity = null;
        //        writeToData(new UpdateRegister(reg, (byte) val), EXPECTED_MAX_BLUETOOTH_LATENCY_MS, resultActivity);
        return null as RFSpyResponse?
    }

    fun setBaseFrequency(freqMHz: Double) {
        val value = (freqMHz * 1000000 / (RILEYLINK_FREQ_XTAL.toDouble() / Math.pow(2.0, 16.0))).toInt()
        //        updateRegister(CC111XRegister.freq0, (byte) (value & 0xff));
//        updateRegister(CC111XRegister.freq1, (byte) ((value >> 8) & 0xff));
//        updateRegister(CC111XRegister.freq2, (byte) ((value >> 16) & 0xff));
        aapsLogger!!.info(LTag.PUMPBTCOMM, "Set frequency to {} MHz", freqMHz)
        currentFrequencyMHz = freqMHz
        configureRadioForRegion(medLinkServiceData!!.rileyLinkTargetFrequency)
    }

    private fun configureRadioForRegion(frequency: RileyLinkTargetFrequency) {
        // we update registers only on first run, or if region changed
        aapsLogger!!.error(LTag.PUMPBTCOMM, "RileyLinkTargetFrequency: $frequency")
        when (frequency) {
            RileyLinkTargetFrequency.MedtronicWorldWide -> {

                // updateRegister(CC111X_MDMCFG4, (byte) 0x59);
                setRXFilterMode(RXFilterMode.Wide)
                // updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                // updateRegister(CC111X_MDMCFG2, (byte) 0x33);
//                updateRegister(CC111XRegister.mdmcfg1, 0x62);
//                updateRegister(CC111XRegister.mdmcfg0, 0x1A);
//                updateRegister(CC111XRegister.deviatn, 0x13);
                setMedtronicEncoding()
            }

            RileyLinkTargetFrequency.MedtronicUS        -> {

                // updateRegister(CC111X_MDMCFG4, (byte) 0x99);
                setRXFilterMode(RXFilterMode.Narrow)
                // updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                // updateRegister(CC111X_MDMCFG2, (byte) 0x33);
//                updateRegister(CC111XRegister.mdmcfg1, 0x61);
//                updateRegister(CC111XRegister.mdmcfg0, 0x7E);
//                updateRegister(CC111XRegister.deviatn, 0x15);
                setMedtronicEncoding()
            }

            else                                         -> aapsLogger!!.warn(LTag.PUMPBTCOMM, "No region configuration for RfSpy and {}", frequency.name)
        }
    }

    private fun setMedtronicEncoding() {
        val encoding = MedLinkEncodingType.FourByteSixByteLocal
        //        if (RileyLinkFirmwareVersion.isSameVersion(medLinkServiceData.firmwareVersion, RileyLinkFirmwareVersion.Version2AndHigher)) {
//            if (sp.getString(RileyLinkConst.Prefs.Encoding, "None")
//                    .equals(resourceHelper.gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink))) {
//                encoding = RileyLinkEncodingType.FourByteSixByteRileyLink;
//            }
//        }
        setRileyLinkEncoding(encoding)
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set Encoding for Medtronic: " + encoding.name)
    }

    private fun setPreamble(preamble: Int): RFSpyResponse? {
        val resp: RFSpyResponse? = null
        try {
            val resultActivity: BaseCallback<*, *>? = null
            //            resp = writeToData(new SetPreamble(injector, preamble), EXPECTED_MAX_BLUETOOTH_LATENCY_MS, resultActivity);
        } catch (e: Exception) {
            aapsLogger!!.error("Failed to set preamble", e)
        }
        return resp
    }

    fun setRileyLinkEncoding(encoding: MedLinkEncodingType?): RFSpyResponse? {
        aapsLogger!!.debug("MedlinkRFSpy encoding call")
        //        RFSpyResponse resp = writeToData(new SetHardwareEncoding(encoding), EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
//
//        if (resp.isOK()) {
//            reader.setMedLinkEncodingType(encoding);
//            medLinkUtil.setEncoding(encoding);
//        }
        return null
    }

    private fun setRXFilterMode(mode: RXFilterMode) {
        val drate_e = 0x9.toByte() // exponent of symbol rate (16kbps)
        val chanbw = mode.value
        // updateRegister(CC111XRegister.mdmcfg4, (chanbw or drate_e) as Byte.toInt())
    }

    /**
     * Reset RileyLink Configuration (set all updateRegisters)
     */
    fun resetRileyLinkConfiguration() {
        if (currentFrequencyMHz != null) setBaseFrequency(currentFrequencyMHz!!)
    }

    fun stopReader() {
        reader!!.stop()
    }

    fun initializeMedLink() {
        if (medLinkServiceData!!.firmwareVersion == null) {
            medLinkServiceData!!.firmwareVersion = RileyLinkFirmwareVersion.Version_4_x
        }
    }

    companion object {

        private const val RILEYLINK_FREQ_XTAL: Long = 24000000
        private const val EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 7500 // 1500
    }
}