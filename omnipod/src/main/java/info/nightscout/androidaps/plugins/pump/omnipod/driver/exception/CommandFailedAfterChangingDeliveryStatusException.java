package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

public class CommandFailedAfterChangingDeliveryStatusException extends OmnipodException {
    public CommandFailedAfterChangingDeliveryStatusException(String message, Throwable cause) {
        super(message, cause, false);
    }
}
