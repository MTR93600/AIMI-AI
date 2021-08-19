package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.jetbrains.annotations.Nullable;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import kotlin.jvm.functions.Function1;

public class TempBasalMicroBolusPair {

    private boolean commandIssued = false;

    private final Function1 callback;
    @Expose
    private Integer interval = 0;
    @Expose
    private Integer duration;
    @Expose
    private BigDecimal dose;

    @Expose
    private BigDecimal calculatedDose;
    @Expose
    private LocalDateTime releaseTime;
    @Expose
    private OperationType operationType;

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

    public TempBasalMicroBolusPair(Integer duration, BigDecimal dose, BigDecimal calculatedDose,
                                   LocalDateTime releaseTime, OperationType operationType,
                                   Function1 callback) {
        this.duration = duration;
        this.dose = dose;
        this.releaseTime = releaseTime;
        this.operationType = operationType;
        this.calculatedDose = calculatedDose;
        this.callback = callback;
    }

    public TempBasalMicroBolusPair(Integer duration, Double dose, Double calculatedDose,
                                   LocalDateTime releaseTime, OperationType operationType,
                                   Function1 callback) {
        this(duration, new BigDecimal(dose), new BigDecimal(calculatedDose), releaseTime,
                operationType, callback);
    }

    public Integer getInterval() {
        return interval;
    }

    public Integer getDuration() {
        return duration;
    }

    public BigDecimal getDose() {
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

    public BigDecimal getCalculatedDose() {
        return calculatedDose;
    }

    public BigDecimal getDelta() {
        return dose.subtract(calculatedDose);
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
                result ="Bolus: " + dose.setScale(1, RoundingMode.HALF_DOWN) +
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
        return new TempBasalMicroBolusPair(duration, dose.subtract(new BigDecimal(toDecrease)), calculatedDose,
                releaseTime, operationType, callback);
    }

    public boolean isCommandIssued() {
        return commandIssued;
    }

    public void setCommandIssued(boolean commandIssued) {
        this.commandIssued = commandIssued;
    }
}
