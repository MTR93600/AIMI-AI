package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import org.joda.time.Duration;

public class AlertConfigurationFactory {
    public static AlertConfiguration createLowReservoirAlertConfiguration(Double units) {
        return new AlertConfiguration(AlertType.LOW_RESERVOIR_ALERT, AlertSlot.SLOT4, true, false, Duration.ZERO,
                new UnitsRemainingAlertTrigger(units), BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, BeepRepeat.EVERY_MINUTE_FOR_3_MINUTES_REPEAT_EVERY_60_MINUTES);
    }

    public static AlertConfiguration createExpirationAdvisoryAlertConfiguration(Duration timeUntilAlert, Duration duration) {
        return new AlertConfiguration(AlertType.EXPIRATION_ADVISORY_ALERT, AlertSlot.SLOT7, true, false, duration,
                new TimerAlertTrigger(timeUntilAlert), BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, BeepRepeat.EVERY_MINUTE_FOR_3_MINUTES_REPEAT_EVERY_15_MINUTES);
    }

    public static AlertConfiguration createShutdownImminentAlertConfiguration(Duration timeUntilAlert) {
        return new AlertConfiguration(AlertType.SHUTDOWN_IMMINENT_ALARM, AlertSlot.SLOT2, true, false, Duration.ZERO,
                new TimerAlertTrigger(timeUntilAlert), BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, BeepRepeat.EVERY_15_MINUTES);
    }

    public static AlertConfiguration createAutoOffAlertConfiguration(boolean active, Duration countdownDuration) {
        return new AlertConfiguration(AlertType.AUTO_OFF_ALARM, AlertSlot.SLOT0, active, true,
                Duration.standardMinutes(15), new TimerAlertTrigger(countdownDuration),
                BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, BeepRepeat.EVERY_MINUTE_FOR_15_MINUTES);
    }

    public static AlertConfiguration createFinishSetupReminderAlertConfiguration() {
        return new AlertConfiguration(AlertType.FINISH_SETUP_REMINDER, AlertSlot.SLOT7, true, false,
                Duration.standardMinutes(55), new TimerAlertTrigger(Duration.standardMinutes(5)),
                BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, BeepRepeat.EVERY_5_MINUTES);
    }
}
