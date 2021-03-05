package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.NonceResyncableMessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalDeliverySchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalDeliveryTable;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BolusDeliverySchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.DeliverySchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.TempBasalDeliverySchedule;

public class SetInsulinScheduleCommand extends NonceResyncableMessageBlock {

    private final DeliverySchedule schedule;
    private int nonce;

    // Bolus
    public SetInsulinScheduleCommand(int nonce, BolusDeliverySchedule schedule) {
        this.nonce = nonce;
        this.schedule = schedule;
        encode();
    }

    // Basal schedule
    public SetInsulinScheduleCommand(int nonce, BasalSchedule schedule, Duration scheduleOffset) {
        int scheduleOffsetInSeconds = (int) scheduleOffset.getStandardSeconds();

        BasalDeliveryTable table = new BasalDeliveryTable(schedule);
        double rate = schedule.rateAt(scheduleOffset);
        byte segment = (byte) (scheduleOffsetInSeconds / BasalDeliveryTable.SEGMENT_DURATION);
        int segmentOffset = scheduleOffsetInSeconds % BasalDeliveryTable.SEGMENT_DURATION;

        int timeRemainingInSegment = BasalDeliveryTable.SEGMENT_DURATION - segmentOffset;

        double timeBetweenPulses = 3600 / (rate / OmnipodConstants.POD_PULSE_SIZE);

        double offsetToNextTenth = timeRemainingInSegment % (timeBetweenPulses / 10.0);

        int pulsesRemainingInSegment = (int) ((timeRemainingInSegment + timeBetweenPulses / 10.0 - offsetToNextTenth) / timeBetweenPulses);

        this.nonce = nonce;
        this.schedule = new BasalDeliverySchedule(segment, timeRemainingInSegment, pulsesRemainingInSegment, table);
        encode();
    }

    // Temp basal
    public SetInsulinScheduleCommand(int nonce, double tempBasalRate, Duration duration) {
        if (tempBasalRate < 0D) {
            throw new IllegalArgumentException("Rate should be >= 0");
        } else if (tempBasalRate > OmnipodConstants.MAX_BASAL_RATE) {
            throw new IllegalArgumentException("Rate exceeds max basal rate");
        }
        if (duration.isLongerThan(OmnipodConstants.MAX_TEMP_BASAL_DURATION)) {
            throw new IllegalArgumentException("Duration exceeds max temp basal duration");
        }
        int pulsesPerHour = (int) Math.round(tempBasalRate / OmnipodConstants.POD_PULSE_SIZE);
        int pulsesPerSegment = pulsesPerHour / 2;
        this.nonce = nonce;
        this.schedule = new TempBasalDeliverySchedule(BasalDeliveryTable.SEGMENT_DURATION, pulsesPerSegment, new BasalDeliveryTable(tempBasalRate, duration));
        encode();
    }

    private void encode() {
        encodedData = ByteUtil.getBytesFromInt(nonce);
        encodedData = ByteUtil.concat(encodedData, schedule.getType().getValue());
        encodedData = ByteUtil.concat(encodedData, ByteUtil.getBytesFromInt16(schedule.getChecksum()));
        encodedData = ByteUtil.concat(encodedData, schedule.getRawData());
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.SET_INSULIN_SCHEDULE;
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public void setNonce(int nonce) {
        this.nonce = nonce;
        encode();
    }

    @Override
    public String toString() {
        return "SetInsulinScheduleCommand{" +
                "schedule=" + schedule +
                ", nonce=" + nonce +
                '}';
    }
}
