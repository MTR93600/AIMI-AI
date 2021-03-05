package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.BeepConfigCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepConfigType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class ConfigureBeepAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final BeepConfigType beepType;
    private final boolean basalCompletionBeep;
    private final Duration basalIntervalBeep;
    private final boolean tempBasalCompletionBeep;
    private final Duration tempBasalIntervalBeep;
    private final boolean bolusCompletionBeep;
    private final Duration bolusIntervalBeep;

    public ConfigureBeepAction(PodStateManager podState, BeepConfigType beepType, boolean basalCompletionBeep, Duration basalIntervalBeep, boolean tempBasalCompletionBeep, Duration tempBasalIntervalBeep, boolean bolusCompletionBeep, Duration bolusIntervalBeep) {
        if (podState == null || beepType == null) {
            throw new IllegalArgumentException("Required parameter(s) missing");
        }

        this.beepType = beepType;
        this.basalCompletionBeep = basalCompletionBeep;
        this.basalIntervalBeep = basalIntervalBeep;
        this.tempBasalCompletionBeep = tempBasalCompletionBeep;
        this.tempBasalIntervalBeep = tempBasalIntervalBeep;
        this.bolusCompletionBeep = bolusCompletionBeep;
        this.bolusIntervalBeep = bolusIntervalBeep;
        this.podStateManager = podState;
    }

    public ConfigureBeepAction(PodStateManager podState, BeepConfigType beepType) {
        this(podState, beepType, false, Duration.ZERO, false, Duration.ZERO, false, Duration.ZERO);
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        return communicationService.sendCommand(
                StatusResponse.class, podStateManager,
                new BeepConfigCommand(beepType, basalCompletionBeep, basalIntervalBeep,
                        tempBasalCompletionBeep, tempBasalIntervalBeep,
                        bolusCompletionBeep, bolusIntervalBeep));
    }
}
