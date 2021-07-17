package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command;

import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by Dirceu on 23/03/21.
 */
public abstract class BleCommand implements Runnable {

    protected final AAPSLogger aapsLogger;
    private boolean isLastMessageEom = false;
    private final MedLinkServiceData medLinkServiceData;
    private final Handler handler;
    private Runnable runnable;
    protected StringBuffer pumpResponse = new StringBuffer();
    //    private MedLinkStandardReturn<Stream> lastResult;
    private boolean partOfReady;

    public BleCommand(AAPSLogger aapsLogger, MedLinkServiceData medlinkServiceData) {
        this.aapsLogger = aapsLogger;
        this.medLinkServiceData = medlinkServiceData;
        handler = new Handler(Looper.myLooper());
    }

    public BleCommand(AAPSLogger aapsLogger, MedLinkServiceData medlinkServiceData, Runnable runnable) {
        this(aapsLogger, medlinkServiceData);
        this.runnable = runnable;
    }

    public void characteristicChanged(String answer, MedLinkBLE bleComm, String lastCommand) {
//        aapsLogger.info(LTag.PUMPBTCOMM,"char changed");

        RemainingBleCommand currentCommand = bleComm.getCurrentCommand();
        if (pumpResponse.length() == 0) {
            pumpResponse.append(System.currentTimeMillis()).append("\n");
        }
        pumpResponse.append(answer);
//                pumpResponse.append("\n");
        if (answer.trim().equals("powerdown")) {
            isLastMessageEom = false;
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
            if (currentCommand != null &&
                    currentCommand.getCommand() != null) {
                if (Arrays.equals(currentCommand.getCommand(),
                        MedLinkCommandType.BolusHistory.getRaw())) {
                    applyResponse(pumpResponse.toString(), currentCommand, bleComm);
//                } else if (Arrays.equals(currentCommand.getCommand(),
//                        MedLinkCommandType.Connect.getRaw())) {
//                    String answers = pumpResponse.toString();
//                    if (!answers.contains("ready") && !answers.contains("confirmed pump wake up")) {
//                        medLinkServiceData.setServiceState(MedLinkServiceState.PumpConnectorError,
//                                MedLinkError.MedLinkUnreachable);
//                        pumpResponse = new StringBuffer();
//                        bleComm.clearCommands();
//                    }
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "MedLink off " + answer);
                    bleComm.retryCommand();
                }
            }
            pumpResponse = new StringBuffer();
            return;
        }
        if (answer.contains("error communication")) {
            isLastMessageEom = false;
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkError);

        }
        if (answer.contains("medtronic")) {
            isLastMessageEom = false;
            bleComm.setPumpModel(answer);
        }


        if ((partOfReady && (lastCommand + answer).contains("ready")) || answer.contains("ready") || answer.contains("eomeomeom")) {
//                    release();

            aapsLogger.info(LTag.PUMPBTCOMM, "ready command");
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
//            medLinkServiceData
//            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
            bleComm.setConnected(true);
            if (currentCommand != null &&
                    !Arrays.equals(currentCommand.getCommand()
                            , MedLinkCommandType.NoCommand.getRaw())) {
                aapsLogger.info(LTag.PUMPBTCOMM, "MedLink Ready");
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());

                aapsLogger.info(LTag.PUMPBTCOMM, "Applying");
                aapsLogger.info(LTag.PUMPBTCOMM, new String(currentCommand.getCommand()));

                applyResponse(pumpResponse.toString(), currentCommand, bleComm);
                pumpResponse = new StringBuffer();

            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, answer);
            if (!isLastMessageEom) {
                bleComm.completedCommand();
            } else {
                pumpResponse = new StringBuffer();
            }
            if (answer.contains("eomeomeom")) {
                isLastMessageEom = true;
            } else {
                isLastMessageEom = false;
            }
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
//            bleComm.nextCommand();
            return;
        }
        if (answer.trim().contains("ok+conn")) {
            isLastMessageEom = false;
            //medLinkUtil.sendBroadcastMessage();
//                    if(remainingCommands.isEmpty()) {
//                    release();

//                    }

            if (bleComm.getCurrentCommand() != null &&
                    bleComm.getCurrentCommand().getCommand() != null &&
                    Arrays.equals(MedLinkCommandType.Connect.getRaw(), bleComm.getCurrentCommand().getCommand())) {
                aapsLogger.info(LTag.PUMPBTCOMM, "clearing command");
//                bleComm.clearCommands();
//            } else if (bleComm.needToAddConnectCommand()) {
//                aapsLogger.info(LTag.PUMPBTCOMM, "added ok");
//                bleComm.addExecuteConnectCommand();
            } else {
                bleComm.printBuffer();
            }
            bleComm.setConnected(false);
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
//            bleComm.nextCommand();
            return;
        }
        String answers = pumpResponse.toString();
        if (answers.contains("check pump status") && (answers.contains("pump suspend state") ||
                answers.contains("pump normal state")) && currentCommand != null &&
                currentCommand.getCommand() != null &&
                Arrays.equals(currentCommand.getCommand(),
                        MedLinkCommandType.StopStartPump.getRaw())) {
            isLastMessageEom = false;
            aapsLogger.info(LTag.PUMPBTCOMM, "status command");
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
            pumpResponse = new StringBuffer();
            bleComm.completedCommand();
            return;
        }

        wasLastCommandPartOfReady(answer);
//        aapsLogger.info(LTag.PUMPBTCOMM, answer);
    }

    protected void wasLastCommandPartOfReady(String answer) {
        if (answer.endsWith("r") || answer.endsWith("re") || answer.endsWith("rea")
                || answer.endsWith("read")) {
            this.partOfReady = true;
        } else {
            this.partOfReady = false;
        }
    }

    private void applyResponse(String pumpResp, RemainingBleCommand currentCommand, MedLinkBLE bleComm) {
        byte[] command = currentCommand.getCommand();

        Function function = currentCommand.getFunction();
        handler.post(() -> {
            try {
                aapsLogger.info(LTag.PUMPBTCOMM, "posting command");
                Supplier<Stream<String>> sup = () -> Arrays.stream(pumpResp.split("\n")).filter(f -> f != "\n");
                if (!Arrays.equals(command, MedLinkCommandType.NoCommand.getRaw()) &&
                        function != null) {
//                aapsLogger.info(LTag.PUMPBTCOMM, String.join(", ", pumpResp));
                    Function func = function.andThen(f -> {
                        if (pumpResp.contains("ready")) {
                            bleComm.applyClose();
                        }
                        return f;
                    });
                    MedLinkStandardReturn<Stream> lastResult = null;
                    if (Arrays.equals(command, MedLinkCommandType.IsigHistory.getRaw())) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "posting isig");
                        MedLinkStandardReturn<Stream> finalLastResult = lastResult;
                        Object result = func.apply(sup);
                        if (result == null) {
                            bleComm.retryCommand();
                        }
                    } else if (!Arrays.equals(command, MedLinkCommandType.Connect.getRaw())) {
                        func.apply(sup);
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(command));

                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "function not called");
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + function);
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(command));
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
