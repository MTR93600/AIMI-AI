package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;

/**
 * Created by Dirceu on 01/06/21.
 */
public class BolusAnswer {
    private final PumpResponses response;
    private double bolusAmount;
    private ZonedDateTime bolusDeliveryTime;
    private double delivered;
    private  String answer;

    public BolusAnswer(PumpResponses response, double bolusAmount, ZonedDateTime bolusDeliveryTime) {
        this.response = response;
        this.bolusAmount = bolusAmount;
        this.bolusDeliveryTime = bolusDeliveryTime;
    }
    public BolusAnswer(PumpResponses response, String answer) {
        this.response = response;
        this.answer = answer;
    }

    public BolusAnswer(PumpResponses response, double delivered, String answer) {
        this.response = response;
        this.delivered = delivered;
        this.answer = answer;
    }

    public PumpResponses getResponse() {
        return response;
    }

    public String getAnswer() {
        return answer;
    }

    public double getBolusAmount() {
        return bolusAmount;
    }

    public ZonedDateTime getBolusDeliveryTime() {
        return bolusDeliveryTime;
    }

    public double getDelivered() {
        return delivered;
    }
}
