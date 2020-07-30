package info.nightscout.androidaps.plugins.pump.omnipod.defs;

public enum PodProgressStatus {
    INITIALIZED((byte) 0x00),
    MEMORY_INITIALIZED((byte) 0x01),
    REMINDER_INITIALIZED((byte) 0x02),
    PAIRING_COMPLETED((byte) 0x03),
    PRIMING((byte) 0x04),
    PRIMING_COMPLETED((byte) 0x05),
    BASAL_INITIALIZED((byte) 0x06),
    INSERTING_CANNULA((byte) 0x07),
    ABOVE_FIFTY_UNITS((byte) 0x08),
    FIFTY_OR_LESS_UNITS((byte) 0x09),
    ONE_NOT_USED((byte) 0x0a),
    TWO_NOT_USED((byte) 0x0b),
    THREE_NOT_USED((byte) 0x0c),
    FAULT_EVENT_OCCURRED((byte) 0x0d), // Fault event occurred (a "screamer")
    ACTIVATION_TIME_EXCEEDED((byte) 0x0e), // Took > 2 hours from progress 2 to 3 or > 1 hour from 3 to 8
    INACTIVE((byte) 0x0f); // Pod deactivated or a fatal packet state error

    private byte value;

    PodProgressStatus(byte value) {
        this.value = value;
    }

    public static PodProgressStatus fromByte(byte value) {
        for (PodProgressStatus type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PodProgressStatus: " + value);
    }

    public byte getValue() {
        return value;
    }

    public boolean isReadyForDelivery() {
        return this == ABOVE_FIFTY_UNITS || this == FIFTY_OR_LESS_UNITS;
    }
}
