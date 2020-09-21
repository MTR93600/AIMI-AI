package info.nightscout.androidaps.plugins.pump.omnipod.driver.definition;

import org.joda.time.Duration;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodConstants {
    public static final double POD_PULSE_SIZE = 0.05;
    public static final double POD_BOLUS_DELIVERY_RATE = 0.025; // units per second
    public static final double POD_PRIMING_DELIVERY_RATE = 0.05; // units per second
    public static final double POD_CANNULA_INSERTION_DELIVERY_RATE = 0.05; // units per second
    public static final double MAX_RESERVOIR_READING = 50.0;
    public static final double MAX_BOLUS = 30.0;
    public static final double MAX_BASAL_RATE = 30.0;
    public static final Duration MAX_TEMP_BASAL_DURATION = Duration.standardHours(12);
    public static final int DEFAULT_ADDRESS = 0xffffffff;

    public static final Duration AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION = Duration.millis(1500);
    public static final Duration AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION = Duration.millis(1500);

    public static final Duration SERVICE_DURATION = Duration.standardHours(80);
    public static final Duration EXPIRATION_ADVISORY_WINDOW = Duration.standardHours(9);
    public static final Duration END_OF_SERVICE_IMMINENT_WINDOW = Duration.standardHours(1);
    public static final Duration NOMINAL_POD_LIFE = Duration.standardHours(72);
    public static final double LOW_RESERVOIR_ALERT = 20.0;

    public static final double POD_PRIME_BOLUS_UNITS = 2.6;
    public static final double POD_CANNULA_INSERTION_BOLUS_UNITS = 0.5;
    public static final double POD_SETUP_UNITS = POD_PRIME_BOLUS_UNITS + POD_CANNULA_INSERTION_BOLUS_UNITS;
}
