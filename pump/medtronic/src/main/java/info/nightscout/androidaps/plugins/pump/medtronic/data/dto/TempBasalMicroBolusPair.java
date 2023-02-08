package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.joda.time.LocalDateTime;

import java.util.Objects;

import kotlin.jvm.functions.Function1;


/**
 * used by medlink
 */
public class TempBasalMicroBolusPair extends TempBasalPair{

    private boolean commandIssued = false;

    private final Function1 callback;
    @Expose
    private final Integer interval = 0;
    @Expose
    private final Integer duration;
    @Expose
    private final Double dose;

    @Expose
    private final Double calculatedDose;
    @Expose
    private LocalDateTime releaseTime;
    @Expose
    private final OperationType operationType;

    public void delayInMinutes(int delay) {
        this.releaseTime = this.releaseTime.plusMinutes(delay);
    }

    public void setReleaseTime(int delay) {
        this.releaseTime = LocalDateTime.now().plusMinutes(delay);
    }
    public enum OperationType {
        BOLUS,
        SUSPEND,
        REACTIVATE
    }

    public TempBasalMicroBolusPair(Integer duration, double dose, double calculatedDose,
                                   LocalDateTime releaseTime, OperationType operationType,
                                   Function1 callback) {
        super(dose,false, duration);
        this.duration = duration;
        this.dose = dose;
        this.releaseTime = releaseTime;
        this.operationType = operationType;
        this.calculatedDose = calculatedDose;
        this.callback = callback;
    }

//    public TempBasalMicroBolusPair(Integer duration, Double dose, Double calculatedDose,
//                                   LocalDateTime releaseTime, OperationType operationType,
//                                   Function1 callback) {
//        this(duration, dose, calculatedDose, releaseTime,
//                operationType, callback);
//    }

    public Integer getInterval() {
        return interval;
    }

    public Integer getDuration() {
        return duration;
    }

    public Double getDose() {
        return dose;
    }

    public LocalDateTime getReleaseTime() {
        return releaseTime;
    }

    public Function1 getCallback() {
        return callback;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TempBasalMicroBolusPair that = (TempBasalMicroBolusPair) o;
        return Objects.equals(callback, that.callback) &&
                Objects.equals(interval, that.interval) &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(dose, that.dose) &&
                Objects.equals(calculatedDose, that.calculatedDose) &&
                Objects.equals(releaseTime, that.releaseTime) &&
                operationType == that.operationType;
    }

    @Override public int hashCode() {
        return Objects.hash(callback, interval, duration, dose, calculatedDose, releaseTime, operationType);
    }

    public Double getCalculatedDose() {
        return calculatedDose;
    }

    public Double getDelta() {
        return dose - calculatedDose;
    }

    @Override public String toString() {
        return "TempBasalMicroBolusPair{" +
                "callback=" + callback +
                ", interval=" + interval +
                ", duration=" + duration +
                ", dose=" + dose +
                ", calculatedDose=" + calculatedDose +
                ", releaseTime=" + releaseTime +
                ", operationType=" + operationType +
                '}';
    }

    public String toStringView() {
        String result = "";
        String release = releaseTime.toString("HH:mm");
        switch(operationType){
            case BOLUS: {
                result ="Bolus: " + dose +
                        "u, at=" + release;
            }
            break;
            case SUSPEND: {
                result = "Suspend: at=" + release;
            }
            break;
            case REACTIVATE: {
                result = "Activate: at=" + release;
            }
            break;

        }
        return result;
    }
    public OperationType getOperationType() {
        return operationType;
    }

    public TempBasalMicroBolusPair decreaseDosage(double toDecrease) {
        return new TempBasalMicroBolusPair(duration, dose -toDecrease, calculatedDose,
                releaseTime, operationType, callback);
    }

    public boolean isCommandIssued() {
        return commandIssued;
    }

    public void setCommandIssued(boolean commandIssued) {
        this.commandIssued = commandIssued;
    }
}
