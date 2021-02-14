package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action;

import org.joda.time.Duration;

import java.util.List;
import java.util.Optional;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action.service.ExpirationReminderBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception.IllegalActivationProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class InsertCannulaAction implements OmnipodAction<Void> {

    private final PodStateManager podStateManager;
    private final BasalSchedule initialBasalSchedule;
    private final Duration expirationReminderTimeBeforeShutdown;
    private final Integer lowReservoirAlertUnits;

    public InsertCannulaAction(PodStateManager podStateManager, BasalSchedule initialBasalSchedule,
                               Duration expirationReminderTimeBeforeShutdown, Integer lowReservoirAlertUnits) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (initialBasalSchedule == null) {
            throw new IllegalArgumentException("Initial basal schedule cannot be null");
        }
        this.podStateManager = podStateManager;
        this.initialBasalSchedule = initialBasalSchedule;
        this.expirationReminderTimeBeforeShutdown = expirationReminderTimeBeforeShutdown;
        this.lowReservoirAlertUnits = lowReservoirAlertUnits;
    }

    @Override
    public Void execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (podStateManager.getActivationProgress().isBefore(ActivationProgress.PRIMING_COMPLETED)) {
            throw new IllegalActivationProgressException(ActivationProgress.PRIMING_COMPLETED, podStateManager.getActivationProgress());
        }

        if (podStateManager.getActivationProgress().needsBasalSchedule()) {
            podStateManager.setBasalSchedule(initialBasalSchedule);
            communicationService.executeAction(new SetBasalScheduleAction(podStateManager, initialBasalSchedule,
                    true, podStateManager.getScheduleOffset(), false));
            podStateManager.setActivationProgress(ActivationProgress.BASAL_INITIALIZED);
        }

        if (podStateManager.getActivationProgress().needsExpirationReminders()) {
            communicationService.executeAction(new ConfigureAlertsAction(podStateManager, buildAlertConfigurations()));

            podStateManager.setExpirationAlertTimeBeforeShutdown(expirationReminderTimeBeforeShutdown);
            podStateManager.setLowReservoirAlertUnits(lowReservoirAlertUnits);
            podStateManager.setActivationProgress(ActivationProgress.EXPIRATION_REMINDERS_SET);
        }

        if (podStateManager.getActivationProgress().needsCannulaInsertion()) {
            communicationService.executeAction(new BolusAction(podStateManager, OmnipodConstants.POD_CANNULA_INSERTION_BOLUS_UNITS,
                    Duration.standardSeconds(1), false, true));
            podStateManager.setActivationProgress(ActivationProgress.INSERTING_CANNULA);
        }

        return null;
    }

    private List<AlertConfiguration> buildAlertConfigurations() {
        ExpirationReminderBuilder builder = new ExpirationReminderBuilder(podStateManager).defaults();
        builder.expirationAdvisory(expirationReminderTimeBeforeShutdown != null,
                Optional.ofNullable(expirationReminderTimeBeforeShutdown).orElse(Duration.ZERO));
        builder.lowReservoir(lowReservoirAlertUnits != null, Optional.ofNullable(lowReservoirAlertUnits).orElse(0));
        return builder.build();
    }
}
