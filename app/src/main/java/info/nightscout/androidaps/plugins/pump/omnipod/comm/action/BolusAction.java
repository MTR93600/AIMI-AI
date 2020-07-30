package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.Duration;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.BolusExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BolusDeliverySchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;

public class BolusAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final double units;
    private final Duration timeBetweenPulses;
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;

    public BolusAction(PodSessionState podState, double units, Duration timeBetweenPulses,
                       boolean acknowledgementBeep, boolean completionBeep) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (timeBetweenPulses == null) {
            throw new ActionInitializationException("Time between pulses cannot be null");
        }
        this.podState = podState;
        this.units = units;
        this.timeBetweenPulses = timeBetweenPulses;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
    }

    public BolusAction(PodSessionState podState, double units, boolean acknowledgementBeep, boolean completionBeep) {
        this(podState, units, Duration.standardSeconds(2), acknowledgementBeep, completionBeep);
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        BolusDeliverySchedule bolusDeliverySchedule = new BolusDeliverySchedule(units, timeBetweenPulses);
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(
                podState.getCurrentNonce(), bolusDeliverySchedule);
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(units, timeBetweenPulses,
                acknowledgementBeep, completionBeep);
        OmnipodMessage primeBolusMessage = new OmnipodMessage(podState.getAddress(),
                Arrays.asList(setInsulinScheduleCommand, bolusExtraCommand), podState.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podState, primeBolusMessage);
    }
}
