package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleConnectCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.CommandStructure
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.BLECommOperation
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult
import info.nightscout.pump.common.defs.PumpRunningState
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.pump.core.utils.ThreadUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.apache.commons.lang3.StringUtils
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Dirceu on 30/09/20.
 */
@SuppressLint("MissingPermission")
@Singleton
class MedLinkBLE //extends RileyLinkBLE
@Inject constructor(
    private val context: Context, val resourceHelper: ResourceHelper, private val aapsLogger: AAPSLogger, private val sp: SP,
) {

    private var bug133: Int=0
    var needToCheckOnHold: Boolean = false
    private var calibrateCommand: CommandExecutor<*>? = null
    private var bluetoothGattCallback: BluetoothGattCallback? = null
    private var firmwareVersion = ""
    private var batteryLevel = 0
    var isCommandConfirmed = false
        private set

    private val bleHandler: Handler
    private var needRetry = false
    private var lastCloseAction = 0L
    private var lastExecutedCommand: Long = 0
    private var isDiscovering = false
    private var sleepSize: Long = 0
    private var lastConnection: Long = 0
    private var connectionStatus = ConnectionStatus.CLOSED
    private var notificationEnabled = false
    private var lastConfirmedCommand = 0L
    private var lastReceivedCharacteristic: Long = 0
    private val handlerThread = HandlerThread("BleThread")
    private val characteristicThread = HandlerThread("CharacteristicThread")
    private val handler: Handler
    private var lastGattConnection = 1L
    private var connectionStatusChange: Long = 0
    private var lastPumpStatus: PumpRunningState? = null
    private var manualDisconnect = false

    // private val toBeRemoved: CommandExecutor<Any,out BleCommand>? = null
    private lateinit var radioResponseCountNotified: Runnable
    private val gattDebugEnabled = true
    private var mIsConnected = false
    private var pumpResponse: StringBuffer? = null
    private var gattConnected = false
    private var medLinkDevice: BluetoothDevice? = null
    fun partialCommand(): Boolean {
        return lastConfirmedCommand > lastConnection &&
            lastReceivedCharacteristic > lastConfirmedCommand
    }

    fun removeFirstCommand(force: Boolean) {
        if (currentCommand != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString())
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "null")
        }
        val toBeRemoved = currentCommand
        if (currentCommand != null &&
            currentCommand!!.hasFinished() || force
        ) {
            aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand)
            aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand?.hasFinished())
            aapsLogger.info(LTag.PUMPBTCOMM, "" + force)
            aapsLogger.info(LTag.PUMPBTCOMM, "" + lowPriorityExecutionCommandQueue.remove(toBeRemoved))
            aapsLogger.info(LTag.PUMPBTCOMM, "" + executionCommandQueue.remove(toBeRemoved))
            aapsLogger.info(LTag.PUMPBTCOMM, "" + priorityExecutionCommandQueue.remove(toBeRemoved))
            currentCommand = null
        }
    }

    fun <B> reExecuteCommand(currentCommand: CommandExecutor<B>) {
        if (!hasCommandsToExecute()) {
            val list = currentCommand.commandList.map {
                it.commandPriority =
                    CommandPriority.HIGH
                it
            }.toMutableList()
            addWriteCharacteristic<B, Any>(
                UUID.fromString(GattAttributes.SERVICE_UUID),
                UUID.fromString(GattAttributes.GATT_UUID),
                list
            )
        } else {
            currentCommand.clearExecutedCommand()
        }
        nextCommand()
    }

    private fun hasCommandsToExecute(): Boolean {
        return !executionCommandQueue.isEmpty() ||
            !priorityExecutionCommandQueue.isEmpty() ||
            !lowPriorityExecutionCommandQueue.isEmpty()
    }

    fun post(r: Runnable?) {
        handler.post(r!!)
    }

    fun setFirmwareVersion(s: String) {
        firmwareVersion = s.replace("_", "-")
    }

    fun setBatteryLevel(batteryLevel: Int) {
        this.batteryLevel = batteryLevel
    }

    fun clearExecutedCommand() {
        commandQueueBusy = false
        if (currentCommand != null) {
            currentCommand!!.clearExecutedCommand()
        }
    }

    fun setConfirmedCommand(commandConfirmed: Boolean) {
        isCommandConfirmed = commandConfirmed
    }

    private enum class ConnectionStatus {
        CLOSED, CLOSING, CONNECTED, CONNECTING,  //        DISCONNECTED,
        DISCONNECTING, DISCOVERING, EXECUTING;

        val isConnecting: Boolean
            get() = this == CONNECTING || this == DISCOVERING || this == DISCONNECTING || this == CLOSING
    }

    // fun applyClose() {
    //     aapsLogger.info(LTag.PUMPBTCOMM, "applying close")
    //     if (commandsToAdd.isEmpty() && !hasCommandsToExecute() && connectionStatus != ConnectionStatus.CLOSED && connectionStatus != ConnectionStatus.CLOSING && connectionStatus != ConnectionStatus.CONNECTING) {
    //         if (System.currentTimeMillis() - lastGattConnection < 5000) {
    //             SystemClock.sleep(lastGattConnection - System.currentTimeMillis())
    //         }
    //         disconnect()
    //     }
    // }

    //
    //    public void clearCommands() {
    //        executionCommandQueue.clear();
    //    }
    //    public void clearCommands() {
    //        executionCommandQueueN.clear();
    //        executionCommandQueue.clear();
    //        close(true);
    //    }
    private class CommandsToAdd(val serviceUUID: UUID, val charaUUID: UUID, val command: MedLinkPumpMessage<*, *>) {

        override fun toString(): String {
            return "CommandsToAdd{" +
                "command=" + command.toString() +
                '}'
        }
    }

    private val notifyingCharacteristics: MutableSet<BluetoothGattCharacteristic> = HashSet()
    private var commandQueueBusy: Boolean? = null
    private val MAX_TRIES = 5
    private var isRetrying = false
    private val commandsToAdd = ConcurrentLinkedDeque<CommandsToAdd>()

    private enum class BLEOperationType {
        READ_CHARACTERISTIC_BLOCKING, WRITE_CHARACTERISTIC_BLOCKING, SEND_NOTIFICATION_BLOCKING
    }

    private var bleCommand: BleCommand? = null

    inner class Resp private constructor(private val func: Function<Any, MedLinkStandardReturn<*>>, private val command: String) {

        fun getFunc(): Function<Any, MedLinkStandardReturn<*>> {
            return func
        }

        override fun toString(): String {
            return "Resp{" +
                "command='" + command + '\'' +
                ", func=" + func +
                '}'
        }
    }

    @JvmField
    @Inject
    var medLinkUtil: MedLinkUtil? = null

    @JvmField
    @Inject
    var medLinkServiceData: MedLinkServiceData? = null
    private var previousLine = ""

    //    private long latestReceivedCommand = 0l;
    private var latestReceivedAnswer = 0L
    private var isConnected = false
    private var pumpModel: String? = null
    private val addedCommands: MutableSet<String> = HashSet()
    private val priorityExecutionCommandQueue = ConcurrentLinkedDeque<CommandExecutor<*>>()
    private val executionCommandQueue = ConcurrentLinkedDeque<CommandExecutor<*>>()
    private val lowPriorityExecutionCommandQueue = ConcurrentLinkedDeque<CommandExecutor<*>>()
    val onHoldCommandQueue = ConcurrentLinkedDeque<CommandExecutor<*>>()

    private var noResponse = 0

    //    private ConcurrentLinkedDeque<String> executionCommandQueueN = new ConcurrentLinkedDeque<>();
    var currentCommand: CommandExecutor<*>? = null
        private set
    private var mCurrentOperation: BLECommOperation? = null
    var resultActivity: MutableList<Resp> = ArrayList()
    private var servicesDiscovered = false
    fun getBluetoothAdapter(): BluetoothAdapter? {
        if (context.getSystemService(Context.BLUETOOTH_SERVICE) != null) {
            bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }
        return bluetoothAdapter
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var lastCharacteristic = ""
    private fun getGattStatusMessage(status: Int): String {
        return if (status == BluetoothGatt.GATT_SUCCESS) {
            "SUCCESS"
        } else if (status == BluetoothGatt.GATT_FAILURE) {
            "FAILED"
        } else if (status ==
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED
        ) {
            "NOT PERMITTED"
        } else if (status == 133) {
            "Found the strange 133 bug"
        } else {
            "UNKNOWN (\$status)"
        }
    }

    private fun removeNotificationCommand() {
        if (!priorityExecutionCommandQueue.isEmpty() &&
            MedLinkCommandType.Notification == priorityExecutionCommandQueue.first!!.getCurrentCommand()
        ) {
            priorityExecutionCommandQueue.pollFirst()
        }
    }

    private fun changeConnectionStatus(newStatus: ConnectionStatus) {
        if (newStatus != connectionStatus) {
            connectionStatus = newStatus
            connectionStatusChange = System.currentTimeMillis()
        }
    }

    //TODO 28/03 removed readcharacteristic from code test to see if it affect the execution
    fun readCharacteristicBlocking(serviceUUID: UUID?, charaUUID: UUID?): BLECommOperationResult {
        aapsLogger.info(LTag.PUMPBTCOMM, "readCharacteristic_blocking")
        val rval = BLECommOperationResult()
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: not configured!")
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
        } else {
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY
            } else {
                if (bluetoothConnectionGatt!!.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    val services = bluetoothConnectionGatt!!.services
                    rval.resultCode = BLECommOperationResult.RESULT_NONE
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported")
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    val chara = bluetoothConnectionGatt!!.getService(serviceUUID).getCharacteristic(
                        charaUUID
                    )

                    // Check if characteristic is valid
                    return if (chara == null) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "ERROR: Characteristic is 'null', ignoring read request")
                        rval
                    } else  // Check if this characteristic actually has READ property
                        if (chara.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
                            aapsLogger.error(LTag.PUMPBTCOMM, "ERROR: Characteristic cannot be read")
                            rval
                        } else {
                            // Enqueue the read command now that all checks have been passed
                            if (executionCommandQueue.isEmpty()) {
                                val result = executionCommandQueue.add(object : CommandExecutor<Any>(MedLinkCommandType.ReadCharacteristic, aapsLogger) {
                                    override fun run() {
                                        isCommandConfirmed = false
                                        try {
                                            synchronized(commandQueueBusy!!) {
                                                lastExecutedCommand = System.currentTimeMillis()
                                                if (!bluetoothConnectionGatt!!.readCharacteristic(chara)) {
                                                    aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: readCharacteristic failed for characteristic: %s", chara.uuid))
                                                    completedCommand()
                                                    if (chara.value != null) {
                                                        aapsLogger.info(LTag.PUMPBTCOMM, String(mCurrentOperation!!.value, StandardCharsets.UTF_8))
                                                    }
                                                } else {
                                                    aapsLogger.info(
                                                        LTag.PUMPBTCOMM, String.format(
                                                            "reading characteristic <%s>",
                                                            chara.uuid
                                                        )
                                                    )
                                                    needRetry = true
                                                    //                                        nrTries++;
                                                }
                                                commandQueueBusy = false
                                            }
                                        } catch (e: NullPointerException) {
                                            aapsLogger.info(LTag.PUMPBTCOMM, "npe need retry")
                                            needRetry = true
                                        }
                                        super.run()
                                    }
                                })

                                if (result) {
                                    nextCommand()
                                } else {
                                    aapsLogger.error(LTag.PUMPBTCOMM, "ERROR: Could not enqueue read characteristic command")
                                }
                            }
                            rval

                        }
                }
            }
            mCurrentOperation = null
        }
        return rval
    }

    private fun <B, C> addWriteCharacteristic(
        serviceUUID: UUID, charaUUID: UUID,
        command: MutableList<CommandStructure<B, BleCommand>>,
    ) {
//        this.latestReceivedCommand = System.currentTimeMillis();
        aapsLogger.info(LTag.PUMPBTCOMM, "commands")
        command[0].command.code?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }
        if (mCurrentOperation != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "busy for the command " + command[0].command.code)
        } else {

            if (isBolus(command[0].command) ||
                command[0].command.isSameCommand(MedLinkCommandType.StopStartPump) ||
                command[0].command.isSameCommand(MedLinkCommandType.CalibrateFrequencyArgument) ||
                !executionCommandQueue.flatMap { it.commandList.map { f -> f.command } }.contains(command[0].command)
            ) {
                var commandExecutor: CommandExecutor<B>
                synchronized(commandQueueBusy!!) { commandExecutor = buildCommandExecutor(charaUUID, serviceUUID, command) }
                aapsLogger.info(LTag.PUMPBTCOMM, "adding command" + command[0].command.code)
                if (command[0].commandPriority == CommandPriority.HIGH) {
                    if (command[0].command == MedLinkCommandType.Connect && !isConnected) {
                        priorityExecutionCommandQueue.addFirst(commandExecutor)
                    } else if (command[0].command != MedLinkCommandType.Connect && !priorityExecutionCommandQueue.contains(commandExecutor)) {
                        if (command.map { it.command }.contains(MedLinkCommandType.Calibrate)) {
                            priorityExecutionCommandQueue.addFirst(commandExecutor)
                        } else {
                            priorityExecutionCommandQueue.add(commandExecutor)
                        }
                    }
                } else if (command[0].commandPriority == CommandPriority.NORMAL && (executionCommandQueue.none { it.firstCommand() == commandExecutor.firstCommand() })) {
                    executionCommandQueue.add(commandExecutor)
                } else if (lowPriorityExecutionCommandQueue.none { it.firstCommand() == commandExecutor.firstCommand() }) {
                    lowPriorityExecutionCommandQueue.add(commandExecutor)
                }
            }
            //                    } else {
//                        aapsLogger.info(LTag.PUMPBTCOMM, "not adding command" + new String(command, UTF_8));
//                        if (bluetoothConnectionGatt == null) {
//                            medLinkConnect();
//
//                        }
//                    }
        }
        //            }
