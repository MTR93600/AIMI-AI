package info.nightscout.pump.common.data;


import info.nightscout.interfaces.pump.defs.PumpType;

public class MedLinkPartialBolus extends PumpStatus {
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
                ", lastBolusTime=" + getLastBolusTime() +
                ", lastBolusAmount=" + getLastBolusAmount() +
                '}';
    }
}
