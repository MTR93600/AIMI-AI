package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;

import org.jetbrains.annotations.NotNull;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.queue.Callback;
import kotlin.jvm.functions.Function1;


/**
 * Created by Dirceu on 30/09/20.
 */
public interface MedLinkPumpDevice extends CommunicatorPumpDevice {


    void setBusy(boolean busy);

    void triggerPumpConfigurationChangedEvent();

    MedLinkService getRileyLinkService();

    MedLinkService getMedLinkService();

    RileyLinkPumpInfo getPumpInfo();

    long getLastConnectionTimeMillis();

    void setLastCommunicationToNow();

    void deliverTreatment(@NotNull DetailedBolusInfo detailedBolusInfo, @NotNull Function1 func);

    PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew, Function1 func);

    PumpEnactResult setTempBasalAbsolute(Double absoluteRate,
                                                         Integer durationInMinutes,
                                                         Profile profile,
                                                         boolean enforceNew,
                                                         Function1 callback);
    void cancelTempBasal(Boolean enforceNew, Callback callback);

    PumpEnactResult extendBasalTreatment(int duration, Function1 callback);

//    void deliverTreatment(DetailedBolusInfo detailedBolusInfo,
//                          @NotNull Function<PumpEnactResult, Unit> func);
}