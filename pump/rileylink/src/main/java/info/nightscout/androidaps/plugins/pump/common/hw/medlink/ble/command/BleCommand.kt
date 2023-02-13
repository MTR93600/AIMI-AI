package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command

import android.os.Handler
import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandExecutor
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.ContinuousCommandExecutor
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Stream

/**
 * Created by Dirceu on 23/03/21.
 */
open class BleCommand(protected val aapsLogger: AAPSLogger, protected val medLinkServiceData: MedLinkServiceData) {

    private val handler: Handler? = null
    protected var pumpResponse = StringBuffer()
    open fun characteristicChanged(answer: String, bleComm: MedLinkBLE, lastCharacteristic: String) {
        // aapsLogger.info(LTag.PUMPBTCOMM, answer)
        // aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
        if (answer.trim { it <= ' ' }.isEmpty()) {
            pumpResponse = StringBuffer()
            return
        }
        val currentCommand = bleComm.currentCommand
        if (pumpResponse.isEmpty()) {
            pumpResponse.append(System.currentTimeMillis()).append("\n")
        } else if ((lastCharacteristic + answer).contains("command confirmed")) {
            currentCommand?.isConfirmed = true
            pumpResponse.append("\n")
            bleComm.setConfirmedCommand(true)
        } else if (answer.contains("powerdown after")) {
            pumpResponse = StringBuffer()
        }
        pumpResponse.append(answer)
        if (answer.startsWith("invalid command")) {
            bleComm.setConfirmedCommand(false)
            if (currentCommand != null && currentCommand.getCurrentCommand() != MedLinkCommandType.NoCommand) {
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.getCurrentCommand().code!!)
                if (currentCommand.isInitialized) {
                    currentCommand.clearExecutedCommand()
                }
                pumpResponse = StringBuffer()
                //                if ((!bleComm.isBolus(currentCommand.getCurrentCommand()))) {
//                    bleComm.retryCommand();
//                }
            }
        }
        if (answer.trim { it <= ' ' }.contains("%") && lastCharacteristic.trim { it <= ' ' }.contains("med-link battery")) {
            val batteryPattern = Pattern.compile("\\d+")
            val batteryMatcher = batteryPattern.matcher(lastCharacteristic + answer)
            if (batteryMatcher.find()) {
                bleComm.setBatteryLevel(Integer.valueOf(batteryMatcher.group()))
            }
            return
        }
        if (lastCharacteristic.trim { it <= ' ' }.contains("firmware")) {
            val firmware = (lastCharacteristic + answer).split(" ".toRegex()).toTypedArray()
            if (firmware.size == 3) {
                bleComm.setFirmwareVersion(firmware[2])
            }
            return
        }
        if ((lastCharacteristic + answer).trim { it <= ' ' }.contains("time to powerdown") //                && !answer.trim().contains("c command confirmed")
        ) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand)
            if (currentCommand != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand.getCurrentCommand())
            }
            if (currentCommand != null && currentCommand.getCurrentCommand() != MedLinkCommandType.NoCommand) {
                if (partialBolus(pumpResponse.toString())) {
                    bleComm.reExecuteCommand(currentCommand)
                } else if (!bleComm.isCommandConfirmed || currentCommand is ContinuousCommandExecutor<*>
                    || currentCommand.firstCommand() === MedLinkCommandType.BolusStatus
                ) {
                    bleComm.retryCommand()
                    //                } else if (bleComm.partialCommand()) {
//                    applyResponse(pumpResponse.toString(), currentCommand, bleComm);
//                    bleComm.nextCommand();
//                    return;
                } else {
                    bleComm.removeFirstCommand(true)
                    bleComm.nextCommand()
                }
            } else {
                bleComm.nextCommand()
            }
        } else if (answer.trim { it <= ' ' }.contains("powerdown")) {
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            if (currentCommand != null) {
                currentCommand.getCurrentCommand()
                if (currentCommand.getCurrentCommand().isSameCommand(
                        MedLinkCommandType.BolusHistory
                    )
                ) {
                    applyResponse(pumpResponse.toString(), currentCommand, bleComm)
                    pumpResponse = StringBuffer()
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
                        currentCommand.clearExecutedCommand()
                    }
                    //                        bleComm.nextCommand();
//                    }else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "MedLink off $answer")
                    pumpResponse = StringBuffer()
                    bleComm.retryCommand()
                    //                    }
                }
            }
            return
        }
        if (answer.contains("error communication")) {
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkError)
        }
        if (answer.contains("medtronic")) {
            bleComm.setPumpModel(answer)
        }
        if (!lastCharacteristic.contains("ready") && !lastCharacteristic.contains("eomeomeom") &&
            (lastCharacteristic + answer).contains("ready") ||
            !lastCharacteristic.contains("eomeomeom") && answer.contains("eomeomeom")
        ) {
//                    release();
            bleComm.setConnected(true)
            aapsLogger.info(LTag.PUMPBTCOMM, "ready command")
            aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString())
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady)
            //            medLinkServiceData
