package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.logging.L;

public class TempBasalMicrobolusOperations {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    @Expose
    private Integer remainingOperations=0;
    @Expose
    private double operationDose=0d;
    @Expose
    private Integer nextOperationInterval=0;
    @Expose
    private List<Integer> intervalBetweenDoses= Collections.emptyList();
    @Expose
    private Integer suspendedTime;

    public TempBasalMicrobolusOperations() {

    }

    public TempBasalMicrobolusOperations(Integer remainingOperations, double operationDose, List<Integer> intervalBetweenDoses) {
        this.remainingOperations = remainingOperations;
        this.operationDose = operationDose;
        this.intervalBetweenDoses = intervalBetweenDoses;
    }

    public Integer getRemainingOperations() {
        return remainingOperations;
    }

    public double getOperationDose() {
        return operationDose;
    }

    public List<Integer> getIntervalBetweenDoses() {
        return intervalBetweenDoses;
    }

    @Override
    public String toString() {
        return "TempBasalMicrobolusOperations{" +
                "remainingOperations=" + remainingOperations +
                ", operationDose=" + operationDose +
                ", nextOperationInterval=" + nextOperationInterval +
                ", intervalBetweenDoses=" + intervalBetweenDoses +
                '}';
    }

    public synchronized void updateOperations(Integer remainingOperations, double operationDose, List<Integer>  intervalBetweenDoses, Integer suspendedTime) {
        this.remainingOperations = remainingOperations;
        this.suspendedTime = suspendedTime;
        this.operationDose = operationDose;
        this.intervalBetweenDoses = intervalBetweenDoses;
    }
}
