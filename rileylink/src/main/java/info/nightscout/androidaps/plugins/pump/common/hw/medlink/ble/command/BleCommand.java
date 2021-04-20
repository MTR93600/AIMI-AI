package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command;

import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
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
    private MedLinkStandardReturn<Stream> lastResult;

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
                    currentCommand.getCommand() != null) {
                if (Arrays.equals(currentCommand.getCommand(),
                        MedLinkCommandType.BolusHistory.getRaw())) {
                    applyResponse(pumpResponse.toString(), currentCommand, bleComm);

                } else if (Arrays.equals(currentCommand.getCommand(),
                        MedLinkCommandType.Connect.getRaw())) {
                    String answers = pumpResponse.toString();
                    if (answer.contains("ready") && !answers.contains("confirmed pump wake up")) {
                        medLinkServiceData.setServiceState(MedLinkServiceState.PumpConnectorError,
                                MedLinkError.MedLinkUnreachable);
                    }
                }
            }

            aapsLogger.debug("MedLink off " + answer);
            bleComm.retryCommand();
            return;
        }
        if (answer.contains("error communication")) {
            aapsLogger.debug("MedLink waked " + answer);
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkError);
        }
        if (answer.contains("medtronic")) {
            bleComm.setPumpModel(answer);
        }
        if (answer.contains("ready")) {//|| answer.contains("eomeomeom")) {
//                    release();
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
//            medLinkServiceData
//            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
            bleComm.setConnected(true);
            bleComm.setConnectAdded(false);
            if (currentCommand != null &&
                    !Arrays.equals(currentCommand.getCommand()
                            , MedLinkCommandType.NoCommand.getRaw())) {
                aapsLogger.info(LTag.PUMPBTCOMM, "MedLink Ready");
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());

                aapsLogger.info(LTag.PUMPBTCOMM, "Applying");
                aapsLogger.info(LTag.PUMPBTCOMM, new String(currentCommand.getCommand()));
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
                applyResponse(pumpResponse.toString(), currentCommand, bleComm);
                pumpResponse = new StringBuffer();

            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, answer);
            bleComm.completedCommand();
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
//            bleComm.nextCommand();
            return;
        }
        if (answer.trim().contains("ok+conn")) {
            //medLinkUtil.sendBroadcastMessage();
//                    if(remainingCommands.isEmpty()) {
//                    release();

//                    }

            if (bleComm.needToAddConnectCommand()) {
                aapsLogger.info(LTag.PUMPBTCOMM, "added ok");
                bleComm.addExecuteConnectCommand();
            } else {
                bleComm.printBuffer();
            }
            bleComm.setConnected(false);
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, answer);
            bleComm.nextCommand();
        }
    }

    private void applyResponse(String pumpResp, RemainingBleCommand currentCommand, MedLinkBLE bleComm) {

        handler.post(() -> {
            try {
                aapsLogger.info(LTag.PUMPBTCOMM, "posting command");
                Supplier<Stream<String>> sup = () -> Arrays.stream(pumpResp.split("\n"));
                if (!Arrays.equals(currentCommand.getCommand(), MedLinkCommandType.NoCommand.getRaw()) &&
                        currentCommand.getFunction() != null) {
//                aapsLogger.info(LTag.PUMPBTCOMM, String.join(", ", pumpResp));
                    Function func = currentCommand.getFunction().andThen(f -> {
                        if (pumpResp.contains("ready")) {
                            bleComm.applyClose();
                        }
                        return f;
                    });
                    if (Arrays.equals(currentCommand.getCommand(), MedLinkCommandType.IsigHistory.getRaw())) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "posting isig");
                        Object result = func.apply(new Pair<Supplier<Stream<String>>, Supplier<Stream<BgReading>>>(sup,
                                () -> lastResult.getFunctionResult()));
                        if(result == null){
                            bleComm.retryCommand();
                        }
                    } else if (!Arrays.equals(currentCommand.getCommand(), MedLinkCommandType.Connect.getRaw())) {
                        lastResult = (MedLinkStandardReturn<Stream>) func.apply(sup);
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(currentCommand.getCommand()));

                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "function not called");
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand.getFunction());
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(currentCommand.getCommand()));
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