//        } else {
//            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!");
//            rval.resultCode = RESULT_NOT_CONFIGURED;
//        }
//        if (rval.value == null) {
//            rval.value = getPumpResponse();
//        }
        return
    }

    private fun <B> buildCommandExecutor(
        charaUUID: UUID, serviceUUID: UUID,
        commands: MutableList<CommandStructure<B,
            BleCommand>>,
    ):
        CommandExecutor<B> {
        return if (MedLinkCommandType.BolusStatus.isSameCommand(commands[0].command) && commands[0].commandHandler.isPresent && commands[0].parseFunction.get() is BolusProgressCallback) {
            object : ContinuousCommandExecutor<B>(commands, aapsLogger) {
                override fun run() {
                    if (this.nextCommand() == MedLinkCommandType.NoCommand) {
                        return
                    }
                    isCommandConfirmed = false
                    val lastReceived = lastReceivedCharacteristic
                    lastExecutedCommand = System.currentTimeMillis()
                    if (bluetoothConnectionGatt != null) {
                        val chara = bluetoothConnectionGatt!!.getService(serviceUUID)
                            .getCharacteristic(charaUUID)

                        chara.value = this.nextRaw()
                        //                                    chara.setWriteType(PROPERTY_WRITE); //TODO validate
                        nrRetries++
                        aapsLogger.debug(LTag.PUMPBTCOMM, "running command")
                        commands[0].command.code?.let { aapsLogger.debug(LTag.PUMPBTCOMM, it) }
                        val count = 0
                        //                        while (lastReceived == lastReceivedCharacteristic && count < MAX_TRIES) {
                        if (bluetoothConnectionGatt == null || !bluetoothConnectionGatt!!.writeCharacteristic(chara)) {
                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeCharacteristic failed for characteristic: %s", chara.uuid))
                            needRetry = true
                            commandQueueBusy = false
                            //                                break;
                        } else {
                            needRetry = false
                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("writing <%s> to characteristic <%s>", commands[0].command.code, chara.uuid))
                        }
                        //                            count++;
//                            SystemClock.sleep(4000);
//                        }
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, "connection gatt is null need retry")
                        needRetry = true
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, "ZZZZZZZZZZZZZZZZZZZZZ")
                    if (needRetry && connectionStatus != ConnectionStatus.DISCOVERING) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "needretry")
                        if (currentCommand != null) {
                            clearExecutedCommand()
                        }
                        needRetry = false
                        disconnect()
                    }
                    super.run()
                }
            }
        } else {
            object : CommandExecutor<B>(commands, aapsLogger) {
                override fun run() {
                    isCommandConfirmed = false
                    if (System.currentTimeMillis() - lastExecutedCommand < 2000) {
                        SystemClock.sleep(2200 - (System.currentTimeMillis() - lastExecutedCommand))
                    }
                    lastExecutedCommand = System.currentTimeMillis()
                    if (bluetoothConnectionGatt != null && bluetoothConnectionGatt!!.getService(serviceUUID) != null) {
                        val chara = bluetoothConnectionGatt!!.getService(serviceUUID)
                            .getCharacteristic(charaUUID)
                        chara.value = nextCommandData()
                        //                                    chara.setWriteType(PROPERTY_WRITE); //TODO validate
//                    nrRetries++;
                        aapsLogger.info(LTag.PUMPBTCOMM, "running command")
                        aapsLogger.info(LTag.PUMPBTCOMM, String(nextCommandData()))
                        val count = 0
                        //                        while (lastReceived == lastReceivedCharacteristic && count < MAX_TRIES) {
                        if (bluetoothConnectionGatt == null || !bluetoothConnectionGatt!!.writeCharacteristic(chara)) {
                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeCharacteristic failed for characteristic: %s", chara.uuid))
                            needRetry = true
                            commandQueueBusy = false
                            //                                break;
                        } else {
                            needRetry = false
                            aapsLogger.info(
                                LTag.PUMPBTCOMM,
                                String.format("writing <%s> to characteristic <%s>", String(nextCommandData(), StandardCharsets.UTF_8), chara.uuid)
                            )
                        }
                        //                            count++;
//                            SystemClock.sleep(4000);
//                        }
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, "connection gatt is null need retry")
                        needRetry = true
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, "ZZZZZZZZZZZZZZZZZZZZZ")
                    if (needRetry && connectionStatus != ConnectionStatus.DISCOVERING) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "needretry")
                        if (currentCommand != null) {
                            clearExecutedCommand()
                        }
                        needRetry = false
                        close(true)
                    }
                    super.run()
                }
            }
        }
    }

    @Synchronized
    fun <B, C> addWriteCharacteristic(serviceUUID: UUID, charaUUID: UUID, msg: MedLinkPumpMessage<B, C>) {
        aapsLogger.info(LTag.PUMPBTCOMM, "writeCharblocking")
        msg.firstCommand().code?.let { aapsLogger.info(LTag.PUMPBTCOMM, it) }
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt)
        aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + msg.btSleepTime)
        if ((//connectionStatus == ConnectionStatus.DISCONNECTING ||
                connectionStatus != ConnectionStatus.CONNECTED) && System.currentTimeMillis() - connectionStatusChange > 60000) {
            disconnect()
        }
        if (msg.btSleepTime > 0) {
            sleepSize = msg.btSleepTime
        }
        addCommands(serviceUUID, charaUUID, msg)
        aapsLogger.info(LTag.PUMPBTCOMM, "before connect")
        if (!connectionStatus.isConnecting || System.currentTimeMillis() - connectionStatusChange > 40000) {
            medLinkConnect()
        } else if (!connectionStatus.isConnecting  && connectionStatus != ConnectionStatus.DISCONNECTING) {
            disconnect() ///TODO rethink
        }
    }

    fun <B, C> needToBeStarted(serviceUUID: UUID, charaUUID: UUID, command: MedLinkCommandType?) {
        if (commandNeedActivePump(command)) {
            if (currentCommand != null && currentCommand!!.secondCommand() == MedLinkCommandType.StopPump) {
                if (!currentCommand!!.hasFinished()) {
                    currentCommand!!.clearExecutedCommand()
                    currentCommand = null
                }
            }
            // if (!containsStart()) {
            //     val commands: MutableList<Quadruple<Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>, Optional<BleCommand>>> = mutableListOf(
            //         Quadruple(
            //             MedLinkCommandType.StopStartPump, Optional.empty<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>(), Optional.of(
            //                 BleStartCommand(
            //                     aapsLogger,
            //                     medLinkServiceData, null
            //                 )
            //             )
            //         ),
            //         Quadruple(MedLinkCommandType.StartPump, Optional.empty<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>(), Optional.empty())
            //     )
            //
            //     addWriteCharacteristic<B, C>(serviceUUID, charaUUID, commands, CommandPriority.HIGH)
            // }
        }
        if (lastPumpStatus === PumpRunningState.Suspended && lowPriorityExecutionCommandQueue.isEmpty() && executionCommandQueue.any { it.contains(MedLinkCommandType.StartPump) }) {
            // val commands: MutableList<Quadruple<Optional<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>, Optional<BleCommand>>> = mutableListOf(
            //     Quadruple(
            //         MedLinkCommandType.StopStartPump, Optional.empty<Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>>(), Optional.of(
            //             BleStopCommand(
            //                 aapsLogger,
            //                 medLinkServiceData, null
            //             )
            //         )
            //     ),
            //     Quadruple(MedLinkCommandType.StopPump, Optional.empty(), Optional.empty()),
            //
            //     )
            // addWriteCharacteristic<B, C>(serviceUUID, charaUUID, commands, CommandPriority.LOWER)
        }
    }

    private fun removeStopCommands(priority: Boolean) {
        aapsLogger.info(LTag.PUMPBTCOMM, "removing stop")
        removeCommandFromQueue(MedLinkCommandType.StopPump, executionCommandQueue)
        if (!priority) {
            removeCommandFromQueue(MedLinkCommandType.StopPump, lowPriorityExecutionCommandQueue)
        }
    }

    private fun removeCommandFromQueue(
        commandType: MedLinkCommandType,
        queue: ConcurrentLinkedDeque<CommandExecutor<*>>,
    ) {
        val toRemove = queue.filter { it -> it?.hasFinished() == false && it.contains(commandType) }
        aapsLogger.info(LTag.PUMPBTCOMM, commandType.toString())
        queue.removeAll(
            toRemove
        )
    }

    private fun containsStart(): Boolean {
        val startMsg = MedLinkCommandType.StartPump
        return priorityExecutionCommandQueue.map { it.contains(startMsg) }.isNotEmpty()
    }

    private fun commandNeedActivePump(commandType: MedLinkCommandType?): Boolean {
        return commandType?.needActivePump == true
    }

    fun isBolus(commandType: MedLinkCommandType?): Boolean {
        return MedLinkCommandType.Bolus.isSameCommand(commandType) ||
            MedLinkCommandType.SMBBolus.isSameCommand(commandType) ||
            MedLinkCommandType.TBRBolus.isSameCommand(commandType)
    }

    private fun <B, C> addCommands(serviceUUID: UUID, charaUUID: UUID, msg: MedLinkPumpMessage<B, C>) {
        synchronized(commandsToAdd) {
            // val command = CommandsToAdd(serviceUUID, charaUUID, msg)
            //            addCommand(command);

            addWriteCharacteristic<B, C>(serviceUUID, charaUUID, msg.commands)
            if (msg.supplementalCommands.isNotEmpty()) {
                addWriteCharacteristic<C, C>(serviceUUID, charaUUID, msg.supplementalCommands)
            }
            handleBolusCommand(msg, serviceUUID, charaUUID)
            handleCalibrateCommand(msg, serviceUUID, charaUUID)
            handleCalibrateFrequencyCommand(msg, serviceUUID, charaUUID)
            needToBeStarted<B, C>(serviceUUID, charaUUID, msg.commands[0].command)
        }
    }

    private fun <B, C> handleCalibrateFrequencyCommand(msg: MedLinkPumpMessage<B, C>, serviceUUID: UUID, charaUUID: UUID) {
        // if (msg is CalibrateFrequencyMedLinkMessage) {
        //     val validation = msg.calibrateVerificationMessage
        //     addWriteCharacteristic<Stream<JSONObject>, Any>(serviceUUID, charaUUID, validation.commands, MedLinkBLE.CommandPriority.HIGH)
        // }
    }

    private fun <B, C> handleCalibrateCommand(msg: MedLinkPumpMessage<B, C>, serviceUUID: UUID, charaUUID: UUID) {
        if (msg.contains(MedLinkCommandType.Calibrate)) {
            removeStopCommands(true)
            // if (msg is BolusMedLinkMessage) {
            //     val bolusStatus = msg.bolusProgressMessage
            //     if (bolusStatus != null) {
            //         addWriteCharacteristic<String, Any>(serviceUUID, charaUUID, bolusStatus.commands, CommandPriority.NORMAL)
            //     }
            // }
        }
    }

    private fun handleBolusCommand(msg: MedLinkPumpMessage<*, *>, serviceUUID: UUID, charaUUID: UUID) {
        if (commandNeedActivePump(msg.firstCommand())) {
            removeStopCommands(true)
            // if (msg is BolusMedLinkMessage) {
            //     val bolusStatus = msg.bolusProgressMessage
            //     if (bolusStatus != null) {
            //         addWriteCharacteristic(serviceUUID, charaUUID, bolusStatus, CommandPriority.NORMAL)
            //     }
            // }
        }
    }

    @SuppressLint("MissingPermission") fun connectGatt() {
        lastGattConnection = System.currentTimeMillis()
        changeConnectionStatus(ConnectionStatus.CONNECTING)
        aapsLogger.info(LTag.PUMPBTCOMM, "Connecting gatt")
        if (medLinkDevice == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device is null, can't do connectGatt.")
            return
        }
        bluetoothConnectionGatt = medLinkDevice!!.connectGatt(
            context, false,
            bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE
        )
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to connect to Bluetooth Low Energy device at " + bluetoothAdapter!!.address)
        } else {
            gattConnected = true
            if (gattDebugEnabled) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Gatt Connected.")
            }
            val deviceName = bluetoothConnectionGatt!!.device.name
            if (StringUtils.isNotEmpty(deviceName)) {
                // Update stored name upon connecting (also for backwards compatibility for device where a name was not yet stored)
                sp.putString(RileyLinkConst.Prefs.RileyLinkName, deviceName)
            } else {
                sp.remove(RileyLinkConst.Prefs.RileyLinkName)
            }
            if (this.medLinkServiceData != null) {
                this.medLinkServiceData?.rileylinkName = deviceName
                this.medLinkServiceData?.rileylinkAddress =
                    bluetoothConnectionGatt!!.device.address
            }
        }
    }

    //    private void updateActivity(Function resultActivity, String command) {
