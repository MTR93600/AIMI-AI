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
    private LocalDateTime releaseTime;
    @Expose
    private OperationType operationType;

    public void delayInMinutes(int delay) {
        this.releaseTime = this.releaseTime.plusMinutes(delay);
    }

    public enum OperationType {
        BOLUS,
        SUSPEND,
        REACTIVATE
    }

    public TempBasalMicroBolusPair(Integer duration, BigDecimal dose, BigDecimal calculatedDose, LocalDateTime releaseTime, OperationType operationType) {
        this.duration = duration;
        this.dose = dose;
        this.releaseTime = releaseTime;
        this.operationType = operationType;
        this.calculatedDose = calculatedDose;
    }

    public TempBasalMicroBolusPair(Integer duration, Double dose, Double calculatedDose, LocalDateTime releaseTime, OperationType operationType) {
        this(duration, new BigDecimal(dose), new BigDecimal(calculatedDose), releaseTime, operationType);
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
                releaseTime.equals(that.releaseTime) &&
                operationType == that.operationType;
    }

    @Override public int hashCode() {
        return Objects.hash(interval, duration, dose, releaseTime, operationType);
    }

    public TempBasalMicroBolusPair decreaseDosage(double toDecrease) {
        return new TempBasalMicroBolusPair(duration, dose.subtract(new BigDecimal(toDecrease)), calculatedDose,
                releaseTime, operationType);
    }

}
