package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

public class NotEnoughDataException extends OmnipodException {
    private final byte[] data;

    public NotEnoughDataException(byte[] data) {
        super("Not enough data: " + ByteUtil.shortHexString(data), false);
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
