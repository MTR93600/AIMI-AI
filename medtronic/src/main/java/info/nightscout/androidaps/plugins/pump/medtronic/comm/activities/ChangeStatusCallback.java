package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;


import info.nightscout.androidaps.extensions.PumpStateExtensionKt;
import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpRunningState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;

/**
 * Created by Dirceu on 19/01/21.
 */
public class ChangeStatusCallback extends BaseCallback<PumpDriverState, Supplier<Stream<String>>> {
    private final OperationType type;
    private final AAPSLogger aapsLogger;
    private final MedLinkMedtronicPumpPlugin medLinkMedtronicPumpPlugin;


    public enum OperationType {
        START,
        STOP
    }

    public ChangeStatusCallback(AAPSLogger aapsLogger,
                                OperationType type, MedLinkMedtronicPumpPlugin plugin) {
        this.aapsLogger = aapsLogger;
        this.type = type;
        this.medLinkMedtronicPumpPlugin = plugin;
    }

    @Override public MedLinkStandardReturn<PumpDriverState> apply(Supplier<Stream<String>> a) {
        aapsLogger.info(LTag.PUMPBTCOMM, "StartStop Function");
        Stream<String> applied = a.get();
        for (Object val : a.get().toArray()) {
            aapsLogger.info(LTag.PUMPBTCOMM, val.toString());
        }
        Stream<String> filtered =
                applied.filter(f -> !f.contains("switching") && f.contains("pump") && f.contains(
                        "state"));
        Optional<PumpDriverState> result = filtered.reduce((first, second) -> second).map(f -> {
            if (f.contains("normal")) {
                medLinkMedtronicPumpPlugin.getPumpStatusData().setPumpRunningState(PumpRunningState.Running);
                medLinkMedtronicPumpPlugin.storeCancelTempBasal();
                if (type == OperationType.START) {

                }
                return PumpDriverState.Initialized;
            } else if (f.contains("suspend")) {
                medLinkMedtronicPumpPlugin.getPumpStatusData().setPumpRunningState(PumpRunningState.Suspended);
                PumpSync.PumpState.TemporaryBasal tempBasalData = medLinkMedtronicPumpPlugin.getTemporaryBasal();
                if (tempBasalData != null && PumpStateExtensionKt.getDurationInMinutes(tempBasalData) > 0) {
                    medLinkMedtronicPumpPlugin.createTemporaryBasalData(PumpStateExtensionKt.getDurationInMinutes(tempBasalData),
                            0);

                } else {
                    medLinkMedtronicPumpPlugin.createTemporaryBasalData(30, 0);
                }
                return PumpDriverState.Suspended;

            } else {
                medLinkMedtronicPumpPlugin.changeStatusTime(System.currentTimeMillis());
                return PumpDriverState.Busy;
            }
        });
        return new MedLinkStandardReturn<>(a, result.orElse(PumpDriverState.Busy));
    }
}
