package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import android.content.Context;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseStringAggregatorCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BasalMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BGHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BasalCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BolusHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.ProfileCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.StatusCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PumpMessage;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;

/**
 * Created by Dirceu on 17/09/20.
 * copied from {@link MedtronicCommunicationManager}
 */
@Singleton
public class MedLinkMedtronicCommunicationManager extends MedLinkCommunicationManager {
    @Inject MedLinkMedtronicPumpStatus medtronicPumpStatus;
    @Inject MedLinkMedtronicPumpPlugin medLinkPumpPlugin;
    @Inject MedtronicConverter medtronicConverter;
    @Inject MedLinkMedtronicUtil medLinkMedtronicUtil;
    //    @Inject MedtronicPumpHistoryDecoder medtronicPumpHistoryDecoder;
    @Inject MedLinkRFSpy rfspy;
    @Inject MedLinkServiceData medLinkServiceData;
    @Inject Context context;

    private final int MAX_COMMAND_TRIES = 3;
    private final int DEFAULT_TIMEOUT = 2000;
    private final long RILEYLINK_TIMEOUT = 15 * 60 * 1000; // 15 min

    private String errorMessage;
    private boolean debugSetCommands = false;

    private boolean doWakeUpBeforeCommand = true;
    private BasalProfile profile;


    @Inject
    public MedLinkMedtronicCommunicationManager(HasAndroidInjector injector, MedLinkRFSpy rfSpy) {
        super(injector, rfSpy);
    }

