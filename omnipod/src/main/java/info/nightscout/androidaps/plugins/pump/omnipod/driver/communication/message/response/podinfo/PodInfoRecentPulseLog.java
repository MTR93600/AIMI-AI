package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;

public class PodInfoRecentPulseLog extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 3;

    private final ArrayList<byte[]> dwords;

    private final int lastEntryIndex;

    public PodInfoRecentPulseLog(byte[] encodedData, int bodyLength) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        lastEntryIndex = ByteUtil.toInt(encodedData[1], encodedData[2]);
        dwords = new ArrayList<>();

        int numberOfDwords = (bodyLength - 3) / 4;

        for (int i = 0; numberOfDwords > i; i++) {
            byte[] dword = ByteUtil.substring(encodedData, 3 + (4 * i), 4);
            dwords.add(dword);
        }
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.RECENT_PULSE_LOG;
    }

    public List<byte[]> getDwords() {
        return Collections.unmodifiableList(dwords);
    }

    public int getLastEntryIndex() {
        return lastEntryIndex;
    }

    @Override
    public String toString() {
        String out = "PodInfoRecentPulseLog{" +
                "lastEntryIndex=" + lastEntryIndex +
                ",dwords=[";

        List<String> hexDwords = new ArrayList<>();
        for (byte[] dword : dwords) {
            hexDwords.add(ByteUtil.shortHexStringWithoutSpaces(dword));
        }
        out += TextUtils.join(", ", hexDwords);
        out += "]}";
        return out;
    }
}
