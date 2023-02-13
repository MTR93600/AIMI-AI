package info.nightscout.androidaps.plugins.pump.common.hw.medlink

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.connector.CommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.connector.ConnectorUtil.isSame
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseStringAggregatorCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.FrequencyScanResults
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.pump.common.data.PumpStatus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

/**
 * This is abstract class for MedLink Communication, this one needs to be extended by specific "Pump" class.
 *
 *
 * Created by Dirceu on 18/09/20.
 * Copied from RileyLinkCommunicationManager
 */
abstract class MedLinkCommunicationManager(val injector: HasAndroidInjector, rfspy: MedLinkRFSpy, val aapsLogger: AAPSLogger, val medLinkServiceData: MedLinkServiceData) : CommunicationManager {

    // @JvmField @Inject
    // ? = null

    @JvmField @Inject
    var sp: SP? = null


    @JvmField @Inject
    var serviceTaskExecutor: ServiceTaskExecutor? = null
    private val SCAN_TIMEOUT = 1500
    private val ALLOWED_PUMP_UNREACHABLE = 10 * 60 * 1000 // 10 minutes
    protected val rfspy: MedLinkRFSpy
    protected var receiverDeviceAwakeForMinutes = 1 // override this in constructor of specific implementation
    protected var receiverDeviceID // String representation of receiver device (ex. Pump (xxxxxx) or Pod (yyyyyy))
        : String? = null
    protected var lastGoodReceiverCommunicationTime: Long = 0L

    //    protected PumpStatus pumpStatus;
    private val nextWakeUpRequired = 0L
    private val timeoutCount = 0

    // All pump communications go through this function.
    @Throws(RileyLinkCommunicationException::class) protected open fun <B,C>sendAndListen(msg: MedLinkPumpMessage<B, C>, timeout_ms: Int) {
        sendAndListen(msg, timeout_ms, null)
    }

    @Throws(RileyLinkCommunicationException::class) private fun <B,C>sendAndListen(msg: MedLinkPumpMessage<B, C>, timeout_ms: Int, extendPreamble_ms: Int?) {
        sendAndListen(msg, timeout_ms, 0, extendPreamble_ms)
    }

    // For backward compatibility
    @Throws(RileyLinkCommunicationException::class) private fun <B,C>sendAndListen(msg: MedLinkPumpMessage<B, C>, timeout_ms: Int, repeatCount: Int, extendPreamble_ms: Int?) {
        sendAndListen(msg, timeout_ms, repeatCount, 0, extendPreamble_ms)
    }

    @Throws(RileyLinkCommunicationException::class) protected fun  <B,C>sendAndListen(
        msg: MedLinkPumpMessage<B, C>,
        timeout_ms: Int,
        repeatCount: Int, retryCount: Int, extendPreamble_ms: Int?
    ){

        // internal flag
        if (msg != null) {
            aapsLogger!!.info(LTag.PUMPCOMM, "Sent:" + msg.firstCommand().code)
        }
        rfspy!!.transmitThenReceive(msg)
    }

    override fun wakeUp(force: Boolean) {
        wakeUp(receiverDeviceAwakeForMinutes, force)
    }

    override fun getNotConnectedCount(): Int {
        return rfspy?.notConnectedCount ?: 0
    }

    override fun hasTunning(): Boolean {
        return true
    }

    // FIXME change wakeup
    // TODO we might need to fix this. Maybe make pump awake for shorter time (battery factor for pump) - Andy
    //    public  <B> void wakeUp(int duration_minutes, boolean force, Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>  resultActivity) {
    //        // If it has been longer than n minutes, do wakeup. Otherwise assume pump is still awake.
    //        // **** FIXME: this wakeup doesn't seem to work well... must revisit
    //        // receiverDeviceAwakeForMinutes = duration_minutes;
    //
    //        setPumpDeviceState(PumpDeviceState.WakingUp);
    //
    //        if (force)
    //            nextWakeUpRequired = 0L;
    //
    //        if (System.currentTimeMillis() > nextWakeUpRequired) {
    //            aapsLogger.info(LTag.PUMPCOMM, "Waking pump...");
    //
    ////            byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
    //
    //            rfspy.transmitThenReceive(new MedLinkPumpMessage<>(MedLinkCommandType.GetState,
    //                    MedLinkCommandType.NoCommand,
    //                    resultActivity,
    //                    medLinkServiceData,
    //                    aapsLogger));
    ////            aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
    //
    //            // FIXME wakeUp successful !!!!!!!!!!!!!!!!!!
    //
    //            nextWakeUpRequired = System.currentTimeMillis() + (receiverDeviceAwakeForMinutes * 60 * 1000);
    //        } else {
    //            aapsLogger.debug(LTag.PUMPCOMM, "Last pump communication was recent, not waking pump.");
    //        }
    //
    //        // long lastGoodPlus = getLastGoodReceiverCommunicationTime() + (receiverDeviceAwakeForMinutes * 60 * 1000);
    //        //
    //        // if (System.currentTimeMillis() > lastGoodPlus || force) {
    //        // LOG.info("Waking pump...");
    //        //
    //        // byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.PowerOn);
    //        // RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(pumpMsgContent), (byte) 0, (byte) 200, (byte)
    //        // 0, (byte) 0, 15000, (byte) 0);
    //        // LOG.info("wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
    //        // } else {
    //        // LOG.trace("Last pump communication was recent, not waking pump.");
    //        // }
    //    }
    override fun setRadioFrequencyForPump(freqMHz: Double) {
//        rfspy.setBaseFrequency(freqMHz);
    }

