package info.nightscout.androidaps.plugins.pump.common.hw.medlink;

public enum PumpResponses {
    StatusFullProcessed("Status complete parse success"),
    StatusPartialProcessed("Status partial parse success"),
    CommandProcessed("ComandProcessed"),
    DeliveringBolus("DeliveringBolus"),
    BolusDelivered("BolusDelivered"),
    UnknownAnswer("UnknowAnswer: "), StatusProcessFailed("Status Process Failed: ");

    private final String answer;

    PumpResponses(String bolusDelivered) {
        answer = bolusDelivered;
    }

    public String getAnswer() {
        return answer;
    }
}
