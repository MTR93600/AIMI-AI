package info.nightscout.androidaps.plugins.pump.common.hw.connector;

import android.content.Context;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import info.nightscout.androidaps.plugins.pump.common.hw.connector.data.HistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.BleAdvertisedData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;


/**
 * used by medlink
 */
public interface ConnectorUtil {
    static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }

    static BleAdvertisedData parseAdertisedData(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();
        String name = null;
        if (advertisedData == null) {
            return new BleAdvertisedData(uuids, name);
        }

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0)
                break;

            byte type = buffer.get();
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2) {
                        uuids
                                .add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                        length -= 2;
                    }
                    break;
                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        uuids.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;
                case 0x09:
                    byte[] nameBytes = new byte[length - 1];
                    buffer.get(nameBytes);
                    name = new String(nameBytes, StandardCharsets.UTF_8);
                    break;
                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }
        return new BleAdvertisedData(uuids, name);
    }

    CommunicatorEncodingType getEncoding();

    void setEncoding(CommunicatorEncodingType encoding);

    void sendBroadcastMessage(String message, Context context);

    // FIXME remove ?
    void setCurrentTask(ServiceTask task);

    void finishCurrentTask(ServiceTask task);

    List<? extends HistoryItem> getRileyLinkHistory();

    Encoding4b6b getEncoding4b6b();

    void setRileyLinkTargetFrequency(RileyLinkTargetFrequency rileyLinkTargetFrequency_);
}