    @Inject
    public void onInit() {
        // we can't do this in the constructor, as sp only gets injected after the constructor has returned
        medtronicPumpStatus.previousConnection = sp.getLong(
                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
    }

    @Override
    public RLMessage createResponseMessage(byte[] payload) {
        return new PumpMessage(aapsLogger, payload);
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.medtronicPumpStatus.setPumpDeviceState(pumpDeviceState);
    }

    public void setDoWakeUpBeforeCommand(boolean doWakeUp) {
        this.doWakeUpBeforeCommand = doWakeUp;
    }


    @Override
    public boolean isDeviceReachable() {
        return isDeviceReachable(false);
    }


    /**
     * We do actual wakeUp and compare PumpModel with currently selected one. If returned model is
     * not Unknown, pump is reachable.
     *
     * @return
     */
    public boolean isDeviceReachable(boolean canPreventTuneUp) {

        PumpDeviceState state = medtronicPumpStatus.getPumpDeviceState();

        if (state != PumpDeviceState.PumpUnreachable)
            medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.WakingUp);

//        for (int retry = 0; retry < 5; retry++) {

        aapsLogger.debug(LTag.PUMPCOMM, "isDeviceReachable. Waking pump... first shot");




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
        return connectToDevice();

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


    private boolean connectToDevice() {

        PumpDeviceState state = medtronicPumpStatus.getPumpDeviceState();

        // check connection

//        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple



        aapsLogger.info(LTag.PUMPBTCOMM, "connect to device");
        getStatusData();
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

        return false;
    }

    public void getStatusData() {

        Function<Supplier<Stream<String>>, MedLinkStandardReturn<MedLinkPumpStatus>> callback =
                new StatusCallback(aapsLogger,
                        medLinkPumpPlugin, medtronicPumpStatus);
        Function<Supplier<Stream<String>>, MedLinkStandardReturn<MedLinkPumpStatus>> function = callback.andThen(f -> {
            aapsLogger.error("andThen " + f.getAnswer().collect(Collectors.joining()));
            Supplier<Stream<String>> answerLines = () -> f.getAnswer();
            if (answerLines.get().anyMatch(ans -> ans.contains("ready") || ans.contains("eomeomeom"))) {
                rememberLastGoodDeviceCommunicationTime();
                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
                if(!medLinkPumpPlugin.isInitialized()) {
                    medLinkPumpPlugin.postInit();
                }
            }
            Optional<String> model = answerLines.get().filter(ans -> ans.equalsIgnoreCase("medtronic")).findFirst();
            if (model.isPresent()) {
                if (model.filter(m -> m.toLowerCase().contains("veo")).isPresent()) {
                    //TODO need to get better model information
                    medLinkMedtronicUtil.setMedtronicPumpModel(MedLinkMedtronicDeviceType.MedLinkMedtronic_554_Veo);
                } else {
                    medLinkMedtronicUtil.setMedtronicPumpModel(MedLinkMedtronicDeviceType.MedLinkMedtronic_515_715);
                }
            }
            if (answerLines.get().anyMatch(m -> m.toLowerCase().contains("eomeomeom"))) {
                processPumpData(answerLines.get());
            }
            return f;
        });
        rfspy.transmitThenReceive(new MedLinkPumpMessage(MedLinkCommandType.GetState, MedLinkCommandType.NoCommand, function));
    }

    private void processPumpData(Stream<String> answerLines) {
        answerLines.forEach(line -> {
            if (line.matches("\\d\\d-\\d\\d-\\d{4}\\s\\d\\d:\\d\\d\\s\\d\\d%")) {
                ClockDTO pumpTime = new ClockDTO();
                DateTimeFormatter formatter = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm");
                pumpTime.pumpTime = LocalDateTime.parse(line.substring(0, 10), formatter);
                medLinkMedtronicUtil.setPumpTime(pumpTime);
            }
        });
    }


    @Override
    public boolean tryToConnectToDevice() {
        return isDeviceReachable(true);
    }


    private void runCommandWithArgs(MedLinkCommandType command, MedLinkCommandType args, BaseCallback resultActivity) throws RileyLinkCommunicationException {
        rfspy.transmitThenReceive(makePumpMessage(command, args, resultActivity));
        //TODO avaliar este retorno

    }

    private MedLinkPumpMessage runCommandWithArgs(MedLinkPumpMessage msg) throws RileyLinkCommunicationException {

        if (debugSetCommands)
            aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: ");

        // look for ack from short message
        BaseCallback baseCallback = new BaseStringAggregatorCallback();

        MedLinkPumpMessage shortResponse = sendAndListen(msg);
        aapsLogger.debug(LTag.PUMPCOMM, "Short response: " + shortResponse.toString() + " to command: " + msg.getCommandType());
        return shortResponse;
//        if (shortResponse.commandType == MedLinkMedtronicCommandType.CommandACK) {
//            if (debugSetCommands)
//                aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: Got ACK response");
//
//            rval = sendAndListen(msg, null);
//            if (debugSetCommands)
//                aapsLogger.debug(LTag.PUMPCOMM, "2nd Response: {}", rval);
//
//            return rval;
//        } else {
//            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithArgs: Pump did not ack Attention packet");
//            return new MedLinkPumpMessage(aapsLogger, "No ACK after Attention packet.");
//        }
    }


//    private MedLinkPumpMessage runCommandWithFrames(MedLinkMedtronicCommandType commandType, List<List<Byte>> frames)
//            throws RileyLinkCommunicationException {
//
//        aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: {}", commandType.name());
//
//        MedLinkPumpMessage rval = null;
//        MedLinkPumpMessage shortMessage = makePumpMessage(commandType, new CarelinkShortMessageBody(new byte[]{0}));
//        // look for ack from short message
//        //TODO null base result activity
//        BaseResultActivity baseResultActivity = null;
//        MedLinkPumpMessage shortResponse = sendAndListen(shortMessage, baseResultActivity);
//
//        if (shortResponse.commandType != MedLinkMedtronicCommandType.CommandACK) {
//            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ack Attention packet");
//
//            return new MedLinkPumpMessage(aapsLogger, "No ACK after start message.");
//        } else {
//            aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for Attention packet");
//        }
//
//        int frameNr = 1;
//
//        for (List<Byte> frame : frames) {
//
//            byte[] frameData = medLinkMedtronicUtil.createByteArray(frame);
//
//            // aapsLogger.debug(LTag.PUMPCOMM,"Frame {} data:\n{}", frameNr, ByteUtil.getCompactString(frameData));
//
//            MedLinkPumpMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(frameData));
//
//            //TODO null result activity
//            rval = sendAndListen(msg, baseResultActivity);
//
//            // aapsLogger.debug(LTag.PUMPCOMM,"PumpResponse: " + rval);
//
//            if (rval.commandType != MedLinkMedtronicCommandType.CommandACK) {
//                aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ACK frame #{}", frameNr);
//
//                aapsLogger.error(LTag.PUMPCOMM, "Run command with Frames FAILED (command={}, response={})", commandType.name(),
//                        rval.toString());
//
//                return new MedLinkPumpMessage(aapsLogger, "No ACK after frame #" + frameNr);
//            } else {
//                aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for frame #{}", (frameNr));
//            }
//
//            frameNr++;
//        }
//
//        return rval;
//
//    }


//    public PumpHistoryResult getPumpHistory(PumpHistoryEntry lastEntry, LocalDateTime targetDate) {
//
//        PumpHistoryResult pumpTotalResult = new PumpHistoryResult(aapsLogger, lastEntry, targetDate == null ? null
//                : DateTimeUtil.toATechDate(targetDate));
//
//        if (doWakeUpBeforeCommand)
//            wakeUp(receiverDeviceAwakeForMinutes, false);
//
//        aapsLogger.debug(LTag.PUMPCOMM, "Current command: " + medLinkMedtronicUtil.getCurrentCommand());
//
//        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
//        boolean doneWithError = false;
//
//        for (int pageNumber = 0; pageNumber < 5; pageNumber++) {
//
//            RawHistoryPage rawHistoryPage = new RawHistoryPage(aapsLogger);
//            // wakeUp(receiverDeviceAwakeForMinutes, false);
//            MedLinkPumpMessage getHistoryMsg = makePumpMessage(MedLinkMedtronicCommandType.GetHistoryData,
//                    new GetHistoryPageCarelinkMessageBody(pageNumber));
//
//            aapsLogger.info(LTag.PUMPCOMM, "getPumpHistory: Page {}", pageNumber);
//            // aapsLogger.info(LTag.PUMPCOMM,"getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
//            // Ask the pump to transfer history (we get first frame?)
//
//            MedLinkPumpMessage firstResponse = null;
//            boolean failed = false;
//
//            medLinkMedtronicUtil.setCurrentCommand(MedLinkMedtronicCommandType.GetHistoryData, pageNumber, null);
//
//            for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {
//
//                try {
//                    firstResponse = runCommandWithArgs(getHistoryMsg);
//                    failed = false;
//                    break;
//                } catch (RileyLinkCommunicationException e) {
//                    aapsLogger.error(LTag.PUMPCOMM, "First call for PumpHistory failed (retry={})", retries);
//                    failed = true;
//                }
//            }
//
//            if (failed) {
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//                return pumpTotalResult;
//            }
//
//            // aapsLogger.info(LTag.PUMPCOMM,"getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));
//
//            MedLinkPumpMessage ackMsg = makePumpMessage(MedLinkMedtronicCommandType.CommandACK, new PumpAckMessageBody());
//            GetHistoryPageCarelinkMessageBody currentResponse = new GetHistoryPageCarelinkMessageBody(firstResponse
//                    .getMessageBody().getTxData());
//            int expectedFrameNum = 1;
//            boolean done = false;
//            // while (expectedFrameNum == currentResponse.getFrameNumber()) {
//
//            int failures = 0;
//            while (!done) {
//                // examine current response for problems.
//                byte[] frameData = currentResponse.getFrameData();
//                if ((frameData != null) && (frameData.length > 0)
//                        && currentResponse.getFrameNumber() == expectedFrameNum) {
//                    // success! got a frame.
//                    if (frameData.length != 64) {
//                        aapsLogger.warn(LTag.PUMPCOMM, "Expected frame of length 64, got frame of length " + frameData.length);
//                        // but append it anyway?
//                    }
//                    // handle successful frame data
//                    rawHistoryPage.appendData(currentResponse.getFrameData());
//                    // RileyLinkMedtronicService.getInstance().announceProgress(((100 / 16) *
//                    // currentResponse.getFrameNumber() + 1));
//                    medLinkMedtronicUtil.setCurrentCommand(MedLinkMedtronicCommandType.GetHistoryData, pageNumber,
//                            currentResponse.getFrameNumber());
//
//                    aapsLogger.info(LTag.PUMPCOMM, "getPumpHistory: Got frame {} of Page {}", currentResponse.getFrameNumber(), pageNumber);
//                    // Do we need to ask for the next frame?
//                    if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
//                        expectedFrameNum++;
//                    } else {
//                        done = true; // successful completion
//                    }
//                } else {
//                    if (frameData == null) {
//                        aapsLogger.error(LTag.PUMPCOMM, "null frame data, retrying");
//                    } else if (currentResponse.getFrameNumber() != expectedFrameNum) {
//                        aapsLogger.warn(LTag.PUMPCOMM, "Expected frame number {}, received {} (retrying)", expectedFrameNum,
//                                currentResponse.getFrameNumber());
//                    } else if (frameData.length == 0) {
//                        aapsLogger.warn(LTag.PUMPCOMM, "Frame has zero length, retrying");
//                    }
//                    failures++;
//                    if (failures == 6) {
//                        aapsLogger.error(LTag.PUMPCOMM,
//                                "getPumpHistory: 6 failures in attempting to download frame {} of page {}, giving up.",
//                                expectedFrameNum, pageNumber);
//                        done = true; // failure completion.
//                        doneWithError = true;
//                    }
//                }
//
//                if (!done) {
//                    // ask for next frame
//                    MedLinkPumpMessage nextMsg = null;
//
//                    for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {
//
//                        try {
//                            nextMsg = sendAndListen(ackMsg, null);
//                            break;
//                        } catch (RileyLinkCommunicationException e) {
//                            aapsLogger.error(LTag.PUMPCOMM, "Problem acknowledging frame response. (retry={})", retries);
//                        }
//                    }
//
//                    if (nextMsg != null)
//                        currentResponse = new GetHistoryPageCarelinkMessageBody(nextMsg.getMessageBody().getTxData());
//                    else {
//                        aapsLogger.error(LTag.PUMPCOMM, "We couldn't acknowledge frame from pump, aborting operation.");
//                    }
//                }
//            }
//
//            if (rawHistoryPage.getLength() != 1024) {
//                aapsLogger.warn(LTag.PUMPCOMM, "getPumpHistory: short page.  Expected length of 1024, found length of "
//                        + rawHistoryPage.getLength());
//                doneWithError = true;
//            }
//
//            if (!rawHistoryPage.isChecksumOK()) {
//                aapsLogger.error(LTag.PUMPCOMM, "getPumpHistory: checksum is wrong");
//                doneWithError = true;
//            }
//
//            if (doneWithError) {
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//                return pumpTotalResult;
//            }
//
//            rawHistoryPage.dumpToDebug();
//
//            List<PumpHistoryEntry> medtronicHistoryEntries = medtronicPumpHistoryDecoder.processPageAndCreateRecords(rawHistoryPage);
//
//            aapsLogger.debug(LTag.PUMPCOMM, "getPumpHistory: Found {} history entries.", medtronicHistoryEntries.size());
//
//            pumpTotalResult.addHistoryEntries(medtronicHistoryEntries, pageNumber);
//
//            aapsLogger.debug(LTag.PUMPCOMM, "getPumpHistory: Search status: Search finished: {}", pumpTotalResult.isSearchFinished());
//
//            if (pumpTotalResult.isSearchFinished()) {
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//
//                return pumpTotalResult;
//            }
//        }
//
//        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//
//        return pumpTotalResult;
//
//    }


    public String getErrorResponse() {
        return this.errorMessage;
    }


    @Override
    public byte[] createPumpMessageContent(RLMessageType type) {
        switch (type) {
            case PowerOn:

                return medLinkMedtronicUtil.buildCommandPayload(medLinkServiceData, MedLinkMedtronicCommandType.RFPowerOn, //
                        new byte[]{2, 1, (byte) receiverDeviceAwakeForMinutes}); // maybe this is better FIXME

            case ReadSimpleData:
//                return medLinkMedtronicUtil.buildCommandPayload(medLinkServiceData, MedLinkMedtronicCommandType.RFPowerOn.PumpModel, null);
                return MedLinkCommandType.GetState.getRaw();
        }
        return new byte[0];
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

    private <B> MedLinkPumpMessage<B> makePumpMessage(MedLinkCommandType messageType, Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> result) {
        return new MedLinkPumpMessage<B>(messageType, MedLinkCommandType.NoCommand, result);
    }


    private <B> MedLinkPumpMessage<B> makePumpMessage(MedLinkCommandType messageType,
                                                      MedLinkCommandType argument,
                                                      Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseResultActivity) {
        return new MedLinkPumpMessage<B>(messageType, argument, baseResultActivity);
    }

    private <B> MedLinkPumpMessage<B> makePumpMessage(MedLinkCommandType messageType,
                                                      MedLinkCommandType argument,
                                                      Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseResultActivity,
                                                      Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> argResultActivity) {
        return new BasalMedLinkMessage<>(messageType, argument, baseResultActivity, argResultActivity);
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
    private MedLinkPumpMessage sendAndGetResponse(MedLinkPumpMessage msg)
            throws RileyLinkCommunicationException {
        // wakeUp
        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);

        // create message


        // send and wait for response
        MedLinkPumpMessage response = sendAndListen(msg);

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

        return response;
    }


    private MedLinkPumpMessage sendAndListen(MedLinkPumpMessage msg) throws RileyLinkCommunicationException {
        return sendAndListen(msg, 4000); // 2000
    }


    // All pump communications go through this function.
    protected MedLinkPumpMessage sendAndListen(MedLinkPumpMessage msg, int timeout_ms) throws RileyLinkCommunicationException {
        return (MedLinkPumpMessage) super.sendAndListen(msg, timeout_ms);
    }


    private Object sendAndGetResponseWithCheck(MedLinkCommandType commandType, BaseCallback baseCallback) {

        return sendAndGetResponseWithCheck(commandType, null, baseCallback);
    }


    private <B> Object sendAndGetResponseWithCheck(MedLinkCommandType commandType, MedLinkCommandType argument, Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> baseResultActivity) {

        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: {}", commandType);

        for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {

            try {
                MedLinkPumpMessage pumpMessage = makePumpMessage(commandType, argument, baseResultActivity);
                //TODO look were to pu the medlink command result, here maybe a good place to
                MedLinkPumpMessage response = sendAndGetResponse(pumpMessage);

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

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }

        }

        return null;
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


    public void setPumpModel() {
//        this.medLinkMedtronicUtil
    }

    public <B> MedLinkMedtronicDeviceType getPumpModel(Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> activity) {

        Object responseObject = sendAndGetResponseWithCheck(MedLinkCommandType.Connect, MedLinkCommandType.NoCommand, activity);

        return responseObject == null ? null : (MedLinkMedtronicDeviceType) responseObject;
    }


    @Override public void wakeUp(int duration_minutes, boolean force) {
//        super.wakeUp(duration_minutes, force);
        setPumpDeviceState(PumpDeviceState.WakingUp);

//        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
        rfspy.initializeMedLink();
//        rfspy.initializeRileyLink();
        aapsLogger.info(LTag.PUMPCOMM, "before wakeup ");
        BaseStringAggregatorCallback resultActivity = new BaseStringAggregatorCallback();
        Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> activity = resultActivity.andThen(s -> {
            aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + s);
            if (s.getAnswer().anyMatch(f -> f.contains("ready"))) {
                lastGoodReceiverCommunicationTime = System.currentTimeMillis();
            }
            return s;
        });
        rfspy.transmitThenReceive(new MedLinkPumpMessage<>(MedLinkCommandType.Connect, MedLinkCommandType.NoCommand, activity
        ));
        // FIXME wakeUp successful !!!!!!!!!!!!!!!!!!
        //nextWakeUpRequired = System.currentTimeMillis() + (receiverDeviceAwakeForMinutes * 60 * 1000);
    }

    @Override public PumpStatus getPumpStatus() {
        return medLinkPumpPlugin.getPumpStatusData();
    }

    public void setProfile(BasalProfile profile) {
        this.profile = profile;
    }

    public BasalProfile getProfile() {
        return this.profile;
    }

    public BasalProfile getBasalProfile(Function<MedLinkStandardReturn<BasalProfile>, MedLinkStandardReturn<BasalProfile>> function) {

        // wakeUp
        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        MedLinkCommandType commandType = MedLinkCommandType.ActiveBasalProfile;

        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: {}", commandType);

        medLinkMedtronicUtil.setCurrentCommand(commandType);

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
        int retries = 5;
//        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

        try {
            // create message
            MedLinkPumpMessage msg;

            BasalCallback baseResultActivity = new BasalCallback(aapsLogger, medLinkPumpPlugin);

            Function<MedLinkStandardReturn<BasalProfile>, MedLinkStandardReturn<BasalProfile>> func = f -> {
                if (baseResultActivity.getErrorMessage() == null) {
                    medLinkMedtronicUtil.setCurrentCommand(null);
                    medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
                    Double[] basalEntries = new Double[24];
                    DateTime dateTime = new DateTime().withHourOfDay(0).withSecondOfMinute(0);
                    for (int i = 0; i < 24; i++) {
                        dateTime = dateTime.withHourOfDay(i);
                        basalEntries[i] = profile.getEntryForTime(dateTime.toInstant()).rate;
                    }
                    medtronicPumpStatus.basalsByHour = basalEntries;
                }
                return f;
            };
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<BasalProfile>> callback = baseResultActivity.andThen(func).andThen(function);
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile>> argCallback =
                    new ProfileCallback(injector, aapsLogger, context, medLinkPumpPlugin);
            msg = makePumpMessage(commandType, MedLinkCommandType.BaseProfile, callback,
                    argCallback);
            // send and wait for response
            MedLinkPumpMessage response = sendAndListen(msg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));
//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getRawContent()));
//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getMessageBody().getTxData()));

//            String check = checkResponseContent(response, commandType.commandDescription, 1);
//
//            byte[] data = null;
//
//            if (check == null) {
//
//                data = response.getRawContentOfFrame();
//
//                MedLinkPumpMessage ackMsg = makePumpMessage(MedLinkCommandType.CommandACK, new PumpAckMessageBody());
//
//                while (checkIfWeHaveMoreData(commandType, response, data)) {
//
//                    response = sendAndListen(ackMsg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries), baseResultActivity);
//
////                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs, HexDump.toHexStringDisplayable(response2.getRawContent()));
////                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs,
////                            HexDump.toHexStringDisplayable(response2.getMessageBody().getTxData()));
//
//                    String check2 = checkResponseContent(response, commandType.commandDescription, 1);
//
//                    if (check2 == null) {
//
//                        data = ByteUtil.concat(data, response.getRawContentOfFrame());
//
//                    } else {
//                        this.errorMessage = check2;
//                        aapsLogger.error(LTag.PUMPCOMM, "Error with response got GetProfile: " + check2);
//                    }
//                }
//
//            } else {
//                errorMessage = check;
//            }

//            BasalProfile basalProfile = (BasalProfile) medtronicConverter.convertResponse(medtronicPumpPlugin.getPumpDescription().pumpType, MedtronicCommandType.valueOf(commandType.getCommand()), data);
//
//            if (basalProfile != null) {
//                aapsLogger.debug(LTag.PUMPCOMM, "Converted response for {} is {}.", commandType.name(), basalProfile);
//
//                medLinkMedtronicUtil.setCurrentCommand(null);
//                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
//
//                return basalProfile;
//            }

        } catch (RileyLinkCommunicationException e) {
            aapsLogger.error(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
        }
//        }

//        aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.");
//        medLinkMedtronicUtil.setCurrentCommand(null);
//        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

        return profile;

    }


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


    public ClockDTO getPumpTime() {

        ClockDTO clockDTO = new ClockDTO();
        clockDTO.localDeviceTime = new LocalDateTime();

        Object responseObject = sendAndGetResponseWithCheck(MedLinkCommandType.GetState,
                new StatusCallback(aapsLogger,
                        medLinkPumpPlugin, medtronicPumpStatus));

        if (responseObject != null) {
            clockDTO.pumpTime = (LocalDateTime) responseObject;
            return clockDTO;
        }

        return null;
    }


    public TempBasalPair getTemporaryBasal() {

        Object responseObject = sendAndGetResponseWithCheck(MedLinkCommandType.GetState, new StatusCallback(aapsLogger, medLinkPumpPlugin, medtronicPumpStatus));

        return responseObject == null ? null : (TempBasalPair) responseObject;
    }


    public Map<String, PumpSettingDTO> getPumpSettings() {
//TODO get status and build settings from it
//        Object responseObject = sendAndGetResponseWithCheck(MedLinkMedtronicCommandType.getSettings(medLinkMedtronicUtil
//                .getMedtronicPumpModel()));
//
//        return responseObject == null ? null : (Map<String, PumpSettingDTO>) responseObject;
        return null;
    }

    public boolean getBolusHistory() {
        BolusHistoryCallback resultActivity = new BolusHistoryCallback(aapsLogger, medLinkPumpPlugin);
        return setCommand(MedLinkCommandType.BolusHistory, MedLinkCommandType.NoCommand,
                resultActivity);
    }

    public boolean getBGHistory() {
        BaseCallback resultActivity = new BGHistoryCallback(injector, medLinkPumpPlugin);
        return setCommand(MedLinkCommandType.BGHistory, MedLinkCommandType.NoCommand, resultActivity);
    }

    public Boolean setBolus(double units) {

        aapsLogger.info(LTag.PUMPCOMM, "setBolus: " + units);

        BaseCallback resultActivity = new BolusCallback();
        MedLinkCommandType bolusCommand = MedLinkCommandType.BolusAmount;
        bolusCommand.insulinAmount = units;
        return setCommand(MedLinkCommandType.Bolus, bolusCommand, resultActivity);

    }


    public boolean setTBR(TempBasalPair tbr) {

        aapsLogger.info(LTag.PUMPCOMM, "setTBR: " + tbr.getDescription());
        throw new IllegalArgumentException("Unsupported command");
//        return setCommand(MedLinkMedtronicCommandType.SetTemporaryBasal, tbr.getAsRawData());
    }


    public Boolean setPumpTime() {

        GregorianCalendar gc = new GregorianCalendar();
        gc.add(Calendar.SECOND, 5);

        aapsLogger.info(LTag.PUMPCOMM, "setPumpTime: " + DateTimeUtil.toString(gc));

        int i = 1;
        byte[] data = new byte[8];
        data[0] = 7;
        data[i] = (byte) gc.get(Calendar.HOUR_OF_DAY);
        data[i + 1] = (byte) gc.get(Calendar.MINUTE);
        data[i + 2] = (byte) gc.get(Calendar.SECOND);

        byte[] yearByte = medLinkMedtronicUtil.getByteArrayFromUnsignedShort(gc.get(Calendar.YEAR), true);

        data[i + 3] = yearByte[0];
        data[i + 4] = yearByte[1];

        data[i + 5] = (byte) (gc.get(Calendar.MONTH) + 1);
        data[i + 6] = (byte) gc.get(Calendar.DAY_OF_MONTH);

        //aapsLogger.info(LTag.PUMPCOMM,"setPumpTime: Body:  " + ByteUtil.getHex(data));
        throw new IllegalArgumentException("unsopported command set time");
//        return setCommand(MedLinkMedtronicCommandType.SetRealTimeClock, data);

    }


    private boolean setCommand(MedLinkCommandType commandType, MedLinkCommandType args, BaseCallback resultActivity) {

//        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

        try {
//                if (this.doWakeUpBeforeCommand)
//                    wakeUp(false);

//                if (debugSetCommands)
//                aapsLogger.debug(LTag.PUMPCOMM, "{}: Body - {}", commandType.getCommandDescription(),
//                        ByteUtil.getHex(args));

            runCommandWithArgs(commandType, args, resultActivity);
//                MedLinkMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(body));
//
//                MedLinkMessage pumpMessage = runCommandWithArgs(msg);
//
//                if (debugSetCommands)
//                    aapsLogger.debug(LTag.PUMPCOMM, "{}: {}", commandType.getCommandDescription(), pumpMessage.getResponseContent());
//
//                if (pumpMessage.commandType == MedLinkMedtronicCommandType.CommandACK) {
//                    return true;
//                } else {
//                    aapsLogger.warn(LTag.PUMPCOMM, "We received non-ACK response from pump: {}", pumpMessage.getResponseContent());
//                }

        } catch (RileyLinkCommunicationException e) {
            aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), 0);
        }
//        }

        return false;
    }


    public boolean cancelTBR() {
        return setTBR(new TempBasalPair(0.0d, false, 0));
    }


//    public BatteryStatusDTO getRemainingBattery() {
//
//        Object responseObject = sendAndGetResponseWithCheck(MedLinkMedtronicCommandType.GetBatteryStatus);
//
//        return responseObject == null ? null : (BatteryStatusDTO) responseObject;
//    }


    public Boolean setBasalProfile(BasalProfile basalProfile) {

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
        throw new IllegalArgumentException("set basal profile isn't supported");
//        return false;

    }

//    @Override public PumpStatus getPumpStatus() {
//        return medtronicPumpStatus;
//    }

    @Override public void wakeUp(boolean force) {
        super.wakeUp(force);
        aapsLogger.debug("MedLink wakeup");
        tryToConnectToDevice();
    }

}

