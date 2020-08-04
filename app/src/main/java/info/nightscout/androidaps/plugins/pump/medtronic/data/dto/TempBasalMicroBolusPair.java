package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.joda.time.LocalDateTime;

public class TempBasalMicroBolusPair {
    @Expose
    private Integer interval = 0;
    @Expose
    private Integer operationDuration = 0;
    @Expose
    private Double bolusDosage = 0d;

    private LocalDateTime operationTime;

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
}
