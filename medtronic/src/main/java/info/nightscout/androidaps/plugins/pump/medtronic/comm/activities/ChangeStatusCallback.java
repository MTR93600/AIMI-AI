package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.db.MedLinkTemporaryBasal;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicroBolusPair;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;

/**
 * Created by Dirceu on 19/01/21.
 */
public class ChangeStatusCallback extends BaseCallback<PumpDriverState,Supplier<Stream<String>>> {
    private final OperationType type;
    private final AAPSLogger aapsLogger;
    private final MedLinkMedtronicPumpPlugin medlinkPumpPlugin;


    public enum OperationType{
        START,
        STOP
    }

    public ChangeStatusCallback(AAPSLogger aapsLogger,
                                OperationType type, MedLinkMedtronicPumpPlugin plugin){
        this.aapsLogger = aapsLogger;
        this.type = type;
        this.medlinkPumpPlugin = plugin;
    }

    @Override public MedLinkStandardReturn<PumpDriverState> apply(Supplier<Stream<String>> a) {
        Stream<String> applied = a.get();
        for (Object val: a.get().toArray()) {
            System.out.println(val.toString());
        }
        Stream<String> filtered = applied.filter(f -> f.contains("pump") && f.contains("state"));
        Optional<PumpDriverState> result = filtered.reduce((first, second) -> second).map(f -> {
            if (f.contains("normal")) {
                medlinkPumpPlugin.getPumpStatusData().pumpStatusType = PumpStatusType.Running;
                medlinkPumpPlugin.createTemporaryBasalData(0,0);
                return PumpDriverState.Initialized;
            } else if (f.contains("suspend")) {
                medlinkPumpPlugin.getPumpStatusData().pumpStatusType = PumpStatusType.Suspended;
                MedLinkTemporaryBasal tempBasalData = medlinkPumpPlugin.getTemporaryBasal();
                if( tempBasalData!= null) {
                    medlinkPumpPlugin.createTemporaryBasalData(tempBasalData.durationInMinutes, 0);
                }else{
                    medlinkPumpPlugin.createTemporaryBasalData(30, 0);
                }
                return PumpDriverState.Suspended;

            } else {
                return PumpDriverState.Busy;
            }
        });

        return new MedLinkStandardReturn<>(a, result.orElse(PumpDriverState.Busy));
    }
}
