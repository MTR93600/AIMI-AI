package info.nightscout.androidaps.plugins.pump.common.data;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

public class MedLinkPartialBolus extends PumpStatus{
    public double bolusDeliveredAmount =0d;

    public MedLinkPartialBolus(PumpType pumpType) {
        super(pumpType);
    }

    @Override public void initSettings() {
    }

    @Override public String getErrorInfo() {
        return null;
    }

    @Override public String toString() {
        return "MedLinkPartialBolus{" +
                "bolusDeliveredAmount=" + bolusDeliveredAmount +
                ", lastBolusTime=" + lastBolusTime +
                ", lastBolusAmount=" + lastBolusAmount +
                '}';
    }
}
