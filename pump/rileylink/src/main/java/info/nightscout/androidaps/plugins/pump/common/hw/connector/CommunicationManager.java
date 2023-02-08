package info.nightscout.androidaps.plugins.pump.common.hw.connector;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.pump.core.defs.PumpDeviceState;

/**
 * used by medlink
 */
public interface CommunicationManager {
    RLMessage createResponseMessage(byte[] payload);

    void setPumpDeviceState(PumpDeviceState pumpDeviceState);

    void wakeUp(boolean force);

    int getNotConnectedCount();

    boolean hasTunning();

    // FIXME change wakeup
    // TODO we might need to fix this. Maybe make pump awake for shorter time (battery factor for pump) - Andy
    void wakeUp(int duration_minutes, boolean force);

    void setRadioFrequencyForPump(double freqMHz);

    double tuneForDevice();

    boolean isValidFrequency(double frequency);

    /**
     * Do device connection, with wakeup
     *
     * @return
     */
    boolean tryToConnectToDevice();

    byte[] createPumpMessageContent(RLMessageType type);

    double quickTuneForPump(double startFrequencyMHz);
}