//        if (this.resultActivity != null) {
//            aapsLogger.info(LTag.PUMPBTCOMM, command + " resultActivity added " + this.resultActivity.toString());
//        }
//        this.resultActivity.add(new Resp(resultActivity, command));
//    }
//    public void addExecuteCommandToCommands() {
//        Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> ret = s -> {
//            return new MedLinkStandardReturn<String>(s, "");
//        };
//        MedLinkPumpMessage<String> msg = new MedLinkPumpMessage<String>(MedLinkCommandType.Connect,
//                MedLinkCommandType.NoCommand,
//                ret, medLinkServiceData, aapsLogger, sleepSize);
//        addCommands(UUID.fromString(GattAttributes.SERVICE_UUID),
//                UUID.fromString(GattAttributes.GATT_UUID),
//                msg, true);
//    }
    private fun addExecuteConnectCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "ad ok conn command ")
        addWriteCharacteristic<Any, Any>(
            UUID.fromString(GattAttributes.SERVICE_UUID),
            UUID.fromString(GattAttributes.GATT_UUID),
            // MedLinkPumpMessage<String>(
            //     MedLinkCommandType.Connect,
            //     BleConnectCommand(aapsLogger, medLinkServiceData)
            // ),
            mutableListOf(
                CommandStructure(
                    MedLinkCommandType.Connect, Optional.empty<Function<Supplier<Stream<String>>, MedLinkStandardReturn<Any>>>(), Optional
                        .of(BleConnectCommand(aapsLogger, medLinkServiceData!!, null)),
                    commandPriority = CommandPriority.HIGH)
            )
        )
    }

    private var connectTries = 0
    private fun medLinkConnect() {
        if (connectTries > MAX_TRIES) {
            aapsLogger.info(LTag.PUMPBTCOMM, "max connection tries")
            return
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "connecting medlink")
        aapsLogger.info(LTag.PUMPBTCOMM, "" + (System.currentTimeMillis() - lastCloseAction))
        aapsLogger.info(LTag.PUMPBTCOMM, "" + (System.currentTimeMillis() - lastExecutedCommand))
        aapsLogger.info(LTag.PUMPBTCOMM, "" + gattConnected)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected())
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isDiscovering)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + connectionStatus)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + sleepSize)
        if (connectionStatus == ConnectionStatus.CLOSED && !connectionStatus.isConnecting && System.currentTimeMillis() - lastCloseAction <= 5000 &&
            !gattConnected && bluetoothConnectionGatt != null
        ) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing")
            commandQueueBusy = false
            close()
            return
        } else if (connectionStatus != ConnectionStatus.CLOSED && !connectionStatus.isConnecting && System.currentTimeMillis() - lastConfirmedCommand > 120000 && System.currentTimeMillis() - lastCloseAction > 500000 && bluetoothConnectionGatt != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing")
            commandQueueBusy = false
            close()
            return
        } else if (connectionStatus == ConnectionStatus.EXECUTING || connectionStatus == ConnectionStatus.CONNECTED ||
            connectionStatus == ConnectionStatus.CONNECTING &&
            hasCommandsToExecute() && priorityExecutionCommandQueue.peekFirst() != null &&
            priorityExecutionCommandQueue.peekFirst()?.nextCommand() == MedLinkCommandType.Notification
        ) {
            aapsLogger.info(LTag.PUMPBTCOMM, "nextcommand")
            nextCommand()
            return
        } else if (connectionStatus == ConnectionStatus.EXECUTING &&
            (lastExecutedCommand - System.currentTimeMillis() > 500000 ||
                lastCloseAction - System.currentTimeMillis() > 500000)
        ) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing")
            commandQueueBusy = false
            disconnect()
        } else if (System.currentTimeMillis() - connectionStatusChange > 180000) {
            aapsLogger.info(LTag.PUMPBTCOMM, "connection status changed")

            close(true)
            return
        } else if (connectionStatus.isConnecting) {
            return
        }
        synchronized(connectionStatus) {
            lastCharacteristic = ""
            if (connectionStatus == ConnectionStatus.CLOSED) {
                changeConnectionStatus(ConnectionStatus.CONNECTING)
                val sleep = System.currentTimeMillis() - lastCloseAction
                if (sleep < sleepSize) {
                    SystemClock.sleep(sleepSize - sleep)
                }
                aapsLogger.info(LTag.PUMPBTCOMM, "connecting gatt")
                connectGatt()
                //                if(currentCommand)
                if (bluetoothConnectionGatt == null) {
                    connectTries++
                    medLinkConnect()
                } else {
                    connectTries = 0
                }
            } else {
                pumpResponse = StringBuffer()
            }
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "ending medlinkconnect")
    }

    fun enableNotifications(): Boolean {
        val result = setNotificationBlocking(
            UUID.fromString(GattAttributes.SERVICE_UUID),  //
            UUID.fromString(GattAttributes.GATT_UUID), false
        )
        if (result.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error setting response count notification")
            return false
        }
        return true
    }

    private fun setNotificationBlocking(serviceUUID: UUID?, charaUUID: UUID?, disable: Boolean): BLECommOperationResult {
        aapsLogger.debug("Enable medlink notification")
        aapsLogger.info(LTag.PUMPBTCOMM, "Enable medlink notification")
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + mCurrentOperation)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + connectionStatus)
        val rval = BLECommOperationResult()
        if (bluetoothConnectionGatt == null) {
            medLinkConnect()
        }
        if (bluetoothConnectionGatt != null) {
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY
            } else {
                if (bluetoothConnectionGatt!!.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported")
                    //                    close();
                    disconnect()
                } else {
                    val characteristic = bluetoothConnectionGatt!!.getService(serviceUUID)
                        .getCharacteristic(charaUUID)
                    // Tell Android that we want the notifications
                    bluetoothConnectionGatt!!.setCharacteristicNotification(characteristic, true)
                    val list = characteristic!!.descriptors
                    //                    if (gattDebugEnabled) {
                    for (i in list.indices) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "Found descriptor: " + list[i].uuid)
                        aapsLogger.info(LTag.PUMPBTCOMM, "Found descriptor: " + list[i].characteristic.uuid)
                    }
                    //                    }
                    val descriptor = list[0]
                    // Tell the remote device to send the notifications

                    /////////////////////////////////////////////////////
                    // Check if characteristic is valid
                    if (characteristic == null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "ERROR: Characteristic is 'null', ignoring setNotify request")
                        rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
                        return rval
                    }

                    // Get the CCC Descriptor for the characteristic
