package info.nightscout.androidaps.plugins.pump.insight.exceptions;

public class ConnectionFailedException extends InsightException {

    private final long durationOfConnectionAttempt;

    public ConnectionFailedException(long durationOfConnectionAttempt) {
        this.durationOfConnectionAttempt = durationOfConnectionAttempt;
    }

    public long getDurationOfConnectionAttempt() {
        return durationOfConnectionAttempt;
    }
}
