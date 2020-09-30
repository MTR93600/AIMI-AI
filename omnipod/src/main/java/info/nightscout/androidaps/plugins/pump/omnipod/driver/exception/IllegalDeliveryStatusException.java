package info.nightscout.androidaps.plugins.pump.omnipod.driver.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;

public class IllegalDeliveryStatusException extends OmnipodException {
    private final DeliveryStatus expected;
    private final DeliveryStatus actual;

    public IllegalDeliveryStatusException(DeliveryStatus expected, DeliveryStatus actual) {
        super(String.format(Locale.getDefault(), "Illegal delivery status: %s, expected: %s", actual, expected), true);
        this.expected = expected;
        this.actual = actual;
    }

    public DeliveryStatus getExpected() {
        return expected;
    }

    public DeliveryStatus getActual() {
        return actual;
    }
}
