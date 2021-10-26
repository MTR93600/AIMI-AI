package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.ContinuousCommandExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;

/**
 * Created by Dirceu on 23/03/21.
 */
public abstract class BleCommand implements Runnable {

    protected final AAPSLogger aapsLogger;
    protected final MedLinkServiceData medLinkServiceData;
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

    public void characteristicChanged(String answer, MedLinkBLE bleComm, String lastCommand) {
//        aapsLogger.info(LTag.PUMPBTCOMM,"char changed");

        CommandExecutor currentCommand = bleComm.getCurrentCommand();
        if (pumpResponse.length() == 0) {
            pumpResponse.append(System.currentTimeMillis()).append("\n");
        }
        pumpResponse.append(answer);
        if(answer.trim().contains("pump suspend state") && lastCommand.contains("x command confirmed")){
            applyResponse(answer,currentCommand, bleComm);
            pumpResponse = new StringBuffer();
            if(currentCommand.getNrRetries()> 5){
                bleComm.completedCommand();
            }
        }
        if (answer.trim().equals("invalid command")) {
            if(lastCommand.contains("suspended state") && currentCommand != null &&
                    MedLinkCommandType.Bolus.isSameCommand(currentCommand.getCurrentCommand())){
                applyResponse(answer,currentCommand, bleComm);
            }
            pumpResponse = new StringBuffer();
            return;
        }
        if (answer.trim().contains("firmware")) {
            //TODO impelement the logic to read the firmware version
            return;
        }
        if (answer.trim().contains("time to powerdown") && !answer.trim().contains("c command confirmed") ) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
            aapsLogger.info(LTag.PUMPBTCOMM, ""+currentCommand);
            if(currentCommand!=null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand.getCurrentCommand());
            }
            if (currentCommand != null && currentCommand.getCurrentCommand() != null) {
                if(partialBolus(answer)){
                    bleComm.reExecuteCommand(currentCommand);
                } else
                if (!bleComm.isCommandConfirmed() || currentCommand instanceof ContinuousCommandExecutor) {
                    bleComm.retryCommand();
                } else {
                    bleComm.removeFirstCommand();
                    bleComm.nextCommand();
                }
            }else{
                bleComm.nextCommand();
            }
        } else if (answer.trim().contains("powerdown")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
            if (currentCommand != null &&
                    currentCommand.getCurrentCommand() != null) {
                if (currentCommand.getCurrentCommand().isSameCommand(
                        MedLinkCommandType.BolusHistory)) {
                    applyResponse(pumpResponse.toString(), currentCommand, bleComm);
                    pumpResponse = new StringBuffer();
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
                    if (currentCommand.hasFinished()) {
                        currentCommand.clearExecutedCommand();
                    }
//                        bleComm.nextCommand();
//                    }else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "MedLink off " + answer);
                    pumpResponse = new StringBuffer();
                    bleComm.retryCommand();
//                    }
                }
            }
            return;
        }
        if (answer.contains("error communication")) {
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkError);

        }
        if (answer.contains("medtronic")) {
            bleComm.setPumpModel(answer);
        }


        if (answer.contains("ready") || answer.contains("eomeomeom")) {
//                    release();
            bleComm.setConnected(true);
            aapsLogger.info(LTag.PUMPBTCOMM, "ready command");
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
//            medLinkServiceData
//            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

            if (currentCommand != null &&
                    currentCommand.nextFunction() != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "MedLink Ready");
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());

                aapsLogger.info(LTag.PUMPBTCOMM, "Applying");
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.getCurrentCommand().code);

                applyResponse(pumpResponse.toString(), currentCommand, bleComm);
                pumpResponse = new StringBuffer();

            } else if (currentCommand != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, answer);
            if(currentCommand!=null && !MedLinkCommandType.BolusStatus.isSameCommand(currentCommand.getCurrentCommand())) {
                SystemClock.sleep(700);
                bleComm.completedCommand();
            }
            pumpResponse = new StringBuffer();

            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
//            bleComm.nextCommand();
            return;
        }
        if (answer.trim().contains("ok+conn")) {
            //medLinkUtil.sendBroadcastMessage();
//                    if(remainingCommands.isEmpty()) {
//                    release();

//                    }
            if (answer.trim().contains("ok+conn or command")) {
//                if (bleComm.needToAddConnectCommand()) {
//                    bleComm.addExecuteConnectCommand();
//                }
//                bleComm.setConnected(true);
//                aapsLogger.info("");
                SystemClock.sleep(500);
                bleComm.completedCommand();
                return;
            }
//            if (bleComm.getCurrentCommand() != null &&
//                    bleComm.getCurrentCommand().getCommand() != null &&
//                    Arrays.equals(MedLinkCommandType.Connect.getRaw(), bleComm.getCurrentCommand().getCommand())) {
//                aapsLogger.info(LTag.PUMPBTCOMM, "clearing command");
//                bleComm.clearCommands();
//            } else
//            if (bleComm.needToAddConnectCommand()) {
//                aapsLogger.info(LTag.PUMPBTCOMM, "added ok");
//                bleComm.addExecuteConnectCommand();
//            } else {
//            }
//            bleComm.setConnected(false);
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command");
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
//            bleComm.nextCommand();
            return;
        }
        String answers = pumpResponse.toString();
        if (currentCommand != null) {
//            aapsLogger.info(LTag.PUMPBTCOMM, answers);
            aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());
            aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.nextCommand().toString());

            if (currentCommand.getCurrentCommand() != null)
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.getCurrentCommand().toString());

            if (answers.contains("check pump status") && (answers.contains("pump suspend state") ||
                    answers.contains("pump normal state")) &&
                    currentCommand.nextCommand() != null &&
                    (MedLinkCommandType.StopPump.isSameCommand(currentCommand.nextCommand())) ||
                    MedLinkCommandType.StartPump.isSameCommand(currentCommand.nextCommand())) {
                aapsLogger.info(LTag.PUMPBTCOMM, "status command");
                aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
                pumpResponse = new StringBuffer();
                bleComm.completedCommand();
            }
        }

//        aapsLogger.info(LTag.PUMPBTCOMM, answer);
    }

    protected boolean partialBolus(String answer){
        Supplier<Stream<String>> answers = () -> Arrays.stream(answer.split("\n"));
        if(answers.get().anyMatch(f -> f.contains("m command confirmed"))){
            return answers.get().filter(f -> f.contains("square bolus")).map(f ->{
               Pattern pat = Pattern.compile("\\d+\\.\\d+");
               Matcher mat = pat.matcher(f);
               if(mat.find()){
                   String total = mat.group();
                   if(mat.find()) {
                       return Double.parseDouble(total) > Double.parseDouble(mat.group());
                   }
               }
               return false;
            }).findFirst().orElse(false);
        } else{
            return false;
        }
    }

    private void applyResponse(String pumpResp, CommandExecutor currentCommand, MedLinkBLE bleComm) {
        byte[] command = currentCommand.getCurrentCommand().getRaw();

        Function function = currentCommand.nextFunction();
        handler.post(() -> {
            try {
                aapsLogger.info(LTag.PUMPBTCOMM, "posting command");
                Supplier<Stream<String>> sup = () -> Arrays.stream(pumpResp.split("\n")).filter(f -> f != "\n");
                if (function != null) {
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
