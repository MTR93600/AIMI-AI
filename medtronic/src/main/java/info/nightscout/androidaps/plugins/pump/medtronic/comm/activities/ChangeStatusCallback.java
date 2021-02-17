package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;

/**
 * Created by Dirceu on 19/01/21.
 */
public class ChangeStatusCallback extends BaseCallback<PumpDriverState> {
    private final OperationType type;

    public enum OperationType{
        START,
        STOP
    }

    public ChangeStatusCallback(OperationType type){
        this.type = type;
    }

    @Override public MedLinkStandardReturn<PumpDriverState> apply(Supplier<Stream<String>> a) {
        Stream<String> applied = a.get();
        Stream<String> filtered = applied.filter(f -> f.contains("pump") && f.contains("state"));
        Optional<PumpDriverState> result = filtered.findFirst().map(f -> {
            if (f.contains("normal")) {
                return PumpDriverState.Initialized;
            } else if (f.contains("suspended")) {
                return PumpDriverState.Suspended;
            } else return PumpDriverState.Busy;
        });

        return new MedLinkStandardReturn<>(a, result.orElse(PumpDriverState.Busy));
    }
}