//                    final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID));
                    if (descriptor == null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.uuid))
                        rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
                        return rval
                    }

                    // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
                    val value: ByteArray
                    val properties = characteristic.properties
                    if (disable) {
                        value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    } else if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
                        value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.uuid))
                        rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
                        return rval
                    }

//                    if (needToAddConnectCommand()) {
//                        addExecuteConnectCommand();
//                    }

                    // Queue Runnable to turn on/off the notification now that all checks have been passed
                    if (!notificationEnabled
                        && priorityExecutionCommandQueue.stream().noneMatch { it.firstCommand() == MedLinkCommandType.Notification }
                        && lastReceivedCharacteristic < lastGattConnection
                    ) {
//                        if (executionCommandQueue.stream().noneMatch(f -> f.contains(MedLinkCommandType.Notification))) {
                        executionCommandQueue.stream().findFirst().map {
                            it.clearExecutedCommand()
                            false
                        }
                        priorityExecutionCommandQueue.addFirst(object : CommandExecutor<Any>(
                            MedLinkCommandType.Notification, aapsLogger
                        ) {
                            override fun run() {
                                isCommandConfirmed = false
                                // First set notification for Gatt object
                                synchronized(commandQueueBusy!!) {
                                    if (bluetoothConnectionGatt != null) {
                                        try {
                                            if (!bluetoothConnectionGatt!!.setCharacteristicNotification(descriptor.characteristic, true)) {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.uuid))
                                            }
                                            nrRetries++
                                            // Then write to descriptor
                                            descriptor.value = value
                                            val result: Boolean
                                            result = bluetoothConnectionGatt!!.writeDescriptor(descriptor)
                                            notificationEnabled = true
                                            if (!result) {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.uuid))
                                                needRetry = true
                                            } else {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format(" descriptor written: %s", descriptor.uuid))
                                                needRetry = false
                                                commandQueueBusy = false
                                                priorityExecutionCommandQueue.poll()
                                                //                                            SystemClock.sleep(6000);
//                                            if (needToAddConnectCommand()) {
//                                                addExecuteConnectCommand();
//                                            }
                                            }
                                        } catch (e: NullPointerException) {
                                            aapsLogger.info(LTag.PUMPBTCOMM, "npe  need retry")
                                            needRetry = true
                                        }
                                    } else {
                                        aapsLogger.info(LTag.PUMPBTCOMM, "connection gatt is null need retry")
                                        needRetry = true
                                    }
                                }
                                super.run()
                            }
                        })
                        //                        }
                    }
                    //                    if (result) {
                    nextCommand()
                    rval.resultCode = BLECommOperationResult.RESULT_SUCCESS

