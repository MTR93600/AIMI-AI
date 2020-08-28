package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractSequentialList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.logging.L;

public class TempBasalMicrobolusOperations {

//    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    public LinkedList<TempBasalMicroBolusPair> operations = new LinkedList<>();
    @Expose
    private Integer remainingOperations = operations.size();
    @Expose
    private double totalDosage = 0d;
    @Expose
    private Integer nextOperationInterval = 0;
    @Expose
    private Integer suspendedTime;

    public TempBasalMicrobolusOperations() {

    }

    public TempBasalMicrobolusOperations(Integer remainingOperations, double totalDosage, LinkedList<TempBasalMicroBolusPair> operations) {
        this.remainingOperations = remainingOperations;
        this.totalDosage = totalDosage;
        this.operations = operations;
    }

    public Integer getRemainingOperations() {
        return remainingOperations;
    }

    public double getTotalDosage() {
        return totalDosage;
    }

    public List<TempBasalMicroBolusPair> getOperations() {
        return operations;
    }

    @Override
    public String toString() {
        return "TempBasalMicrobolusOperations{" +
                "remainingOperations=" + remainingOperations +
                ", operationDose=" + totalDosage +
                ", nextOperationInterval=" + nextOperationInterval +
                ", operations=" + operations +
                '}';
    }

    public synchronized void updateOperations(Integer remainingOperations, double operationDose, LinkedList<TempBasalMicroBolusPair> operations, Integer suspendedTime) {
        this.remainingOperations = remainingOperations;
        this.suspendedTime = suspendedTime;
        this.totalDosage = operationDose;
        this.operations = operations;
    }

    public synchronized void clearOperations() {
        this.operations.clear();
    }
}
