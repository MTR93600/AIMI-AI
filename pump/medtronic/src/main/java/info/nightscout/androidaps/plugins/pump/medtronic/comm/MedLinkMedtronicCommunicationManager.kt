package info.nightscout.androidaps.plugins.pump.medtronic.comm

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.ConnectionCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandPriority
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommandReader
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleConnectCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.StatusCallback
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PumpMessage
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.core.utils.DateTimeUtil
import info.nightscout.pump.common.data.MedLinkPumpStatus
import info.nightscout.pump.common.data.PumpStatus
import info.nightscout.pump.core.defs.PumpDeviceState
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import org.joda.time.LocalDateTime
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Dirceu on 17/09/20.
 * copied from [MedtronicCommunicationManager]
 */
@Singleton
class MedLinkMedtronicCommunicationManager @Inject constructor(
    injector: HasAndroidInjector,
    rfSpy: MedLinkRFSpy,
    aapsLogger: AAPSLogger,
    medLinkServiceData: MedLinkServiceData,
) :
    MedLinkCommunicationManager
        (injector, rfSpy, aapsLogger, medLinkServiceData) {

    var medtronicPumpStatus: MedLinkMedtronicPumpStatus? = null
        @Inject set

    var medLinkPumpPlugin: MedLinkMedtronicPumpPlugin? = null
        @Inject set

    var medtronicConverter: MedtronicConverter? = null
        @Inject set

    var medLinkMedtronicUtil: MedLinkMedtronicUtil? = null
        @Inject set

    var context: Context? = null
        @Inject set

    var resourceHelper: ResourceHelper? = null
        @Inject set

    private val MAX_COMMAND_TRIES = 3
    private val DEFAULT_TIMEOUT = 2000
    private val RILEYLINK_TIMEOUT = (15 * 60 * 1000 // 15 min
        ).toLong()

    private val errorMessage: String? = null
    private val debugSetCommands = false
    private var doWakeUpBeforeCommand = true
    var profile: BasalProfile? = null
    @Inject fun onInit() {
        // we can't do this in the constructor, as sp only gets injected after the constructor has returned
        medtronicPumpStatus!!.previousConnection = sp?.getLong(
            RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L
        )!!
    }

    override fun createResponseMessage(payload: ByteArray): RLMessage {
        return PumpMessage(medLinkPumpPlugin!!.aapsLogger, payload)
    }


    override fun setPumpDeviceState(pumpDeviceState: PumpDeviceState) {
        medtronicPumpStatus!!.pumpDeviceState = pumpDeviceState
    }

    override fun setDoWakeUpBeforeCommand(doWakeUp: Boolean) {
        doWakeUpBeforeCommand = doWakeUp
    }

    /**
     * We do actual wakeUp and compare PumpModel with currently selected one. If returned model is
     * not Unknown, pump is reachable.
     *
     * @return
     */
    fun isDeviceReachable(canPreventTuneUp: Boolean): Boolean {
        val state = medtronicPumpStatus!!.pumpDeviceState
        if (state !== PumpDeviceState.PumpUnreachable) medtronicPumpStatus!!.pumpDeviceState = PumpDeviceState.WakingUp

//        for (int retry = 0; retry < 5; retry++) {
        aapsLogger.debug(LTag.PUMPCOMM, "isDeviceReachable. Waking pump... first shot")

//                .andThen(f -> {

//            Stream<String> answer = f.getAnswer();
//            aapsLogger.info(LTag.PUMPBTCOMM,"BaseResultActivity");
//            aapsLogger.info(LTag.PUMPBTCOMM,answer.toString());
//            String[] messages =  answer.toArray(String[]::new);
//            if (f.getAnswer().anyMatch(m -> m.contains("eomeomeom"))) {
//                aapsLogger.debug("eomomom");
//                medLinkPumpPlugin.setLastCommunicationToNow();
//                PumpStatus pumpStatus = medLinkPumpPlugin.getPumpStatusData();
//                MedLinkStatusParser.parseStatus(messages, pumpStatus);
//                aapsLogger.debug("Pumpstatus");
//                aapsLogger.debug(pumpStatus.toString());
//                medLinkPumpPlugin.postInit();
////                medLinkPumpPlugin.getPumpStatus();
//
//
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
//                medtronicPumpStatus.batteryRemaining = pumpStatus.batteryRemaining;
//                medtronicPumpStatus.reservoirRemainingUnits = pumpStatus.reservoirRemainingUnits;
//                medtronicPumpStatus.lastBolusAmount = pumpStatus.lastBolusAmount;
//                medtronicPumpStatus.lastBolusTime = pumpStatus.lastBolusTime;
//                medtronicPumpStatus.activeProfileName = pumpStatus.activeProfileName;
//                medtronicPumpStatus.currentBasal = pumpStatus.currentBasal;
//                medtronicPumpStatus.dailyTotalUnits = pumpStatus.dailyTotalUnits;
//                medtronicPumpStatus.lastDataTime = pumpStatus.lastDataTime;
//                medtronicPumpStatus.tempBasalRatio = pumpStatus.tempBasalRatio;
//                medtronicPumpStatus.tempBasalInProgress = pumpStatus.tempBasalInProgress;
//                medtronicPumpStatus.tempBasalRemainMin = pumpStatus.tempBasalRemainMin;
//                medtronicPumpStatus.tempBasalStart = pumpStatus.tempBasalStart;
//                aapsLogger.info(LTag.PUMPBTCOMM, "status " + pumpStatus.currentBasal);
//                aapsLogger.info(LTag.PUMPBTCOMM, "status " + medtronicPumpStatus.currentBasal);
//                medtronicPumpStatus.setLastCommunicationToNow();
//            } else if (messages[messages.length - 1].toLowerCase().equals("ready")) {
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
//            } else {
//                aapsLogger.debug("Apply last message" + messages[messages.length - 1]);
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
//                errorMessage = PumpDeviceState.PumpUnreachable.name();
//                f.addError(MedlinkStandardReturn.ParsingError.Unreachable);
//            }

//            return f;

//        });
        return connectToDevice()

//        if (connected)
//            return true;
//
//            SystemClock.sleep(1000);

//        }

//        if (state != PumpDeviceState.PumpUnreachable)
//            medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
//
//        if (!canPreventTuneUp) {
//
//            long diff = System.currentTimeMillis() - medtronicPumpStatus.lastConnection;
//
//            if (diff > RILEYLINK_TIMEOUT) {
//                serviceTaskExecutor.startTask(new WakeAndTuneTask(injector));
//            }
//        }
//
//        return false;
    }

    private fun connectToDevice(): Boolean {
        val state = medtronicPumpStatus!!.pumpDeviceState

        // check connection

//        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
        aapsLogger.info(LTag.PUMPBTCOMM, "connect to device")
        //        getStatusData();

//        aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(rfSpyResponse.getRaw()));

//        if (rfSpyResponse.wasTimeout()) {
//            aapsLogger.error(LTag.PUMPCOMM, "isDeviceReachable. Failed to find pump (timeout).");
//        } else if (rfSpyResponse.looksLikeRadioPacket()) {
//            RadioResponse radioResponse = new RadioResponse(injector);
//
//            try {
//
//                radioResponse.init(rfSpyResponse.getRaw());
//
//                if (radioResponse.isValid()) {
//
//                    PumpMessage pumpResponse = (PumpMessage) createResponseMessage(radioResponse.getPayload());
//
//                    if (!pumpResponse.isValid()) {
//                        aapsLogger.warn(LTag.PUMPCOMM, "Response is invalid ! [interrupted={}, timeout={}]", rfSpyResponse.wasInterrupted(),
//                                rfSpyResponse.wasTimeout());
//                    } else {
//
//                        // radioResponse.rssi;
//                        Object dataResponse = medtronicConverter.convertResponse(medLinkPumpPlugin.getPumpDescription().pumpType, MedtronicCommandType.PumpModel,
//                                pumpResponse.getRawContent());
//
//                        MedLinkMedtronicDeviceType pumpModel = (MedLinkMedtronicDeviceType) dataResponse;
//                        boolean valid = (pumpModel != MedLinkMedtronicDeviceType.Unknown_Device);
//
//                        if (medLinkMedtronicUtil.getMedtronicPumpModel() == null && valid) {
//                            medLinkMedtronicUtil.setMedtronicPumpModel(pumpModel);
//                        }
//
//                        aapsLogger.debug(LTag.PUMPCOMM, "isDeviceReachable. PumpModel is {} - Valid: {} (rssi={})", pumpModel.name(), valid,
//                                radioResponse.rssi);
//
//                        if (valid) {
//                            if (state == PumpDeviceState.PumpUnreachable)
//                                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.WakingUp);
//                            else
//                                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//
//                            rememberLastGoodDeviceCommunicationTime();
//
//                            return true;
//
//                        } else {
//                            if (state != PumpDeviceState.PumpUnreachable)
//                                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
//                        }
//
//                    }
//
//                } else {
//                    aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Failed to parse radio response: "
//                            + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
//                }
//
//            } catch (RileyLinkCommunicationException e) {
//                aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Failed to decode radio response: "
//                        + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
//            }
//
//        } else {
//            aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Unknown response: " + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
//        }
        return false
    }

    fun <B, C> getStatusData(pumpMessage: MedLinkPumpMessage<B, C>) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Getting status data")
        rfspy?.transmitThenReceive(pumpMessage)
    }

    override fun tryToConnectToDevice(): Boolean {
        return isDeviceReachable(true)
    }

    //    private void runCommandWithArgs(MedLinkCommandType command, MedLinkCommandType args,
    //                                    Function resultActivity, Function argResultActivity) throws RileyLinkCommunicationException {
    //
    //        Function activity;
    //        Function argActivity;
    //        if (argResultActivity != null) {
    //            argActivity = addPostProcessCommand(argResultActivity);
    //            activity = resultActivity;
    //
    //        } else {
    //            activity = addPostProcessCommand(resultActivity);
    //            argActivity = argResultActivity;
    //        }
    //
    //        rfspy.transmitThenReceive(makePumpMessage(command, args, activity,
    //                argActivity, medLinkPumpPlugin.getBtSleepTime()));
    //        //TODO avaliar este retorno
    //
    //    }
    @Throws(RileyLinkCommunicationException::class) private fun <B, C> runCommandWithArgs(msg: MedLinkPumpMessage<B, C>) {
        if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: ")

        // look for ack from short message
        val shortResponse = sendAndListen(msg)
        aapsLogger.debug(
            LTag.PUMPCOMM, "Short response: " + shortResponse.toString() + " to " +
                "command: " + msg.firstCommand()
        )
    }

    override fun createPumpMessageContent(type: RLMessageType): ByteArray {
        return when (type) {
            RLMessageType.PowerOn        -> medLinkMedtronicUtil!!.buildCommandPayload(
                medLinkServiceData,
                MedLinkMedtronicCommandType.RFPowerOn,
                byteArrayOf(2, 1, receiverDeviceAwakeForMinutes.toByte())
            ) // maybe this is better FIXME
            RLMessageType.ReadSimpleData -> //                return medLinkMedtronicUtil.buildCommandPayload(medLinkServiceData, MedLinkMedtronicCommandType.RFPowerOn.PumpModel, null);
                MedLinkCommandType.GetState.getRaw()
        }
        return ByteArray(0)
    }

    //    private MedLinkPumpMessage makePumpMessage(MedLinkMedtronicCommandType messageType, byte[] body) {
    //        return makePumpMessage(messageType, body == null ? new CarelinkShortMessageBody()
    //                : new CarelinkShortMessageBody(body));
    //    }
    //    private MedLinkPumpMessage makePumpMessage(MedLinkMedtronicCommandType messageType) {
    ////        if(messageType == MedLinkMedtronicCommandType.GetBasalProfileSTD || messageType == MedLinkMedtronicCommandType.GetBasalProfileA || messageType == MedLinkMedtronicCommandType.GetBasalProfileB){
    ////            MedLinkMessage
    ////        }
    //        return makePumpMessage(messageType, (byte[]) null);
    //    }
    //    private <B> MedLinkPumpMessage<B> makePumpMessage(MedLinkCommandType messageType,
    //                                                      MedLinkCommandType argument,
    //                                                      Function<Supplier<Stream<String>>,
    //                                                              MedLinkStandardReturn<B>> baseResultActivity,
    //                                                      long btSleepSize) {
    //
    //        Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> activity = baseResultActivity.andThen(f -> {
    //            if (f.getErrors().isEmpty()) {
    //                medLinkMedtronicUtil.setCurrentCommand(null);
    //            }
    //            return f;
    //        });
    //        return new MedLinkPumpMessage<B>(messageType, argument, activity,
    //                btSleepSize);
    //    }
    private fun <B> makePumpMessage(
        messageType: MedLinkCommandType,
        argument: MedLinkCommandType,
        baseResultActivity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        btSleepSize: Long, bleCommand: BleCommand,
    ): MedLinkPumpMessage<B, Any> {
        return MedLinkPumpMessage(
            messageType, argument, baseResultActivity,

            btSleepSize, bleCommand,
            CommandPriority.NORMAL
        )
    }
    //    private MedLinkPumpMessage sendAndGetResponse(MedLinkCommandType commandType) throws RileyLinkCommunicationException {
    //
    //        return sendAndGetResponse(commandType, null, DEFAULT_TIMEOUT);
    //    }
    /**
     * Main wrapper method for sending data - (for getting responses)
     *
     * @return
     */
    @Throws(RileyLinkCommunicationException::class) private fun <B> sendAndGetResponse(msg: MedLinkPumpMessage<B, Any>) {
        // wakeUp
        if (doWakeUpBeforeCommand) wakeUp(receiverDeviceAwakeForMinutes, false)
        medtronicPumpStatus!!.pumpDeviceState = PumpDeviceState.Active

        // create message

        // send and wait for response
        sendAndListen(msg)
        medtronicPumpStatus!!.pumpDeviceState = PumpDeviceState.Sleeping

    }

    @Throws(RileyLinkCommunicationException::class) private fun <B, C> sendAndListen(msg: MedLinkPumpMessage<B, C>) {
        sendAndListen(msg, 4000) // 2000
    }

    // All pump communications go through this function.
    @Throws(RileyLinkCommunicationException::class) override fun <B, C> sendAndListen(msg: MedLinkPumpMessage<B, C>, timeout_ms: Int) {
        super.sendAndListen(msg, timeout_ms)
    }

    //    private Object sendAndGetResponseWithCheck(MedLinkCommandType commandType, BaseCallback baseCallback) {
    //
    //        return sendAndGetResponseWithCheck(commandType, null, baseCallback);
    //    }
    private fun <B, C> sendAndGetResponseWithCheck(
        commandType: MedLinkCommandType,
        argument: MedLinkCommandType,
        baseResultActivity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
        btSleepTime: Long,
        bleCommand: BleCommand,
        commandPriority: CommandPriority = CommandPriority.NORMAL
    ): Any? {
        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: {}", commandType)
        for (retries in 0 until MAX_COMMAND_TRIES) {
            try {
                val pumpMessage = makePumpMessage(
                    commandType, argument,
                    baseResultActivity, btSleepTime, bleCommand
                )
                //TODO look were to pu the medlink command result, here maybe a good place to
                sendAndGetResponse(pumpMessage)

//                String check = checkResponseContent(response, commandType.getCommandDescription(), 0);// commandType.expectedLength);
//
//                if (check == null) {
//
//                    Object dataResponse = medtronicConverter.convertResponse(medtronicPumpPlugin.getPumpDescription().pumpType, MedtronicCommandType.valueOf(commandType.command), response.getRawContent());
//
//                    if (dataResponse != null) {
//                        this.errorMessage = null;
//                        aapsLogger.debug(LTag.PUMPCOMM, "Converted response for {} is {}.", commandType.name(), dataResponse);
//
//                        return dataResponse;
//                    } else {
//                        this.errorMessage = "Error decoding response.";
//                    }
//                } else {
//                    this.errorMessage = check;
//                    // return null;
//                }
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.message, retries + 1)
            }
        }
        return null
    }

    //    private String checkResponseContent(MedLinkPumpMessage response, String method, int expectedLength) {
    //
    //        if (!response.isValid()) {
    //            String responseData = String.format("%s: Invalid response.", method);
    //            aapsLogger.warn(LTag.PUMPCOMM, responseData);
    //            return responseData;
    //        }
    //
    //        byte[] contents = response.getRawContent();
    //
    //        if (contents != null) {
    //            if (contents.length >= expectedLength) {
    //                aapsLogger.debug(LTag.PUMPCOMM, "{}: Content: {}", method, ByteUtil.shortHexString(contents));
    //                return null;
    //
    //            } else {
    //                String responseData = String.format(
    //                        "%s: Cannot return data. Data is too short [expected=%s, received=%s].", method, ""
    //                                + expectedLength, "" + contents.length);
    //
    //                aapsLogger.warn(LTag.PUMPCOMM, responseData);
    //                return responseData;
    //            }
    //        } else {
    //            String responseData = String.format("%s: Cannot return data. Null response.", method);
    //            aapsLogger.warn(LTag.PUMPCOMM, responseData);
    //            return responseData;
    //        }
    //    }
    // PUMP SPECIFIC COMMANDS
    //
    //    public Float getRemainingInsulin() {
    //
    //        Object responseObject = sendAndGetResponseWithCheck(MedLinkMedtronicCommandType.GetRemainingInsulin);
    //
    //        return responseObject == null ? null : (Float) responseObject;
    //    }
    fun setPumpModel() {
//        this.medLinkMedtronicUtil
    }

    fun getPumpModel(activity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>>): MedLinkMedtronicDeviceType? {
        val responseObject = sendAndGetResponseWithCheck<String, Any>(
            MedLinkCommandType.Connect,
            MedLinkCommandType.NoCommand, activity, medLinkPumpPlugin!!.btSleepTime,
            BleConnectCommand(aapsLogger, medLinkServiceData, medLinkPumpPlugin),
            CommandPriority.HIGH
        )
        return if (responseObject == null) null else responseObject as MedLinkMedtronicDeviceType?
    }

    override fun wakeUp(duration_minutes: Int, force: Boolean) {
//        super.wakeUp(duration_minutes, force);
        setPumpDeviceState(PumpDeviceState.WakingUp)

//        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
        rfspy.initializeMedLink()
        //        rfspy.initializeRileyLink();
        aapsLogger.info(LTag.PUMPCOMM, "before wakeup ")
        val resultActivity = ConnectionCallback()
        val activity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> = resultActivity.andThen(Function { s: MedLinkStandardReturn<String> ->
            aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is $s")
            if (s.getAnswer().anyMatch { f -> f.contains("ready") }) {
                lastGoodReceiverCommunicationTime = System.currentTimeMillis()
            }
            if (s.getErrors().isEmpty()) {
                medLinkMedtronicUtil!!.currentCommand = null
                if (medLinkPumpPlugin != null && medLinkPumpPlugin!!.isEnabled() && medLinkPumpPlugin!!.isInitialized()) {
                    medLinkPumpPlugin!!.postInit()
                }
            }
            s
        })
        if (System.currentTimeMillis() - lastGoodReceiverCommunicationTime > 400000) {
            rfspy.transmitThenReceive(
                MedLinkPumpMessage<String, Any>(
                    MedLinkCommandType.BolusStatus,
                    activity,
                    medLinkPumpPlugin!!.btSleepTime,
                    BleCommandReader(aapsLogger, medLinkServiceData, medLinkPumpPlugin),
                    CommandPriority.NORMAL)
            )
        }
        // FIXME wakeUp successful !!!!!!!!!!!!!!!!!!
        //nextWakeUpRequired = System.currentTimeMillis() + (receiverDeviceAwakeForMinutes * 60 * 1000);
    }

    override val pumpStatus: PumpStatus?
        get() = medLinkPumpPlugin!!.pumpStatusData
    override val isDeviceReachable: Boolean
        get() = TODO("Not yet implemented")