//                    } else {
//                        aapsLogger.info(LTag.PUMPBTCOMM, "ERROR: Could not enqueue write command");
//                        rval.resultCode = RESULT_NOT_CONFIGURED;
//                    }

                    //////////////////////////////////////////////////////
//                    mCurrentOperation = new DescriptorWriteOperation(aapsLogger, bluetoothGatt, descr,
//                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                    mCurrentOperation.execute(this);
//                    if (mCurrentOperation.timedOut) {
//                        rval.resultCode = BLECommOperationResult.RESULT_TIMEOUT;
//                    } else if (mCurrentOperation.interrupted) {
//                        rval.resultCode = BLECommOperationResult.RESULT_INTERRUPTED;
//                    } else {
//                        rval.resultCode = BLECommOperationResult.RESULT_SUCCESS;
//                        notificationEnabled = true;
////                        nextCommand();
//
//                    }
//                }
                    aapsLogger.info(LTag.PUMPBTCOMM, "nulling currentoperation")
                    mCurrentOperation = null
                    return rval
                }
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "setNotification_blocking: not configured!")
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
        }
        return rval
    }

    fun isConnected(): Boolean {
        return isConnected
    }

    fun setConnected(connected: Boolean) {
        isConnected = connected
        gattConnected = connected
        if (!connected) {
            pumpResponse = StringBuffer()
        } else {
            lastConnection = System.currentTimeMillis()
            changeConnectionStatus(ConnectionStatus.CONNECTED)
        }
    }

    fun setPumpModel(pumpModel: String?) {
        this.pumpModel = pumpModel
    }

    fun findMedLink(medLinkAddress: String) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "MedLink address: $medLinkAddress")
        // Must verify that this is a valid MAC, or crash.
        if (bleCommand == null) {
            bleCommand = BleCommand(aapsLogger, medLinkServiceData!!)
        }
        getBluetoothAdapter()
        medLinkDevice = bluetoothAdapter!!.getRemoteDevice(medLinkAddress)
        // if this succeeds, we get a connection state change callback?
        if (medLinkDevice != null) {
            connectGatt()
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device not found with address: $medLinkAddress")
        }
    }

    private var bluetoothConnectionGatt: BluetoothGatt? = null
    fun disconnect() {
        if (connectionStatus == ConnectionStatus.DISCONNECTING) {
            close(true)
        } else {
            changeConnectionStatus(ConnectionStatus.DISCONNECTING)
            servicesDiscovered = false
            pumpResponse = StringBuffer()
            aapsLogger.warn(LTag.PUMPBTCOMM, "Closing GATT connection")
            // Close old connection
            if (bluetoothConnectionGatt != null) {
                // Not sure if to disconnect or to close first..
                bluetoothConnectionGatt!!.disconnect()
                manualDisconnect = true
            } else {
                changeConnectionStatus(ConnectionStatus.CLOSED)
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "Post disconnect")
            setConnected(false)
            isDiscovering = false
            commandQueueBusy = false
        }
    }

    @JvmOverloads
    fun close(force: Boolean = false) {
        aapsLogger.info(LTag.PUMPBTCOMM, "closing")
        if (currentCommand != null && currentCommand!!.hasFinished() && currentCommand!!.nextFunction() != null) {
            currentCommand!!.nextBleCommand().map { it.applyResponse(this) }
            removeFirstCommand(true)
        } else if (currentCommand != null &&
            currentCommand!!.commandList.any { it -> isBolus(it.command) }
        ) {
            onHoldCommandQueue.add(currentCommand)
            removeFirstCommand(true)
            needToCheckOnHold = true
        }
        if (force) {
            if(connectionStatus != ConnectionStatus.DISCONNECTING) {
                disconnect()
            }
            if (System.currentTimeMillis() - lastConfirmedCommand < 6000 && currentCommand != null && MedLinkCommandType.Connect.isSameCommand(
                    currentCommand!!.getCurrentCommand()
                )
            ) {
                aapsLogger.info(LTag.PUMPBTCOMM, "connecting gatt")
                connectGatt()
                return
            }
        }
        //        this.tryingToClose++;
        aapsLogger.info(LTag.EVENTS, "" + commandQueueBusy)
        aapsLogger.info(LTag.EVENTS, "" + (lastConnection - System.currentTimeMillis()))
        if (commandQueueBusy!!) {
            aapsLogger.info(LTag.EVENTS, "trying to close to close")
            return
        }
        changeConnectionStatus(ConnectionStatus.CLOSING)
        setConnected(false)
        servicesDiscovered = false
        isDiscovering = false
        notificationEnabled = false
        aapsLogger.info(LTag.EVENTS, "closing")
        if (bluetoothConnectionGatt != null) {
            bluetoothConnectionGatt!!.close()
            bluetoothConnectionGatt = null
        }
        changeConnectionStatus(ConnectionStatus.CLOSED)
        lastCloseAction = System.currentTimeMillis()
        previousLine = ""
        if (!hasCommandsToExecute() && commandsToAdd.isEmpty()) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Remaining commands")
            resultActivity.clear()
            addedCommands.clear()
            pumpResponse = StringBuffer()
            if (currentCommand != null) {
                if (currentCommand!!.hasFinished()) {
                    removeFirstCommand(false)
                } else {
                    currentCommand!!.clearExecutedCommand()
                }
            }
            currentCommand = null
            latestReceivedAnswer = 0L
            lastCharacteristic = ""
        } else {
            if (currentCommand != null) {
                if (currentCommand!!.hasFinished()) {
                    removeFirstCommand(false)
                } else {
                    currentCommand?.clearExecutedCommand()
                }
            }
            currentCommand = null
            medLinkConnect()
        }
        aapsLogger.info(LTag.EVENTS, "ending close")
    }

    fun completedCommand() {
        completedCommand(false)
    }

    fun completedCommand(force: Boolean) {
        lastCharacteristic = ""
        if (currentCommand != null && currentCommand!!.nrRetries > MAX_TRIES) {
            aapsLogger.info(LTag.PUMPBTCOMM, "maxtries")
            aapsLogger.info(LTag.PUMPBTCOMM, "removing command")
            currentCommand?.commandFailed()
            removeFirstCommand(true)
            disconnect()
            return
        } else if (currentCommand != null && currentCommand!!.firstCommand() == MedLinkCommandType.CalibrateFrequencyArgument) {
            calibrateCommand = currentCommand
        } else if (calibrateCommand != null) {
            // addWriteCharacteristic(calibrateCommand.medLinkPumpMessage)
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "completed command")
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + priorityExecutionCommandQueue.size)
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + executionCommandQueue.size)
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + lowPriorityExecutionCommandQueue.size)
        commandQueueBusy = false
        isRetrying = false
        removeFirstCommand(force)
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + priorityExecutionCommandQueue.size)
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + executionCommandQueue.size)
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + lowPriorityExecutionCommandQueue.size)
        currentCommand = null
        lastCharacteristic = ""
        val com = nextCommand
        if (hasCommandsToExecute() && MedLinkCommandType.Connect == com?.firstCommand() && isConnected) {
            aapsLogger.info(LTag.PUMPBTCOMM, "completed command")
            priorityExecutionCommandQueue.remove(com)
        }
        //        if (com != null) {
