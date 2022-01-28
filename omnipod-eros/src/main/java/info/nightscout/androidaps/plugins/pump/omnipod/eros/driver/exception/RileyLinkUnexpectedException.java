package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception;

public class RileyLinkUnexpectedException extends OmnipodException {
    public RileyLinkUnexpectedException(Throwable cause) {
        super("Unexpected Exception during RileyLink communication", cause, false);
    }
}
