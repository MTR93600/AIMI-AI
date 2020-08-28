package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.joda.time.LocalDateTime;

import java.util.Objects;

public class TempBasalMicroBolusPair {
    @Expose
    private Integer interval = 0;
    @Expose
    private Integer operationDuration = 0;
    @Expose
    private Double bolusDosage = 0d;

    @Expose
    private LocalDateTime operationTime;

    @Expose
    private OperationType operationType;

    public enum OperationType {
        BOLUS,
        SUSPEND,
        REACTIVATE
    }

    public TempBasalMicroBolusPair(Integer operationDuration, Double bolusDosage, LocalDateTime operationTime, OperationType operationType) {
        this.operationDuration = operationDuration;
        this.bolusDosage = bolusDosage;
        this.operationTime = operationTime;
        this.operationType = operationType;
    }

    public Integer getInterval() {
        return interval;
    }

    public Integer getOperationDuration() {
        return operationDuration;
    }

    public Double getBolusDosage() {
        return bolusDosage;
    }

    public LocalDateTime getOperationTime() {
        return operationTime;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TempBasalMicroBolusPair that = (TempBasalMicroBolusPair) o;
        return interval.equals(that.interval) &&
                operationDuration.equals(that.operationDuration) &&
                bolusDosage.equals(that.bolusDosage) &&
                operationTime.equals(that.operationTime) &&
                operationType == that.operationType;
    }

    @Override public int hashCode() {
        return Objects.hash(interval, operationDuration, bolusDosage, operationTime, operationType);
    }

}