    override fun tuneForDevice(): Double {
        return scanForDevice(medLinkServiceData!!.rileyLinkTargetFrequency.scanFrequencies)
    }

    /**
     * If user changes pump and one pump is running in US freq, and other in WW, then previously set frequency would be
     * invalid,
     * so we would need to retune. This checks that saved frequency is correct range.
     *
     * @param frequency
     * @return
     */
    override fun isValidFrequency(frequency: Double): Boolean {
        val scanFrequencies = medLinkServiceData!!.rileyLinkTargetFrequency.scanFrequencies
        return if (scanFrequencies.size == 1) {
            isSame(scanFrequencies[0], frequency)
        } else {
            scanFrequencies[0] <= frequency && scanFrequencies[scanFrequencies.size - 1] >= frequency
        }
    }

    private fun scanForDevice(frequencies: DoubleArray): Double {
        aapsLogger!!.info(LTag.PUMPCOMM, "Scanning for receiver ({})", receiverDeviceID)
        wakeUp(receiverDeviceAwakeForMinutes, false)
        val results = FrequencyScanResults()

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
        return RileyLinkTargetFrequency.MedtronicUS.scanFrequencies[1]
        //        }
    }

    private fun calculateRssi(rssiIn: Int): Int {
        val rssiOffset = 73
        var outRssi = 0
        outRssi = if (rssiIn >= 128) {
            (rssiIn - 256) / 2 - rssiOffset
        } else {
            rssiIn / 2 - rssiOffset
        }
        return outRssi
    }

    private fun tune_tryFrequency(freqMHz: Double, resultActivity: BaseCallback<*, *>): Int {
//        rfspy.setBaseFrequency(freqMHz);
        // RLMessage msg = makeRLMessage(RLMessageType.ReadSimpleData);
        val pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData)
        val pkt = RadioPacket(injector, pumpMsgContent)
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
        return 0
    }

    override fun quickTuneForPump(startFrequencyMHz: Double): Double {
        aapsLogger!!.info(LTag.PUMPBTCOMM, "quicktune")
        wakeUp(false)
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
        return startFrequencyMHz
    }

    private fun quickTunePumpStep(startFrequencyMHz: Double, stepSizeMHz: Double): Double {
        aapsLogger!!.info(LTag.PUMPCOMM, "Doing quick radio tune for receiver ({})", receiverDeviceID)
        wakeUp(false)
        val resultActivity: BaseCallback<*, *> = BaseStringAggregatorCallback()
        val startRssi = tune_tryFrequency(startFrequencyMHz, resultActivity)
        val lowerFrequency = startFrequencyMHz - stepSizeMHz
        val lowerRssi = tune_tryFrequency(lowerFrequency, resultActivity)
        val higherFrequency = startFrequencyMHz + stepSizeMHz
        val higherRssi = tune_tryFrequency(higherFrequency, resultActivity)
        if (higherRssi.toDouble() == 0.0 && lowerRssi.toDouble() == 0.0 && startRssi.toDouble() == 0.0) {
            // we can't see the pump at all...
            return 0.0
        }
        if (higherRssi > startRssi) {
            // need to move higher
            return higherFrequency
        } else if (lowerRssi > startRssi) {
            // need to move lower.
            return lowerFrequency
        }
        return startFrequencyMHz
    }

    protected fun rememberLastGoodDeviceCommunicationTime() {
        lastGoodReceiverCommunicationTime = System.currentTimeMillis()
        sp!!.putLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, lastGoodReceiverCommunicationTime)
        if (pumpStatus != null) {
            pumpStatus!!.setLastCommunicationToNow()
        }
    }

    // private fun getLastGoodReceiverCommunicationTime(): Long {
    //     // If we have a value of zero, we need to load from prefs.
    //     if (lastGoodReceiverCommunicationTime == 0L) {
    //         lastGoodReceiverCommunicationTime = sp!!.getLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L)
    //         // Might still be zero, but that's fine.
    //     }
    //     val minutesAgo = (System.currentTimeMillis() - lastGoodReceiverCommunicationTime) / (1000.0 * 60.0)
    //     aapsLogger!!.debug(LTag.PUMPCOMM, "Last good pump communication was $minutesAgo minutes ago.")
    //     return lastGoodReceiverCommunicationTime
    // }

    fun clearNotConnectedCount() {
        if (rfspy != null) {
            rfspy.notConnectedCount = 0
        }
    }

    abstract val pumpStatus: PumpStatus?
    abstract val isDeviceReachable: Boolean
    abstract fun setDoWakeUpBeforeCommand(doWakeUp: Boolean)
    abstract fun getBGHistory(pumpMessage: MedLinkPumpMessage<Any, Any>): Boolean
    abstract val bolusHistory: Boolean

    init {
        injector.androidInjector().inject(this)
        this.rfspy = rfspy
    }
}