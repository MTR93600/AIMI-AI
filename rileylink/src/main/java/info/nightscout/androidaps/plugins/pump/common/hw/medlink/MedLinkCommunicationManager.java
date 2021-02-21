package info.nightscout.androidaps.plugins.pump.common.hw.medlink;



import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseStringAggregatorCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.CommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.FrequencyScanResults;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * This is abstract class for MedLink Communication, this one needs to be extended by specific "Pump" class.
 * <p>
 * Created by Dirceu on 18/09/20.
 * Copied from RileyLinkCommunicationManager
 */
public abstract class MedLinkCommunicationManager implements CommunicationManager {

    @Inject protected AAPSLogger aapsLogger;
    @Inject protected SP sp;
    @Inject protected MedLinkServiceData medLinkServiceData;
    @Inject protected ServiceTaskExecutor serviceTaskExecutor;


    private final int SCAN_TIMEOUT = 1500;
    private final int ALLOWED_PUMP_UNREACHABLE = 10 * 60 * 1000; // 10 minutes

    public final HasAndroidInjector injector;
    protected final MedLinkRFSpy rfspy;
    protected int receiverDeviceAwakeForMinutes = 1; // override this in constructor of specific implementation
    protected String receiverDeviceID; // String representation of receiver device (ex. Pump (xxxxxx) or Pod (yyyyyy))
    protected long lastGoodReceiverCommunicationTime = 0;
    //    protected PumpStatus pumpStatus;
    private long nextWakeUpRequired = 0L;

    private int timeoutCount = 0;


    public MedLinkCommunicationManager(HasAndroidInjector injector, MedLinkRFSpy rfspy) {
        this.injector = injector;
        injector.androidInjector().inject(this);
        this.rfspy = rfspy;
    }


