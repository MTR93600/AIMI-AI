package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception;

public class NonceOutOfSyncException extends OmnipodException {
    public NonceOutOfSyncException() {
        super("Nonce out of sync", true);
    }
}
