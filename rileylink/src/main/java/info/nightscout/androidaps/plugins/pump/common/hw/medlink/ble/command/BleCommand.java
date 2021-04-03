package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command;

import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;

/**
 * Created by Dirceu on 23/03/21.
 */
public abstract class BleCommand implements Runnable {

    private final AAPSLogger aapsLogger;
    private final MedLinkServiceData medLinkServiceData;
    private final Handler handler;
    private Runnable runnable;
    protected StringBuffer pumpResponse = new StringBuffer();

    public BleCommand(AAPSLogger aapsLogger, MedLinkServiceData medlinkServiceData) {
        this.aapsLogger = aapsLogger;
        this.medLinkServiceData = medlinkServiceData;
        handler = new Handler(Looper.myLooper());
    }

    public BleCommand(AAPSLogger aapsLogger, MedLinkServiceData medlinkServiceData, Runnable runnable) {
        this(aapsLogger, medlinkServiceData);
        this.runnable = runnable;
    }

    public void characteristicChanged(String answer, MedLinkBLE bleComm) {
        RemainingBleCommand currentCommand = bleComm.getCurrentCommand();
        if (pumpResponse.length() == 0) {
            pumpResponse.append(System.currentTimeMillis()).append("\n");
        }
        pumpResponse.append(answer);
//                pumpResponse.append("\n");
        if (answer.trim().equals("powerdown")) {
            if (currentCommand != null &&
                    currentCommand.getCommand() != null &&
                    currentCommand.getCommand() ==
                            MedLinkCommandType.BolusHistory.getRaw()) {
                currentCommand.getFunction().apply(answer);
            }
            aapsLogger.debug("MedLink off " + answer);
            bleComm.disconnect();
            bleComm.retryCommand();
            return;
        }
        if (answer.contains("error communication")) {
            aapsLogger.debug("MedLink waked " + answer);
            medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkError);
        }
        if (answer.contains("medtronic")) {
            bleComm.setPumpModel(answer);
        }
        if (answer.contains("ready")) {//|| answer.contains("eomeomeom")) {
//                    release();
            bleComm.setConnected(true);
            bleComm.setConnectAdded(false);
            if (currentCommand != null && currentCommand.getCommand()
                    != MedLinkCommandType.NoCommand.getRaw()) {
                aapsLogger.info(LTag.PUMPBTCOMM, "MedLink Ready");
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());

                aapsLogger.info(LTag.PUMPBTCOMM, "Applying");
                aapsLogger.info(LTag.PUMPBTCOMM, new String(currentCommand.getCommand()));
                applyResponse(pumpResponse.toString(), currentCommand);
                pumpResponse = new StringBuffer();

            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, answer);
            bleComm.completedCommand();
            medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);
//            bleComm.nextCommand();
            return;
        }
        if (answer.trim().contains("ok+conn")) {
            //medLinkUtil.sendBroadcastMessage();
//                    if(remainingCommands.isEmpty()) {
//                    release();

//                    }

            bleComm.setConnected(false);
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, answer);
            bleComm.nextCommand();
        }
    }

    private void applyResponse(String pumpResp, RemainingBleCommand currentCommand) {

        handler.post(() -> {


            try {
                Supplier<Stream<String>> sup = () -> Arrays.stream(pumpResp.split("\n"));
                if (currentCommand.getCommand() != MedLinkCommandType.NoCommand.getRaw() &&
                        currentCommand.getFunction() != null) {
//                aapsLogger.info(LTag.PUMPBTCOMM, String.join(", ", pumpResp));
                    currentCommand.getFunction().apply(sup);
                }
//            else if (!this.resultActivity.isEmpty()) {
//                MedLinkBLE.Resp resp = this.resultActivity.get(0);
////                aapsLogger.info(LTag.PUMPBTCOMM, resp.toString());
//                resp.getFunc().apply(sup);
//            }
//            if (!this.resultActivity.isEmpty()) {
//                this.resultActivity.remove(0);
//            }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });

    }

    @Override public void run() {
        if (runnable != null) {
            runnable.run();
        }
    }
}