    // All pump communications go through this function.
    protected MedLinkPumpMessage sendAndListen(MedLinkPumpMessage msg, int timeout_ms)
            throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, null);
    }

    private MedLinkPumpMessage sendAndListen(MedLinkPumpMessage msg, int timeout_ms, Integer extendPreamble_ms)
            throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, 0, extendPreamble_ms);
    }

    // For backward compatibility
    private MedLinkPumpMessage sendAndListen(MedLinkPumpMessage msg, int timeout_ms, int repeatCount, Integer extendPreamble_ms)
            throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, repeatCount, 0, extendPreamble_ms);
    }

    protected <B> MedLinkPumpMessage sendAndListen(MedLinkPumpMessage<B> msg, int timeout_ms, int repeatCount, int retryCount, Integer extendPreamble_ms)
            throws RileyLinkCommunicationException {

        // internal flag
        if (msg != null) {
            aapsLogger.info(LTag.PUMPCOMM, "Sent:" + msg.getCommandType().code);
        }

        Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>> function = msg.getBaseCallBack().andThen(f ->{
            Supplier<Stream<String>> answer = () -> f.getAnswer();
            if(f.getErrors() == null || f.getErrors().isEmpty()){
                rememberLastGoodDeviceCommunicationTime();
            }else{
                aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Response is invalid ! [errors={}", f.getErrors());
                if(answer.get().anyMatch(ans -> ans.contains("at+sleep")) || answer.get().anyMatch(ans -> ans.contains("powerdown"))){
//                    throw new RileyLinkCommunicationException(RileyLinkBLEError.Timeout);
                    f.addError(MedLinkStandardReturn.ParsingError.Timeout);
                }else if(answer.get().anyMatch(ans -> ans.contains("wake up not confirmed")) || answer.get().anyMatch(ans -> ans.equals(""))){ //TODO look for ex error
                    f.addError(MedLinkStandardReturn.ParsingError.Interrupted);
                }
            }
            return f;
        });
        msg.setBaseCallBack(function);
        rfspy.transmitThenReceive(msg);

//        RadioResponse radioResponse = rfSpyResponse.getRadioResponse(injector);
//        RLMessage response = createResponseMessage(radioResponse.getPayload());
//
//        if (response.isValid()) {
//            // Mark this as the last time we heard from the pump.
//            rememberLastGoodDeviceCommunicationTime();
//        } else {
//            aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Response is invalid ! [interrupted={}, timeout={}, unknownCommand={}, invalidParam={}]", rfSpyResponse.wasInterrupted(),
//                    rfSpyResponse.wasTimeout(), rfSpyResponse.isUnknownCommand(), rfSpyResponse.isInvalidParam());
//
//            if (rfSpyResponse.wasTimeout()) {
//                if (hasTunning()) {
//                    timeoutCount++;
//
//                    long diff = System.currentTimeMillis() - getPumpStatus().lastConnection;
//
//                    if (diff > ALLOWED_PUMP_UNREACHABLE) {
//                        aapsLogger.warn(LTag.PUMPCOMM, "We reached max time that Pump can be unreachable. Starting Tuning.");
//                        serviceTaskExecutor.startTask(new WakeAndTuneTask(injector));
//                        timeoutCount = 0;
//                    }
//                }
//
//                throw new RileyLinkCommunicationException(RileyLinkBLEError.Timeout);
//            } else if (rfSpyResponse.wasInterrupted()) {
//                throw new RileyLinkCommunicationException(RileyLinkBLEError.Interrupted);
//            }
//        }
//
//        if (showPumpMessages) {
//            aapsLogger.info(LTag.PUMPCOMM, "Received:" + ByteUtil.shortHexString(rfSpyResponse.getRadioResponse(injector).getPayload()));
//        }

        return msg;
    }


    @Override public void wakeUp(boolean force) {
        wakeUp(receiverDeviceAwakeForMinutes, force);
    }


    @Override public int getNotConnectedCount() {
        return rfspy != null ? rfspy.notConnectedCount : 0;
    }

    @Override public boolean hasTunning() {
        return true;
    }


    // FIXME change wakeup
    // TODO we might need to fix this. Maybe make pump awake for shorter time (battery factor for pump) - Andy
    public  <B> void wakeUp(int duration_minutes, boolean force, Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>  resultActivity) {
        // If it has been longer than n minutes, do wakeup. Otherwise assume pump is still awake.
        // **** FIXME: this wakeup doesn't seem to work well... must revisit
        // receiverDeviceAwakeForMinutes = duration_minutes;

        setPumpDeviceState(PumpDeviceState.WakingUp);

        if (force)
            nextWakeUpRequired = 0L;

        if (System.currentTimeMillis() > nextWakeUpRequired) {
            aapsLogger.info(LTag.PUMPCOMM, "Waking pump...");

//            byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple

            rfspy.transmitThenReceive(new MedLinkPumpMessage<>(MedLinkCommandType.GetState,MedLinkCommandType.NoCommand,resultActivity));
//            aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));

            // FIXME wakeUp successful !!!!!!!!!!!!!!!!!!

            nextWakeUpRequired = System.currentTimeMillis() + (receiverDeviceAwakeForMinutes * 60 * 1000);
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Last pump communication was recent, not waking pump.");
        }

        // long lastGoodPlus = getLastGoodReceiverCommunicationTime() + (receiverDeviceAwakeForMinutes * 60 * 1000);
        //
        // if (System.currentTimeMillis() > lastGoodPlus || force) {
        // LOG.info("Waking pump...");
        //
        // byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.PowerOn);
        // RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(pumpMsgContent), (byte) 0, (byte) 200, (byte)
        // 0, (byte) 0, 15000, (byte) 0);
        // LOG.info("wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
        // } else {
        // LOG.trace("Last pump communication was recent, not waking pump.");
        // }
    }


    @Override public void setRadioFrequencyForPump(double freqMHz) {
//        rfspy.setBaseFrequency(freqMHz);
    }


    @Override public double tuneForDevice() {
        return scanForDevice(medLinkServiceData.rileyLinkTargetFrequency.getScanFrequencies());
    }


    /**
     * If user changes pump and one pump is running in US freq, and other in WW, then previously set frequency would be
     * invalid,
     * so we would need to retune. This checks that saved frequency is correct range.
     *
     * @param frequency
     * @return
     */
    @Override public boolean isValidFrequency(double frequency) {

        double[] scanFrequencies = medLinkServiceData.rileyLinkTargetFrequency.getScanFrequencies();

        if (scanFrequencies.length == 1) {
            return Round.isSame(scanFrequencies[0], frequency);
        } else {
            return (scanFrequencies[0] <= frequency && scanFrequencies[scanFrequencies.length - 1] >= frequency);
        }
    }


    private double scanForDevice(double[] frequencies) {
        aapsLogger.info(LTag.PUMPCOMM, "Scanning for receiver ({})", receiverDeviceID);
        wakeUp(receiverDeviceAwakeForMinutes, false);
        FrequencyScanResults results = new FrequencyScanResults();

//        for (int i = 0; i < frequencies.length; i++) {
//            int tries = 3;
//            FrequencyTrial trial = new FrequencyTrial();
//            trial.frequencyMHz = frequencies[i];
//            rfspy.setBaseFrequency(frequencies[i]);
//
//            int sumRSSI = 0;
//            for (int j = 0; j < tries; j++) {
//
//                byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData);
//                RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(injector, pumpMsgContent), (byte) 0, (byte) 0,
//                        (byte) 0, (byte) 0, 1250, (byte) 0);
//                if (resp.wasTimeout()) {
//                    aapsLogger.error(LTag.PUMPCOMM, "scanForPump: Failed to find pump at frequency {}", frequencies[i]);
//                } else if (resp.looksLikeRadioPacket()) {
//                    RadioResponse radioResponse = new RadioResponse(injector);
//
//                    try {
//
//                        radioResponse.init(resp.getRaw());
//
//                        if (radioResponse.isValid()) {
//                            int rssi = calculateRssi(radioResponse.rssi);
//                            sumRSSI += rssi;
//                            trial.rssiList.add(rssi);
//                            trial.successes++;
//                        } else {
//                            aapsLogger.warn(LTag.PUMPCOMM, "Failed to parse radio response: " + ByteUtil.shortHexString(resp.getRaw()));
//                            trial.rssiList.add(-99);
//                        }
//
//                    } catch (RileyLinkCommunicationException rle) {
//                        aapsLogger.warn(LTag.PUMPCOMM, "Failed to decode radio response: " + ByteUtil.shortHexString(resp.getRaw()));
//                        trial.rssiList.add(-99);
//                    }
//
//                } else {
//                    aapsLogger.error(LTag.PUMPCOMM, "scanForPump: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
//                    trial.rssiList.add(-99);
//                }
//                trial.tries++;
//            }
//            sumRSSI += -99.0 * (trial.tries - trial.successes);
//            trial.averageRSSI2 = (double) (sumRSSI) / (double) (trial.tries);
//
//            trial.calculateAverage();
//
//            results.trials.add(trial);
//        }

//        results.dateTime = System.currentTimeMillis();
//
//        StringBuilder stringBuilder = new StringBuilder("Scan results:\n");
//
//        for (int k = 0; k < results.trials.size(); k++) {
//            FrequencyTrial one = results.trials.get(k);
//
//            stringBuilder.append(String.format("Scan Result[%s]: Freq=%s, avg RSSI = %s\n", "" + k, ""
//                    + one.frequencyMHz, "" + one.averageRSSI + ", RSSIs =" + one.rssiList));
//        }
//
//        aapsLogger.info(LTag.PUMPCOMM, stringBuilder.toString());
//
//        results.sort(); // sorts in ascending order
//
//        FrequencyTrial bestTrial = results.trials.get(results.trials.size() - 1);
//        results.bestFrequencyMHz = bestTrial.frequencyMHz;
//        if (bestTrial.successes > 0) {
//            rfspy.setBaseFrequency(results.bestFrequencyMHz);
//            aapsLogger.debug(LTag.PUMPCOMM, "Best frequency found: " + results.bestFrequencyMHz);
//            return results.bestFrequencyMHz;
//        } else {
//            aapsLogger.error(LTag.PUMPCOMM, "No pump response during scan.");
        //Hardcoded frequency because medlink doesn't support frequency change
            return RileyLinkTargetFrequency.Medtronic_US.getScanFrequencies()[1];
//        }
    }


    private int calculateRssi(int rssiIn) {
        int rssiOffset = 73;
        int outRssi = 0;
        if (rssiIn >= 128) {
            outRssi = ((rssiIn - 256) / 2) - rssiOffset;
        } else {
            outRssi = (rssiIn / 2) - rssiOffset;
        }

        return outRssi;
    }


    private int tune_tryFrequency(double freqMHz, BaseCallback resultActivity) {
//        rfspy.setBaseFrequency(freqMHz);
        // RLMessage msg = makeRLMessage(RLMessageType.ReadSimpleData);
        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData);
        RadioPacket pkt = new RadioPacket(injector, pumpMsgContent);
//        RFSpyResponse resp = rfspy.transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, SCAN_TIMEOUT, (byte) 0, resultActivity);
//        if (resp.wasTimeout()) {
//            aapsLogger.warn(LTag.PUMPCOMM, "tune_tryFrequency: no pump response at frequency {}", freqMHz);
//        } else if (resp.looksLikeRadioPacket()) {
//            RadioResponse radioResponse = new RadioResponse(injector);
//            try {
//                radioResponse.init(resp.getRaw());
//
//                if (radioResponse.isValid()) {
//                    aapsLogger.warn(LTag.PUMPCOMM, "tune_tryFrequency: saw response level {} at frequency {}", radioResponse.rssi, freqMHz);
//                    return calculateRssi(radioResponse.rssi);
//                } else {
//                    aapsLogger.warn(LTag.PUMPCOMM, "tune_tryFrequency: invalid radio response:"
//                            + ByteUtil.shortHexString(radioResponse.getPayload()));
//                }
//
//            } catch (RileyLinkCommunicationException e) {
//                aapsLogger.warn(LTag.PUMPCOMM, "Failed to decode radio response: " + ByteUtil.shortHexString(resp.getRaw()));
//            }
//        }

        return 0;
    }


    @Override public double quickTuneForPump(double startFrequencyMHz) {
        double betterFrequency = startFrequencyMHz;
        wakeUp(false);
//        double stepsize = 0.05;
////        for (int tries = 0; tries < 4; tries++) {
//            double evenBetterFrequency = quickTunePumpStep(betterFrequency, stepsize);
//            if (evenBetterFrequency == 0.0) {
//                // could not see the pump at all.
//                // Try again at larger step size
//                stepsize += 0.05;
//            } else {
//                if ((int) (evenBetterFrequency * 100) != (int) (betterFrequency * 100)) {
//                    // value did not change, so we're done.
//                    betterFrequency = evenBetterFrequency; // and go again.
//                }
//            }
////        }
//        if (betterFrequency == 0.0) {
//            // we've failed... caller should try a full scan for pump
//            aapsLogger.error(LTag.PUMPCOMM, "quickTuneForPump: failed to find pump");
//        } else {
//            rfspy.setBaseFrequency(betterFrequency);
//            if (betterFrequency != startFrequencyMHz) {
//                aapsLogger.info(LTag.PUMPCOMM, "quickTuneForPump: new frequency is {}MHz", betterFrequency);
//            } else {
//                aapsLogger.info(LTag.PUMPCOMM, "quickTuneForPump: pump frequency is the same: {}MHz", startFrequencyMHz);
//            }
//        }
        return betterFrequency;
    }


    private double quickTunePumpStep(double startFrequencyMHz, double stepSizeMHz) {
        aapsLogger.info(LTag.PUMPCOMM, "Doing quick radio tune for receiver ({})", receiverDeviceID);
        wakeUp(false);
        BaseCallback resultActivity = new BaseStringAggregatorCallback();
        int startRssi = tune_tryFrequency(startFrequencyMHz, resultActivity);
        double lowerFrequency = startFrequencyMHz - stepSizeMHz;
        int lowerRssi = tune_tryFrequency(lowerFrequency, resultActivity);
        double higherFrequency = startFrequencyMHz + stepSizeMHz;
        int higherRssi = tune_tryFrequency(higherFrequency, resultActivity);

        if ((higherRssi == 0.0) && (lowerRssi == 0.0) && (startRssi == 0.0)) {
            // we can't see the pump at all...
            return 0.0;
        }
        if (higherRssi > startRssi) {
            // need to move higher
            return higherFrequency;
        } else if (lowerRssi > startRssi) {
            // need to move lower.
            return lowerFrequency;
        }
        return startFrequencyMHz;
    }


    protected void rememberLastGoodDeviceCommunicationTime() {
        lastGoodReceiverCommunicationTime = System.currentTimeMillis();

        sp.putLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, lastGoodReceiverCommunicationTime);
        if(getPumpStatus() != null) {
            getPumpStatus().setLastCommunicationToNow();
        }

    }


    private long getLastGoodReceiverCommunicationTime() {
        // If we have a value of zero, we need to load from prefs.
        if (lastGoodReceiverCommunicationTime == 0L) {
            lastGoodReceiverCommunicationTime = sp.getLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
            // Might still be zero, but that's fine.
        }
        double minutesAgo = (System.currentTimeMillis() - lastGoodReceiverCommunicationTime) / (1000.0 * 60.0);
        aapsLogger.debug(LTag.PUMPCOMM, "Last good pump communication was " + minutesAgo + " minutes ago.");
        return lastGoodReceiverCommunicationTime;
    }

    public void clearNotConnectedCount() {
        if (rfspy != null) {
            rfspy.notConnectedCount = 0;
        }
    }

    public abstract PumpStatus getPumpStatus();

    public abstract boolean isDeviceReachable();


    public abstract void setDoWakeUpBeforeCommand(boolean doWakeUp);

    public abstract boolean getBGHistory(MedLinkPumpMessage pumpMessage);

    public abstract boolean getBolusHistory();
}