//            RemainingBleCommand remCom = com.getRemainingBleCommand();
//            if (remCom != null) {
//                String commandCode = new String(remCom.getCommand(), UTF_8);
//                addedCommands.remove(commandCode);
//                if (remCom.hasArg()) {
//                    CommandExecutor commArg = executionCommandQueue.peek();
//                    if (commArg != null && commArg.getRemainingBleCommand() != null) {
//                        addedCommands.remove(new String(commArg.getRemainingBleCommand().getCommand(), UTF_8));
//                    }
//                }
//            }
//        }
//        executionCommandQueue.poll();
//         processCommandToAdd()
        nextCommand()
    }

    private val nextCommand: CommandExecutor<*>?
        get() {
            if (!hasCommandsToExecute()) {
                return null
            } else if (!priorityExecutionCommandQueue.isEmpty()) {
                return priorityExecutionCommandQueue.peek()
            } else if (!executionCommandQueue.isEmpty()) {
                return executionCommandQueue.peek()
            } else if (!lowPriorityExecutionCommandQueue.isEmpty()) {
                return lowPriorityExecutionCommandQueue.peek()
            }
            return null
        }

    //
//    public void retryCommand(MedLinkCommandType commandType) {
//        if (arguments.get(commandType) != null) {
//            MedLinkPumpMessage msg = arguments.remove(commandType);
//
//        }
//    }
    fun retryCommand() {
        lastCharacteristic = ""
        commandQueueBusy = false
        if (currentCommand != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Retrying " + currentCommand!!.nrRetries)
        }
        if (hasCommandsToExecute()) {
            if (currentCommand != null && currentCommand!!.nrRetries >= MAX_TRIES) {
//                currentCommand.clearExecutedCommand();
                // Max retries reached, give up on this one and proceed
                aapsLogger.error(LTag.PUMPBTCOMM, "Max number of tries reached")
                removeFirstCommand(true)
                completedCommand()
                currentCommand = null
                //            } else if (currentCommand == null) {

//            } else if (currentCommand.getCommand().isSameCommand(MedLinkCommandType.Bolus)) {
//                completedCommand();
            } else {
                nextCommand()
                isRetrying = true
            }
            //            disconnect();
        }
        //        if (!isConnected && !connectAdded && this.currentCommand != null &&
//                this.currentCommand.getCommand() != MedLinkCommandType.Connect.getRaw() &&
//                needToAddConnectCommand()
//        ) {
//            if (executionCommandQueue.peek().getRemainingBleCommand() != null &&
//                    executionCommandQueue.peek().getRemainingBleCommand().getCommand() != null) {
//                aapsLogger.info(LTag.PUMPBTCOMM,
//                        new String(executionCommandQueue.peek().getRemainingBleCommand().getCommand(), UTF_8));
//            }
//            aapsLogger.info(LTag.PUMPBTCOMM, "add connect command");
//            addExecuteCommandToCommands();
//        }
    }

    private fun needToAddConnectCommand(): Boolean {
        aapsLogger.info(LTag.PUMPBTCOMM, "" + commandsToAdd.size)
        aapsLogger.info(LTag.PUMPBTCOMM, "" + executionCommandQueue.size)
        return (lastConnection == 0L && !hasCommandsToExecute() //                connectionStatus != ConnectionStatus.DISCOVERING &&
            || (!isConnected
            &&
            hasCommandsToExecute() && nextCommand!!.nextCommand() != MedLinkCommandType.NoCommand && //
            //             !isBolus(executionCommandQueue.peek().nextCommand()) &&
            !MedLinkCommandType.Connect.isSameCommand(nextCommand!!.nextCommand()) &&
            !MedLinkCommandType.ReadCharacteristic.isSameCommand(nextCommand!!.nextCommand()) &&  //                                !MedLinkCommandType.StopStartPump.isSameCommand(executionCommandQueue.peek().nextCommand()) &&
            !MedLinkCommandType.Notification.isSameCommand(nextCommand!!.nextCommand()) &&
            priorityExecutionCommandQueue.stream().noneMatch { f: CommandExecutor<*>? -> f!!.contains(MedLinkCommandType.Connect) }))
    }

    @Synchronized
    fun nextCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand $servicesDiscovered")
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand $commandQueueBusy")
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand $connectionStatus")
        if (currentCommand != null && currentCommand!!.nrRetries > MAX_TRIES) {
            removeFirstCommand(true)
        }
        printBuffer()
        if (connectionStatus == ConnectionStatus.EXECUTING || connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.DISCOVERING) {
            // If there is still a command being executed then bail out
            aapsLogger.info(LTag.PUMPBTCOMM, "CommandQueueBusy $commandQueueBusy")
            aapsLogger.info(LTag.PUMPBTCOMM, "bluetoothConnectionGatt $bluetoothConnectionGatt")
            if (commandQueueBusy!!) {
                return
            } else if (currentCommand != null && currentCommand!!.hasFinished()) {
                removeFirstCommand(false)
            }
            // Check if we still have a valid gatt object
            try {
                if (bluetoothConnectionGatt == null) {
                    aapsLogger.error(LTag.PUMPBTCOMM, String.format("ERROR: GATT is 'null' for peripheral '%s', clearing command queue", "Medlink"))
                    priorityExecutionCommandQueue.clear()
                    executionCommandQueue.clear()

//                commandQueueBusy = false;
                    return
                }
                // processCommandToAdd()
                // Execute the next command in the queue
                if (nextCommand != null) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "Queue size greater than 0 " + executionCommandQueue.size)
                    currentCommand = nextCommand
                    //                    aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand.hasFinished());
//                    if (currentCommand != null && currentCommand.nextCommand() != null &&
//                            connectionStatus == ConnectionStatus.EXECUTING &&
//                            currentCommand.nextCommand().isSameCommand(MedLinkCommandType.Connect)) {
//                        nextCommand();
//                        return;
//                    } else {
                    if (needToAddConnectCommand()) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "need to add")
                        addExecuteConnectCommand()
                        currentCommand = nextCommand
                        nextCommand()
                        return
                    }
                    commandQueueBusy = true
                    if (currentCommand != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString())
                        bleHandler.post(currentCommand!!)
                    }
                } else if (isConnected) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "empty execution queue")
                    // disconnect()
                }
            } catch (e: NullPointerException) {
                e.printStackTrace()
                return
            }
        }
    }

    private val allCommands: Stream<CommandExecutor<*>>
        get() = Stream.concat(
            Stream.concat(
                priorityExecutionCommandQueue.stream(),
                executionCommandQueue.stream()
            ), lowPriorityExecutionCommandQueue.stream()
        )

    fun printBuffer() {
        val buf = StringBuilder("Print buffer")
        buf.append("\n")
        val it: MutableIterator<CommandExecutor<*>?> = allCommands.map { f: CommandExecutor<*> -> return@map f }.filter(
            { obj: CommandExecutor<*>? -> Objects.nonNull(obj) }).iterator()
        while (it.hasNext()) {
            buf.append(it.next().toString())
            buf.append("\n")
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "commands to add")
        val it1: Iterator<CommandsToAdd> = commandsToAdd.iterator()
        while (it1.hasNext()) {
            buf.append(it1.next().command.commands[0].command.code)
            buf.append("\n")
        }
        aapsLogger.info(LTag.PUMPBTCOMM, buf.toString())
    }

    fun discoverServices(): Boolean {
        aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name)
        if (connectionStatus == ConnectionStatus.CLOSED) {
            if (bluetoothConnectionGatt == null) return false else if (bluetoothConnectionGatt!!.discoverServices() == true) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Starting to discover GATT Services.")
                return true
            } else {
                aapsLogger.error(LTag.PUMPBTCOMM, "Cannot discover GATT Services.")
            }
        }
        return false
    }

    fun clearNoResponse() {
        noResponse = 0
    }

    fun pumpConnectionError() {
        noResponse++
        if (noResponse > 2) {
            medLinkUtil?.sendBroadcastMessage(MedLinkConst.Intents.MedLinkConnectionError, context)
        }
    }

    companion object {

        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    init {
        val that = this
        handlerThread.start()
        characteristicThread.start()
        bleHandler = Handler(handlerThread.looper)
        handler = Handler(characteristicThread.looper)
        bluetoothGattCallback = object : BluetoothGattCallback() {
            private fun processCharacteristics(buffer: StringBuffer, answer: String): Array<String> {
                return if (answer.contains("\n")) {
                    val index = answer.indexOf("\n")
                    buffer.append(answer.substring(0, index + 1))
                    processCharacteristics(buffer, answer.substring(index + 1))
                } else {
                    arrayOf(buffer.toString(), answer)
                }
            }

            fun isNotifying(characteristic: BluetoothGattCharacteristic): Boolean {
                return notifyingCharacteristics.contains(characteristic)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                val answer = String(characteristic.value).lowercase()
                if (lastCharacteristic == answer && answer.isNotEmpty()) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "closing")
                    aapsLogger.info(LTag.PUMPBTCOMM, answer)

                    close(true)
                    return
                }

                lastReceivedCharacteristic = System.currentTimeMillis()
                removeNotificationCommand()
                // aapsLogger.info(LTag.PUMPBTCOMM, answer)
                // aapsLogger.info(LTag.PUMPBTCOMM, lastCharacteristic)
                if (lastCharacteristic == answer) {
                    setNotificationBlocking(
                        UUID.fromString(GattAttributes.SERVICE_UUID),  //
                        UUID.fromString(GattAttributes.GATT_UUID), true
                    )
                    if (currentCommand != null) {
                        currentCommand!!.clearExecutedCommand()
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, "next")

                    nextCommand()
                    return
                }
                if (currentCommand != null) {
                    aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString())
                }
                //                String[] processed = processCharacteristics(new StringBuffer(previousLine), answer);
//                previousLine = processed[1];
//                answer = processed[0];
//                aapsLogger.info(LTag.PUMPBTCOMM, answer)
                if (answer.trim { it <= ' ' }.isNotEmpty()) {
                    if (answer.contains("time to powerdown")) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "time to powerdown")
                        if (!answer.contains("5")) {
                            isConnected = true
                        }
                        commandQueueBusy = false
                    }
                    if (currentCommand != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "command not null")
                        aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand!!.nextBleCommand())
                        val next = currentCommand!!.nextBleCommand()
                        if (next.isPresent) {

                            aapsLogger.info(LTag.PUMPBTCOMM, "blecommand")
                            next.get().characteristicChanged(answer, that, lastCharacteristic)
                        } else {
                            // if (bleCommand != null)
                            logCa(answer, that)
                            // else nextCommand()
                        }
                        if (answer.contains("time to powerdown") && currentCommand?.let { !it.hasFinished() } == true) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "clear executed")

