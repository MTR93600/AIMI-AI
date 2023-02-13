package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.PumpResponses;
import info.nightscout.interfaces.pump.DetailedBolusInfo;


/**
 * Created by Dirceu on 01/06/21.
 */
public class BolusAnswer {
    private final PumpResponses response;
    private double carbs;
    private double bolusAmount;
    private double delivered;
    private  String answer;
    private DetailedBolusInfo info;

    public BolusAnswer(PumpResponses response, double bolusAmount,
                        DetailedBolusInfo info) {
        this.response = response;
        this.bolusAmount = bolusAmount;
        this.info = info;
    }


    public BolusAnswer(PumpResponses response, String answer, DetailedBolusInfo info) {
        this.response = response;
        this.answer = answer;
        this.info = info;
    }

    public BolusAnswer(PumpResponses response, double delivered, String answer, double carbs) {
        this.response = response;
        this.delivered = delivered;
        this.answer = answer;
        this.carbs = carbs;
    }

    public PumpResponses getResponse() {
        return response;
    }

    public String getAnswer() {
        return answer;
    }


    public DetailedBolusInfo getDetailedBolusInfo() {
        return info;
    }

    public double getDelivered() {
        return delivered;
    }


}
