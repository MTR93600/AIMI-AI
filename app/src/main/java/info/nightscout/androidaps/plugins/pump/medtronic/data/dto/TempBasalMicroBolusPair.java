package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

public class TempBasalMicroBolusPair {
    @Expose
    private Integer interval=0;
    @Expose
    private Integer operationDuration=0;
    @Expose
    private Double bolusDosage=0d;



}
