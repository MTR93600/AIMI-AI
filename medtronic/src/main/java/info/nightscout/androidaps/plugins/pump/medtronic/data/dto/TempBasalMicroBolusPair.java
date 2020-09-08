package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.joda.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.Objects;

public class TempBasalMicroBolusPair {
    @Expose
    private Integer interval = 0;
    @Expose
    private Integer duration;
    @Expose
    private BigDecimal dose;

    @Expose
    private BigDecimal calculatedDose;
    @Expose
    private LocalDateTime timeToRelease;
    @Expose
    private OperationType operationType;

    public enum OperationType {
        BOLUS,
        SUSPEND,
        REACTIVATE
    }

    public TempBasalMicroBolusPair(Integer duration, BigDecimal dose, BigDecimal calculatedDose, LocalDateTime timeToRelease, OperationType operationType) {
        this.duration = duration;
        this.dose = dose;
        this.timeToRelease = timeToRelease;
        this.operationType = operationType;
        this.calculatedDose = calculatedDose;
    }

    public TempBasalMicroBolusPair(Integer duration, Double dose, Double calculatedDose, LocalDateTime timeToRelease, OperationType operationType) {
        this(duration, new BigDecimal(dose), new BigDecimal(calculatedDose), timeToRelease, operationType);
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

    public LocalDateTime getTimeToRelease() {
        return timeToRelease;
    }

    public BigDecimal getCalculatedDose() {
        return calculatedDose;
    }

    public BigDecimal getDelta() {
        return dose.subtract(calculatedDose);
    }

    public OperationType getOperationType() {
        return operationType;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TempBasalMicroBolusPair that = (TempBasalMicroBolusPair) o;
        return interval.equals(that.interval) &&
                duration.equals(that.duration) &&
                dose.equals(that.dose) &&
                timeToRelease.equals(that.timeToRelease) &&
                operationType == that.operationType;
    }

    @Override public int hashCode() {
        return Objects.hash(interval, duration, dose, timeToRelease, operationType);
    }

    public TempBasalMicroBolusPair decreaseDosage(double toDecrease) {
        return new TempBasalMicroBolusPair(duration, dose.subtract(new BigDecimal(toDecrease)), calculatedDose,
                timeToRelease, operationType);
    }

}