//            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
            if (currentCommand != null && currentCommand.nextFunction() != null && !(answer + lastCharacteristic).contains(
                    "invalid"
                ) && currentCommand.isConfirmed
            ) {
                aapsLogger.info(LTag.PUMPBTCOMM, "MedLink Ready")
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString())
                aapsLogger.info(LTag.PUMPBTCOMM, "Applying")
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.getCurrentCommand().code!!)
                val response = pumpResponse.toString()
                pumpResponse = StringBuffer()
                applyResponse(response, currentCommand, bleComm)
            } else if (currentCommand != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString())
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "completing command")
            aapsLogger.info(LTag.PUMPBTCOMM, answer)
            if (currentCommand != null && !currentCommand.contains(MedLinkCommandType.BolusStatus)) {
                SystemClock.sleep(700)
                bleComm.completedCommand()
            }
            pumpResponse = StringBuffer()
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady)
            //            bleComm.nextCommand();
        }
    }

    protected fun partialBolus(answer: String): Boolean {
        val answers = Supplier { Arrays.stream(answer.split("\n".toRegex()).toTypedArray()) }
        return if (answers.get().anyMatch { f: String -> f.contains("m command confirmed") }) {
            answers.get().filter { f: String -> f.contains("square bolus") }.map { f: String? ->
                val pat = Pattern.compile("\\d+\\.\\d+")
                val mat = pat.matcher(f.toString())
                if (mat.find()) {
                    val total = mat.group()
                    if (mat.find()) {
                        return@map total.toDouble() > mat.group().toDouble()
                    }
                }
                false
            }.findFirst().orElse(false)
        } else {
            false
        }
    }

    fun applyResponse(bleComm: MedLinkBLE) {
        this.applyResponse(pumpResponse.toString(), bleComm.currentCommand, bleComm)
    }

    protected fun applyResponse(pumpResp: String, currentCommand: CommandExecutor<*>?, bleComm: MedLinkBLE) {
        val command = currentCommand!!.getCurrentCommand()
        aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString())
        val function: Function<Supplier<Stream<String>>, out MedLinkStandardReturn<*>?>? = currentCommand.nextFunction()
        bleComm.post {
            try {
                aapsLogger.info(LTag.PUMPBTCOMM, "posting command")
                val sup = Supplier { Arrays.stream(pumpResp.split("\n".toRegex()).toTypedArray()) } //.filter(f -> f != "\n");
                if (function != null) {
                    // val lastResult: MedLinkStandardReturn<Stream<String>>? = null
                    if (command == MedLinkCommandType.IsigHistory) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "posting isig")
                        val result = function.apply(sup)
                        if (result == null) {
                            bleComm.retryCommand()
                        }
                    } else if (command != MedLinkCommandType.Connect) {
                        function.apply(sup)
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, String(command.getRaw()))
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "function not called")
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + function)
                    aapsLogger.info(LTag.PUMPBTCOMM, String(command.getRaw()))
                }
                //            else if (!this.resultActivity.isEmpty()) {
//                MedLinkBLE.Resp resp = this.resultActivity.get(0);
////                aapsLogger.info(LTag.PUMPBTCOMM, resp.toString());
//                resp.getFunc().apply(sup);
//            }
//            if (!this.resultActivity.isEmpty()) {
//                this.resultActivity.remove(0);
//            }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}