//     fun getBasalProfile(
//         function: Function<MedLinkStandardReturn<BasalProfile>, MedLinkStandardReturn<BasalProfile>>?,
//         pumpMessage: MedLinkPumpMessage<Any,Any>?
//     ): BasalProfile? {
//
//         // wakeUp
// //        if (doWakeUpBeforeCommand)
// ////            btSleepTime = ms
// //            wakeUp(receiverDeviceAwakeForMinutes, false);
//         val commandType = MedLinkCommandType.ActiveBasalProfile
//         aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: {}", commandType)
//         medLinkMedtronicUtil!!.currentCommand = commandType
//         medtronicPumpStatus!!.pumpDeviceState = PumpDeviceState.Active
//         val retries = 5
//         //        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {
//         try {
//             // create message
//
// //            BasalCallback baseResultActivity = new BasalCallback(aapsLogger, medLinkPumpPlugin);
//
// //            Function<MedLinkStandardReturn<BasalProfile>, MedLinkStandardReturn<BasalProfile>> func = f -> {
// //                if (pumpMessage.getBaseCallback() instanceof  BasalCallback &&
// //                        ((BasalCallback)pumpMessage.getBaseCallback()).getErrorMessage() == null) {
// //                    medLinkMedtronicUtil.setCurrentCommand(null);
// //                    medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
// //                    double[] basalEntries = new double[24];
// //                    DateTime dateTime = new DateTime().withHourOfDay(0).withSecondOfMinute(0);
// //                    for (int i = 0; i < 24; i++) {
// //                        dateTime = dateTime.withHourOfDay(i);
// //                        basalEntries[i] = profile.getEntryForTime(dateTime.toInstant()).getRate();
// //                    }
// //                    medtronicPumpStatus.setBasalsByHour(basalEntries);
// //                }
// //                return f;
// //            };
// //            Function<Supplier<Stream<String>>, MedLinkStandardReturn<BasalProfile>> callback = pumpMessage.getBaseCallback().andThen(func).andThen(function);
// //            Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> argCallback =
// //                    new ProfileCallback(injector, aapsLogger, context, medLinkPumpPlugin);
// //            MedLinkPumpMessage msg = makePumpMessage(commandType, MedLinkCommandType.BaseProfile, callback,
// //                    argCallback, medLinkPumpPlugin.getBtSleepTime(),
// //                    new BleCommand(aapsLogger, medLinkServiceData));
//             // send and wait for response
// //            pumpMessage.setBaseCallback(callback);
//             sendAndListen(
//                 pumpMessage,
//                 DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries
//             )
//             //                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getRawContent()));
// //                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getMessageBody().getTxData()));
//
// //            String check = checkResponseContent(response, commandType.commandDescription, 1);
// //
// //            byte[] data = null;
// //
// //            if (check == null) {
// //
// //                data = response.getRawContentOfFrame();
// //
// //                MedLinkPumpMessage ackMsg = makePumpMessage(MedLinkCommandType.CommandACK, new PumpAckMessageBody());
// //
// //                while (checkIfWeHaveMoreData(commandType, response, data)) {
// //
// //                    response = sendAndListen(ackMsg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries), baseResultActivity);
// //
// ////                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs, HexDump.toHexStringDisplayable(response2.getRawContent()));
// ////                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs,
// ////                            HexDump.toHexStringDisplayable(response2.getMessageBody().getTxData()));
// //
// //                    String check2 = checkResponseContent(response, commandType.commandDescription, 1);
// //
// //                    if (check2 == null) {
// //
// //                        data = ByteUtil.concat(data, response.getRawContentOfFrame());
// //
// //                    } else {
// //                        this.errorMessage = check2;
// //                        aapsLogger.error(LTag.PUMPCOMM, "Error with response got GetProfile: " + check2);
// //                    }
// //                }
// //
// //            } else {
// //                errorMessage = check;
// //            }
//
// //            BasalProfile basalProfile = (BasalProfile) medtronicConverter.convertResponse(medtronicPumpPlugin.getPumpDescription().pumpType, MedtronicCommandType.valueOf(commandType.getCommand()), data);
// //
// //            if (basalProfile != null) {
// //                aapsLogger.debug(LTag.PUMPCOMM, "Converted response for {} is {}.", commandType.name(), basalProfile);
// //
// //                medLinkMedtronicUtil.setCurrentCommand(null);
// //                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
// //
// //                return basalProfile;
// //            }
//         } catch (e: RileyLinkCommunicationException) {
//             aapsLogger.error(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.message, retries + 1)
//         }
//         //        }
//
// //        aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.");
// //        medLinkMedtronicUtil.setCurrentCommand(null);
// //        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//         return profile
//     }

    //    private boolean checkIfWeHaveMoreData(MedLinkMedtronicCommandType commandType, MedLinkPumpMessage response, byte[] data) {
    //
    //        if (commandType == MedLinkMedtronicCommandType.GetBasalProfileSTD || //
    //                commandType == MedLinkMedtronicCommandType.GetBasalProfileA || //
    //                commandType == MedLinkMedtronicCommandType.GetBasalProfileB) {
    //            byte[] responseRaw = response.getRawContentOfFrame();
    //
    //            int last = responseRaw.length - 1;
    //
    //            aapsLogger.debug(LTag.PUMPCOMM, "Length: " + data.length);
    //
    //            if (data.length >= BasalProfile.MAX_RAW_DATA_SIZE) {
    //                return false;
    //            }
    //
    //            if (responseRaw.length < 2) {
    //                return false;
    //            }
    //
    //            return !(responseRaw[last] == 0x00 && responseRaw[last - 1] == 0x00 && responseRaw[last - 2] == 0x00);
    //        }
    //
    //        return false;
    //    }
    fun getPumpTime(): ClockDTO? {
        val responseObject = sendAndGetResponseWithCheck<MedLinkPumpStatus, Any>(
            MedLinkCommandType.GetState,
            MedLinkCommandType.NoCommand,
            StatusCallback(
                aapsLogger,
                medLinkPumpPlugin!!, medtronicPumpStatus!!
            ),
            medLinkPumpPlugin!!.btSleepTime,
            BleCommand(aapsLogger, medLinkServiceData)
        )
        return if (responseObject != null) {
            ClockDTO(
                LocalDateTime(),
                (responseObject as LocalDateTime?)!!
            )
        } else null
    }

    //    public TempBasalPair getTemporaryBasal() {
    //
    //        Object responseObject = sendAndGetResponseWithCheck(MedLinkCommandType.GetState, new StatusCallback(aapsLogger, medLinkPumpPlugin, medtronicPumpStatus));
    //
    //        return responseObject == null ? null : (TempBasalPair) responseObject;
    //    }
    fun getPumpSettings(): Map<String, PumpSettingDTO>? {
//TODO get status and build settings from it
//        Object responseObject = sendAndGetResponseWithCheck(MedLinkMedtronicCommandType.getSettings(medLinkMedtronicUtil
//                .getMedtronicPumpModel()));
//
//        return responseObject == null ? null : (Map<String, PumpSettingDTO>) responseObject;
        return null
    }

    fun <B, C> getBolusHistory(pumpMessage: MedLinkPumpMessage<B, C>): Boolean {
        try {
            runCommandWithArgs(pumpMessage)
        } catch (e: RileyLinkCommunicationException) {
            e.printStackTrace()
        }
        return false
    }

    private fun addPostProcessCommand(
        callback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<*>>?,
    ): Function<Supplier<Stream<String>>, MedLinkStandardReturn<*>>? {
        return callback?.andThen { f: MedLinkStandardReturn<*> ->
            if (f.getErrors().isEmpty()) {
                medLinkMedtronicUtil!!.currentCommand = null
            }
            f
        }
    }

    override fun getBGHistory(pumpMessage: MedLinkPumpMessage<Any, Any>): Boolean {
        return setCommand(pumpMessage)
    }

    override val bolusHistory: Boolean
        get() = TODO("Not yet implemented")

    fun <B, C> setBolus(pumpMessage: MedLinkPumpMessage<B, C>): Boolean {
        aapsLogger.info(LTag.PUMPCOMM, "setBolus: $pumpMessage")
        return setCommand(pumpMessage)
    }

    fun setTBR(tbr: TempBasalPair): Boolean {
        aapsLogger.info(LTag.PUMPCOMM, "setTBR: " + tbr.description)
        throw IllegalArgumentException("Unsupported command")
        //        return setCommand(MedLinkMedtronicCommandType.SetTemporaryBasal, tbr.getAsRawData());
    }

    fun setPumpTime(): Boolean {
        val gc = GregorianCalendar()
        gc.add(Calendar.SECOND, 5)
        aapsLogger.info(LTag.PUMPCOMM, "setPumpTime: " + DateTimeUtil.toString(gc))
        val i = 1
        val data = ByteArray(8)
        data[0] = 7
        data[i] = gc[Calendar.HOUR_OF_DAY].toByte()
        data[i + 1] = gc[Calendar.MINUTE].toByte()
        data[i + 2] = gc[Calendar.SECOND].toByte()
        val yearByte = MedLinkMedtronicUtil.getByteArrayFromUnsignedShort(gc[Calendar.YEAR], true)
        data[i + 3] = yearByte[0]
        data[i + 4] = yearByte[1]
        data[i + 5] = (gc[Calendar.MONTH] + 1).toByte()
        data[i + 6] = gc[Calendar.DAY_OF_MONTH].toByte()
        throw IllegalArgumentException("unsopported command set time")
        //        return setCommand(MedLinkMedtronicCommandType.SetRealTimeClock, data);
    }

    //    public boolean setCommand(MedLinkPumpMessage msg) {
    //
    ////        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {
    //
    //        try {
    ////                if (this.doWakeUpBeforeCommand)
    ////                    wakeUp(false);
    //
    ////                if (debugSetCommands)
    ////                aapsLogger.debug(LTag.PUMPCOMM, "{}: Body - {}", commandType.getCommandDescription(),
    ////                        ByteUtil.getHex(args));
    //
    //            runCommandWithArgs(commandType, args, resultActivity, argResultActivity);
    ////                MedLinkMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(body));
    ////
    ////                MedLinkMessage pumpMessage = runCommandWithArgs(msg);
    ////
    ////                if (debugSetCommands)
    ////                    aapsLogger.debug(LTag.PUMPCOMM, "{}: {}", commandType.getCommandDescription(), pumpMessage.getResponseContent());
    ////
    ////                if (pumpMessage.commandType == MedLinkMedtronicCommandType.CommandACK) {
    ////                    return true;
    ////                } else {
    ////                    aapsLogger.warn(LTag.PUMPCOMM, "We received non-ACK response from pump: {}", pumpMessage.getResponseContent());
    ////                }
    //
    //        } catch (RileyLinkCommunicationException e) {
    //            aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), 0);
    //        }
    ////        }
    //
    //        return false;
    //    }
    fun <B, C> setCommand(msg: MedLinkPumpMessage<B, C>): Boolean {
        try {
            runCommandWithArgs(msg)
        } catch (e: RileyLinkCommunicationException) {
            aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.message, 0)
        }
        return false
    }

    fun cancelTBR(): Boolean {
        return setTBR(TempBasalPair(0.0, false, 0))
    }

    //    public BatteryStatusDTO getRemainingBattery() {
    //
    //        Object responseObject = sendAndGetResponseWithCheck(MedLinkMedtronicCommandType.GetBatteryStatus);
    //
    //        return responseObject == null ? null : (BatteryStatusDTO) responseObject;
    //    }
    fun setBasalProfile(basalProfile: BasalProfile?): Boolean {

//        List<List<Byte>> basalProfileFrames = medLinkMedtronicUtil.getBasalProfileFrames(basalProfile.getRawData());
//
//        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {
//
//            MedLinkPumpMessage responseMessage = null;
//            try {
//                responseMessage = runCommandWithFrames(MedLinkMedtronicCommandType.SetBasalProfileSTD,
//                        basalProfileFrames);

//                if (responseMessage.commandType == MedLinkMedtronicCommandType.CommandACK)
//                    return true;

//            } catch (RileyLinkCommunicationException e) {
//                aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
//            }

//            if (responseMessage != null)
//                aapsLogger.warn(LTag.PUMPCOMM, "Set Basal Profile: Invalid response: commandType={},rawData={}", responseMessage.commandType, ByteUtil.shortHexString(responseMessage.getRawContent()));
//            else
//                aapsLogger.warn(LTag.PUMPCOMM, "Set Basal Profile: Null response.");
//        }
        throw IllegalArgumentException("set basal profile isn't supported")
        //        return false;
    }

    //    @Override public PumpStatus getPumpStatus() {
    //        return medtronicPumpStatus;
    //    }
    override fun wakeUp(force: Boolean) {
        super.wakeUp(force)
        aapsLogger.debug("MedLink wakeup")
        tryToConnectToDevice()
    }
}