package info.nightscout.androidaps.interfaces;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.TimeChangeType;

/**
 * Created by mike on 04.06.2016.
 */
public interface PumpInterface {

    boolean isInitialized(); // true if pump status has been read and is ready to accept commands

    boolean isSuspended();   // true if suspended (not delivering insulin)

    boolean isBusy();        // if true pump is not ready to accept commands right now

    boolean isConnected();   // true if BT connection is established

    boolean isConnecting();  // true if BT connection is in progress

    boolean isHandshakeInProgress(); // true if BT is connected but initial handshake is still in progress

    void finishHandshaking(); // set initial handshake completed

    void connect(String reason);

    void disconnect(String reason);

    void stopConnecting();

    void getPumpStatus();

    // Upload to pump new basal profile
    @NotNull
    PumpEnactResult setNewBasalProfile(Profile profile);

    boolean isThisProfileSet(Profile profile);

    long lastDataTime();

    double getBaseBasalRate(); // base basal rate, not temp basal

    double getReservoirLevel();

    int getBatteryLevel();  // in percent as integer

    @NotNull
    PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo);

    void stopBolusDelivering();

    @NotNull
    PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew);

    @NotNull
    PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew);

    @NotNull
    PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes);

    //some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    //when the cancel request is requested by the user (forced), the pump should always do a real cancel
    @NotNull
    PumpEnactResult cancelTempBasal(boolean enforceNew);

    @NotNull
    PumpEnactResult cancelExtendedBolus();

    // Status to be passed to NS
    @NotNull
    JSONObject getJSONStatus(Profile profile, String profileName, String version);

    @NotNull
    ManufacturerType manufacturer();

    @NotNull
    PumpType model();

    @NotNull
    String serialNumber();

    // Pump capabilities
    @NotNull
    PumpDescription getPumpDescription();

    // Short info for SMS, Wear etc
    @NotNull
    String shortStatus(boolean veryShort);

    boolean isFakingTempsByExtendedBoluses();

    @NotNull
    PumpEnactResult loadTDDs();

    boolean canHandleDST();

    List<CustomAction> getCustomActions();

    void executeCustomAction(CustomActionType customActionType);

    /**
     * This method will be called when time or Timezone changes, and pump driver can then do a specific action (for
     * example update clock on pump).
     */
    void timezoneOrDSTChanged(TimeChangeType timeChangeType);

    /* Only used for pump types where hasCustomUnreachableAlertCheck=true */
    default boolean isUnreachableAlertTimeoutExceeded(long alertTimeoutMilliseconds) {
        return false;
    }

    default boolean setNeutralTempAtFullHour() {
        return false;
    }
}
