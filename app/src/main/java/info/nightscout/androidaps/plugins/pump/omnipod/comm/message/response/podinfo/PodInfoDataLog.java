package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

public class PodInfoDataLog extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 8;
    private final FaultEventCode faultEventCode;
    private final Duration timeFaultEvent;
    private final Duration timeSinceActivation;
    private final byte dataChunkSize;
    private final byte maximumNumberOfDwords;
    private final List<byte[]> dwords;

    public PodInfoDataLog(byte[] encodedData, int bodyLength) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        faultEventCode = FaultEventCode.fromByte(encodedData[1]);
        timeFaultEvent = Duration.standardMinutes(ByteUtil.toInt(encodedData[2], encodedData[3]));
        timeSinceActivation = Duration.standardMinutes(ByteUtil.toInt(encodedData[4], encodedData[5]));
        dataChunkSize = encodedData[6];
        maximumNumberOfDwords = encodedData[7];

        dwords = new ArrayList<>();

        int numberOfDwords = (bodyLength - 8) / 4;
        for (int i = 0; i < numberOfDwords; i++) {
            dwords.add(ByteUtil.substring(encodedData, 8 + (4 * i), 4));
        }
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.DATA_LOG;
    }

    public FaultEventCode getFaultEventCode() {
        return faultEventCode;
    }

    public Duration getTimeFaultEvent() {
        return timeFaultEvent;
    }

    public Duration getTimeSinceActivation() {
        return timeSinceActivation;
    }

    public byte getDataChunkSize() {
        return dataChunkSize;
    }

    public byte getMaximumNumberOfDwords() {
        return maximumNumberOfDwords;
    }

    public List<byte[]> getDwords() {
        return Collections.unmodifiableList(dwords);
    }

    @Override
    public String toString() {
        return "PodInfoDataLog{" +
                "faultEventCode=" + faultEventCode +
                ", timeFaultEvent=" + timeFaultEvent +
                ", timeSinceActivation=" + timeSinceActivation +
                ", dataChunkSize=" + dataChunkSize +
                ", maximumNumberOfDwords=" + maximumNumberOfDwords +
                ", dwords=" + dwords +
                '}';
    }
}