//                            currentCommand.clearExecutedCommand();
                        }
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, "command null")
                        bleCommand!!.characteristicChanged(answer, that, lastCharacteristic)
                    }
                    latestReceivedAnswer = System.currentTimeMillis()
                    if (answer.contains("command con") && currentCommand != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "confirmed")
//                        characteristicChanged!!.characteristicChanged(answer, that, lastCharacteristic)

//                        if (!answer.startsWith(currentCommand.nextCommand().code.toLowerCase())) {
//                            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
//                            close(true);
//                        } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, "answer not empty")
                        isCommandConfirmed = true
                        lastConfirmedCommand = System.currentTimeMillis()
                        aapsLogger.info(LTag.PUMPBTCOMM, "command executed")
                        currentCommand!!.commandExecuted()

//                        }
                    } else if ((lastCharacteristic + answer).contains("pump status: suspend") || (lastCharacteristic + answer).contains("pump suspend state")) {
                        lastPumpStatus = PumpRunningState.Suspended
                    } else if ((lastCharacteristic + answer).contains("pump status: normal") || (lastCharacteristic + answer).contains("pump normal state")) {
                        lastPumpStatus = PumpRunningState.Running
                    }
                    //                if (answer.contains("bolus"))
//                    aapsLogger.info(LTag.PUMPBTCOMM, answer);
//                    aapsLogger.info(LTag.PUMPBTCOMM, "" + answer.contains("\n"));
                    lastCharacteristic = answer
                    radioResponseCountNotified.run()
                }
            }

            private fun logCa(answer: String, that: MedLinkBLE) {
                aapsLogger.info(LTag.PUMPBTCOMM, "logca")
                bleCommand?.characteristicChanged(answer, that, lastCharacteristic)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                aapsLogger.info(LTag.PUMPBTCOMM, "onCharRead ")
                val statusMessage = getGattStatusMessage(status)
                if (gattDebugEnabled) {
                    aapsLogger.debug(
                        LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicRead ("
                            + GattAttributes.lookup(characteristic.uuid) + ") " + statusMessage + ":"
                            + ByteUtil.getHex(characteristic.value)
                    )
                }
                if (mCurrentOperation != null) {
                    mCurrentOperation!!.gattOperationCompletionCallback(characteristic.uuid, characteristic.value)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                aapsLogger.info(LTag.PUMPBTCOMM, "oncharwrite")
                val uuidString = GattAttributes.lookup(characteristic.uuid)
                if (gattDebugEnabled) {
                    aapsLogger.debug(
                        LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicWrite " + getGattStatusMessage(status) + " "
                            + uuidString + " " + ByteUtil.shortHexString(characteristic.value)
                    )
                }
                if (mCurrentOperation != null) {
                    mCurrentOperation!!.gattOperationCompletionCallback(
                        characteristic.uuid,
                        characteristic.value
                    )
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                aapsLogger.error(LTag.PUMPBTCOMM, "Statechange $newState")
                // https://github.com/NordicSemiconductor/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java#L117
                if (status == 133) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Got the status 133 bug, closing gatt")
                    commandQueueBusy = false
                    SystemClock.sleep(180000)
                    bug133+=1
                    if(bug133>3){
                        medLinkUtil!!.sendBroadcastMessage(MedLinkConst.Intents.MedLinkConnectionError, context)
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, "Got the status 133 bug, closing gatt")
                    close(true)
                    SystemClock.sleep(500)
                    return
                }
                if (gattDebugEnabled) {
                    val stateMessage: String
                    stateMessage = if (newState == BluetoothProfile.STATE_CONNECTED) {
                        bug133=0
                        "CONNECTED"
                    } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                        "CONNECTING"
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        changeConnectionStatus(ConnectionStatus.CLOSED)
                        "DISCONNECTED"
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
//                        connectionStatus = ConnectionStatus.DISCONNECTING;
                        "DISCONNECTING"
                    } else {
                        "UNKNOWN newState ($newState)"
                    }
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage)
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        medLinkUtil!!.sendBroadcastMessage(MedLinkConst.Intents.BluetoothConnected, context)
                        val bondstate = medLinkDevice!!.bondState // Take action depending on the bond state
                        if (bondstate == BluetoothDevice.BOND_NONE || bondstate == BluetoothDevice.BOND_BONDED) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "Discoverying Services")
                            aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name)
                            if (connectionStatus == ConnectionStatus.CONNECTING && bluetoothConnectionGatt != null) {
                                synchronized(bluetoothConnectionGatt!!) {
                                    changeConnectionStatus(ConnectionStatus.DISCOVERING)
                                    isDiscovering = true
                                    bluetoothConnectionGatt!!.discoverServices()
                                }
                            }
                        }
                    } else if (newState == BluetoothProfile.STATE_CONNECTING ||  //
                        newState == BluetoothProfile.STATE_DISCONNECTING
                    ) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "We are in {} state.", if (status == BluetoothProfile.STATE_CONNECTING) "Connecting" else "Disconnecting")
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (latestReceivedAnswer > 0 && !hasCommandsToExecute()) {
                            medLinkUtil!!.sendBroadcastMessage(MedLinkConst.Intents.CommandCompleted, context)
                            latestReceivedAnswer = 0L
                            //TODO fix handling this events
                        } else if (currentCommand != null && currentCommand!!.nrRetries > MAX_TRIES) {
                            medLinkUtil!!.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnected, context)
                        }
                        commandQueueBusy = false
                        aapsLogger.info(LTag.PUMPBTCOMM, "Disconnecting")
                        close(true)
                        aapsLogger.warn(LTag.PUMPBTCOMM, "MedLink Disconnected.")
                    } else {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Some other state: (status={},newState={})", status, newState)
                    }
                } else {
                    commandQueueBusy = false
                    aapsLogger.info(LTag.PUMPBTCOMM, "Connection status changed")
                    close(true)
                    aapsLogger.warn(LTag.PUMPBTCOMM, "BT State connected, GATT status {} ({})", status, getGattStatusMessage(status))
                }
            }

            private fun failureThatShouldTriggerBonding(gattStatus: Int): Boolean {
                if (gattStatus == BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED || gattStatus == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION || gattStatus == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                    // Characteristic/descriptor is encrypted and needs bonding, bonding should be in progress already
                    // Operation must be retried after bonding is completed.
                    // This only seems to happen on Android 5/6/7.
                    // On newer versions Android will do retry internally
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "operation will be retried after bonding, bonding should be in progress")
                        return true
                    }
                }
                return false
            }

            fun nonnullOf(source: ByteArray?): ByteArray {
                return source ?: ByteArray(0)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                super.onDescriptorWrite(gatt, descriptor, status)
                aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.uuid)
                val parentCharacteristic = descriptor.characteristic
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    aapsLogger.error(
                        LTag.PUMPBTCOMM, String.format(
                            "failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ",
                            currentCommand!!.nextCommand().code, parentCharacteristic.uuid, "Medlink", status
                        )
                    )
                    if (failureThatShouldTriggerBonding(status)) return
                }

                // Check if this was the Client Characteristic Configuration Descriptor
                if (descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val value = nonnullOf(descriptor.value)
                        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        ) {
                            notifyingCharacteristics.add(parentCharacteristic)
                        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                            notifyingCharacteristics.remove(parentCharacteristic)
                        }
                    }

                    aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.uuid)
                    //                    SystemClock.sleep(6000);
                    aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.uuid)
                    changeConnectionStatus(ConnectionStatus.DISCOVERING)
                    currentCommand?.commandExecuted()
                    completedCommand()
                }
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                super.onDescriptorRead(gatt, descriptor, status)
                aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorRead ")
                mCurrentOperation!!.gattOperationCompletionCallback(descriptor.uuid, descriptor.value)
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorRead " + getGattStatusMessage(status) + " status " + descriptor)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                aapsLogger.info(LTag.PUMPBTCOMM, "onMtuchanged ")
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onMtuChanged $mtu status $status")
                }
                completedCommand()
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                super.onReadRemoteRssi(gatt, rssi, status)
                aapsLogger.info(LTag.PUMPBTCOMM, "onReadRemoteRssi ")
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi)
                }
                completedCommand()
            }

            override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
                super.onReliableWriteCompleted(gatt, status)
                aapsLogger.info(LTag.PUMPBTCOMM, "onReliableWriteCompleted ")
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReliableWriteCompleted status $status")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered ")
                if (connectionStatus != ConnectionStatus.DISCOVERING) {
                    return
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val medLinkFound = gatt.device.address == sp.getString(MedLinkConst.Prefs.MedLinkAddress, "")
                    if (gattDebugEnabled) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status))
                    }
                    aapsLogger.info(
                        LTag.PUMPBTCOMM, "Gatt device is MedLink device: " +
                            medLinkFound + " " + gatt.device.name + " " +
                            gatt.device.address
                    )
                    if (medLinkFound) {
                        mIsConnected = true
                        val message = Intent()
                        message.action = MedLinkConst.Intents.MedLinkReady
                        message.putExtra("BatteryLevel", batteryLevel)
                        message.putExtra("FirmwareVersion", firmwareVersion)
                        medLinkUtil!!.sendBroadcastMessage(message, context)
                        servicesDiscovered = true
                        commandQueueBusy = false
                        enableNotifications()
                        //                        nextCommand();
                    } else {
                        mIsConnected = false
                        if (System.currentTimeMillis() - latestReceivedAnswer > 600000) {
                            medLinkServiceData!!.setServiceState(
                                MedLinkServiceState.MedLinkError,
                                MedLinkError.DeviceIsNotMedLink
                            )
                        } else {
                            aapsLogger.info(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status))
                            disconnect()
                        }
                    }
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status))
                    disconnect()
                    medLinkUtil!!.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed, context)
                }
            }
        }
    }

    fun registerRadioResponseCountNotification(notifier: Runnable) {
        radioResponseCountNotified = notifier
    }

    fun reprocessOnHold() {
        currentCommand = onHoldCommandQueue.first
        nextCommand()
    }

    fun postponeCurrentCommand() {
        synchronized(executionCommandQueue) {
            val commands = executionCommandQueue.filter { it.commandList.any { com -> com.command.listCommand } }
            val lowCommands = executionCommandQueue.filter { it.commandList.none { com -> com.command.listCommand } }

            if (currentCommand != null && commands.isNotEmpty()) {
                val command = currentCommand
                removeFirstCommand(true)
                val newQueue = ConcurrentLinkedDeque<CommandExecutor<*>>()
                newQueue.addAll(commands)
                newQueue.add(command)
                newQueue.addAll(lowCommands)
                executionCommandQueue.clear()
                executionCommandQueue.addAll(newQueue)
            }
        }
    }

}