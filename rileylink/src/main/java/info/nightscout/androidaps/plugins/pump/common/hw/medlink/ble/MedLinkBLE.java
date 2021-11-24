
package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION;
import static android.bluetooth.BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static java.nio.charset.StandardCharsets.UTF_8;
import static info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst.Intents.MedLinkReady;
import static info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult.RESULT_SUCCESS;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleConnectCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleStartCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleStopCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.BLECommOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by Dirceu on 30/09/20.
 */
@Singleton
public class MedLinkBLE extends RileyLinkBLE {

    private String firmwareVersion = "";
    private int batteryLevel = 0;
    private boolean commandConfirmed = false;
    private final ResourceHelper resourceHelper;
    private final Handler bleHandler;
    private boolean needRetry;
    private long lastCloseAction = 0L;
    private long lastExecutedCommand;
    private boolean isDiscovering;
    private long sleepSize;
    private long lastConnection;

    private ConnectionStatus connectionStatus = ConnectionStatus.CLOSED;
    private boolean notificationEnabled;
    private long lastConfirmedCommand = 0L;
    private long lastReceivedCharacteristic;
    private final HandlerThread handlerThread = new HandlerThread("BleThread");
    private final HandlerThread characteristicThread = new HandlerThread("CharacteristicThread");
    private final Handler handler;
    private long lastGattConnection;
    private long connectionStatusChange;
    private PumpStatusType lastPumpStatus;

    public boolean partialCommand() {
        return lastConfirmedCommand > lastConnection &&
                lastReceivedCharacteristic > lastConfirmedCommand;
    }

    public void removeFirstCommand(Boolean force) {
        if (currentCommand != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, "null");
        }
        if ((currentCommand != null &&
                currentCommand.hasFinished()) || force) {
            aapsLogger.info(LTag.PUMPBTCOMM, "" + lowPriorityExecutionCommandQueue.remove(currentCommand));
            aapsLogger.info(LTag.PUMPBTCOMM, "" + executionCommandQueue.remove(currentCommand));
            aapsLogger.info(LTag.PUMPBTCOMM, "" + priorityExecutionCommandQueue.remove(currentCommand));
            currentCommand = null;
        }
    }

    public void reExecuteCommand(CommandExecutor currentCommand) {
        if (!hasCommandsToExecute()) {
            addWriteCharacteristic(UUID.fromString(GattAttributes.SERVICE_UUID),
                    UUID.fromString(GattAttributes.GATT_UUID),
                    currentCommand.getMedLinkPumpMessage(), CommandPriority.HIGH);
        } else {
            currentCommand.clearExecutedCommand();
        }
        nextCommand();
    }

    private boolean hasCommandsToExecute() {
        return !executionCommandQueue.isEmpty() ||
                !priorityExecutionCommandQueue.isEmpty() ||
                !lowPriorityExecutionCommandQueue.isEmpty();
    }

    public void post(Runnable r) {
        handler.post(r);
    }

    public void setFirmwareVersion(String s) {
        this.firmwareVersion = s.replace("_", "-");
    }

    public void setBatteryLevel(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public void clearExecutedCommand() {
        commandQueueBusy = false;
        if (currentCommand != null) {
            currentCommand.clearExecutedCommand();
        }
    }

    public void setConfirmedCommand(boolean commandConfirmed) {
        this.commandConfirmed = commandConfirmed;
    }

    private enum CommandPriority {
        LOWER,
        NORMAL,
        HIGH
    }

    private enum ConnectionStatus {
        CLOSED,
        CLOSING,
        CONNECTED,
        CONNECTING,
        //        DISCONNECTED,
        DISCONNECTING,
        DISCOVERING,
        EXECUTING;


        public boolean isConnecting() {
            return this == CONNECTING || this == DISCOVERING || this == DISCONNECTING ||
                    this == CLOSING;
        }
    }

    public boolean isCommandConfirmed() {
        return commandConfirmed;
    }

    public void applyClose() {
        aapsLogger.info(LTag.PUMPBTCOMM, "applying close");
        if (commandsToAdd.isEmpty() && !hasCommandsToExecute() && connectionStatus != ConnectionStatus.CLOSED
                && connectionStatus != ConnectionStatus.CLOSING &&
                connectionStatus != ConnectionStatus.CONNECTING) {
            if (System.currentTimeMillis() - lastGattConnection < 5000) {
                SystemClock.sleep(lastGattConnection - System.currentTimeMillis());
            }
            disconnect();
        }
    }
//
//    public void clearCommands() {
//        executionCommandQueue.clear();
//    }

//    public void clearCommands() {
//        executionCommandQueueN.clear();
//        executionCommandQueue.clear();
//        close(true);
//    }

    private static class CommandsToAdd {
        private final UUID serviceUUID;
        private final UUID charaUUID;
        private final MedLinkPumpMessage<?> command;

        public CommandsToAdd(UUID serviceUUID, UUID charaUUID, MedLinkPumpMessage<?> commandMsg) {
            this.serviceUUID = serviceUUID;
            this.charaUUID = charaUUID;
            this.command = commandMsg;
        }

        @Override public String toString() {
            return "CommandsToAdd{" +
                    "command=" + command.toString() +
                    '}';
        }
    }

    private final Set<BluetoothGattCharacteristic> notifyingCharacteristics = new HashSet<>();
    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private Boolean commandQueueBusy;
    private final int MAX_TRIES = 5;
    private boolean isRetrying;

    private final ConcurrentLinkedDeque<CommandsToAdd> commandsToAdd = new ConcurrentLinkedDeque<>();


    private enum BLEOperationType {
        READ_CHARACTERISTIC_BLOCKING,
        WRITE_CHARACTERISTIC_BLOCKING,
        SEND_NOTIFICATION_BLOCKING,
    }

    private BleCommand characteristicChanged;


    private class Resp {
        private final String command;

        public Function<Object, MedLinkStandardReturn> getFunc() {
            return func;
        }

        private final Function func;

        private Resp(Function func, String command) {
            this.func = func;
            this.command = command;
        }

        @Override public String toString() {
            return "Resp{" +
                    "command='" + command + '\'' +
                    ", func=" + func +
                    '}';
        }
    }

    @Inject
    MedLinkUtil medLinkUtil;
    @Inject
    MedLinkServiceData medLinkServiceData;

    private String previousLine = "";
    //    private long latestReceivedCommand = 0l;
    private long latestReceivedAnswer = 0L;
    private boolean isConnected = false;
    private String pumpModel = null;
    private Set<String> addedCommands = new HashSet<>();
    private ConcurrentLinkedDeque<CommandExecutor> priorityExecutionCommandQueue = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<CommandExecutor> executionCommandQueue = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<CommandExecutor> lowPriorityExecutionCommandQueue = new ConcurrentLinkedDeque<>();
    //    private ConcurrentLinkedDeque<String> executionCommandQueueN = new ConcurrentLinkedDeque<>();
    private CommandExecutor currentCommand;
    private BLECommOperation mCurrentOperation;
    List<Resp> resultActivity = new ArrayList<>();
    private boolean servicesDiscovered = false;

    private String lastCharacteristic = "";

    @Inject
    public MedLinkBLE(final Context context, ResourceHelper resourceHelper) {
        super(context);
        MedLinkBLE that = this;
        handlerThread.start();
        characteristicThread.start();
        bleHandler = new Handler(handlerThread.getLooper());
        handler = new Handler(characteristicThread.getLooper());
        this.resourceHelper = resourceHelper;
        bluetoothGattCallback = new BluetoothGattCallback() {

            private String[] processCharacteristics(StringBuffer buffer, String answer) {
                if (answer.contains("\n")) {
                    int index = answer.indexOf("\n");
                    buffer.append(answer.substring(0, index + 1));
                    return processCharacteristics(buffer, answer.substring(index + 1));
                } else {
                    return new String[]{buffer.toString(), answer};
                }
            }

            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt,
                                                final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                String answer = new String(characteristic.getValue()).toLowerCase();
                lastReceivedCharacteristic = System.currentTimeMillis();
                removeNotificationCommand();

                aapsLogger.info(LTag.PUMPBTCOMM, answer);
                if (currentCommand != null) {
                    aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());
                }
//                String[] processed = processCharacteristics(new StringBuffer(previousLine), answer);
//                previousLine = processed[1];
//                answer = processed[0];
                aapsLogger.info(LTag.PUMPBTCOMM, answer);
                if (!answer.trim().isEmpty()) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "answer not empty");

                    if (answer.contains("time to powerdown")) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "time to powerdown");

                        commandQueueBusy = false;
                    }
                    if (currentCommand != null && currentCommand.getMedLinkPumpMessage() != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "command not null");

                        currentCommand.getMedLinkPumpMessage().characteristicChanged(answer, that, lastCharacteristic);
                        if (answer.contains("time to powerdown") && !currentCommand.hasFinished()) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "clear executed");

                            currentCommand.clearExecutedCommand();
                        }
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, "command null");

                        characteristicChanged.characteristicChanged(answer, that, lastCharacteristic);
                    }

                    latestReceivedAnswer = System.currentTimeMillis();
                    if (answer.contains("command confir") && currentCommand != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "confirmed");

                        if (!answer.startsWith(currentCommand.nextCommand().code.toLowerCase())) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
                            close();
                        } else {
                            aapsLogger.info(LTag.PUMPBTCOMM, "answer not empty");

                            commandConfirmed = true;
                            lastConfirmedCommand = System.currentTimeMillis();
                            aapsLogger.info(LTag.PUMPBTCOMM, "command executed");
                            currentCommand.commandExecuted();
                        }
                    } else if (answer.contains("pump status: suspend") || answer.contains("pump suspend state")) {
                        lastPumpStatus = PumpStatusType.Suspended;
                    } else if (answer.contains("pump status: normal") || answer.contains("pump normal state")) {
                        lastPumpStatus = PumpStatusType.Running;
                    }
                    //                if (answer.contains("bolus"))
//                    aapsLogger.info(LTag.PUMPBTCOMM, answer);
//                    aapsLogger.info(LTag.PUMPBTCOMM, "" + answer.contains("\n"));
                    lastCharacteristic = answer;
                    if (radioResponseCountNotified != null) {
                        radioResponseCountNotified.run();
                    }
                }
            }


            @Override
            public void onCharacteristicRead(final BluetoothGatt gatt,
                                             final BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                aapsLogger.info(LTag.PUMPBTCOMM, "onCharRead ");
                final String statusMessage = getGattStatusMessage(status);
                if (gattDebugEnabled) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicRead ("
                            + GattAttributes.lookup(characteristic.getUuid()) + ") " + statusMessage + ":"
                            + ByteUtil.getHex(characteristic.getValue()));
                }
                if (mCurrentOperation != null) {
                    mCurrentOperation.gattOperationCompletionCallback(characteristic.getUuid(), characteristic.getValue());
                }
            }


            @Override
            public void onCharacteristicWrite(final BluetoothGatt gatt,
                                              final BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                aapsLogger.info(LTag.PUMPBTCOMM, "oncharwrite");
                final String uuidString = GattAttributes.lookup(characteristic.getUuid());
                if (gattDebugEnabled) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicWrite " + getGattStatusMessage(status) + " "
                            + uuidString + " " + ByteUtil.shortHexString(characteristic.getValue()));
                }
                if (mCurrentOperation != null) {
                    mCurrentOperation.gattOperationCompletionCallback(
                            characteristic.getUuid(),
                            characteristic.getValue());
                }
            }


            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                aapsLogger.error(LTag.PUMPBTCOMM, "Statechange " + newState);
                // https://github.com/NordicSemiconductor/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java#L117
                if (status == 133) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Got the status 133 bug, closing gatt");
                    commandQueueBusy = false;
                    SystemClock.sleep(1000);
                    close(true);
                    SystemClock.sleep(500);
                    return;
                }

                if (gattDebugEnabled) {
                    final String stateMessage;
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        stateMessage = "CONNECTED";
                    } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                        stateMessage = "CONNECTING";
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        changeConnectionStatus(ConnectionStatus.CLOSED);

                        stateMessage = "DISCONNECTED";
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
//                        connectionStatus = ConnectionStatus.DISCONNECTING;
                        stateMessage = "DISCONNECTING";
                    } else {
                        stateMessage = "UNKNOWN newState (" + newState + ")";
                    }
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage);
                }

                if (status == GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.BluetoothConnected, context);
                        int bondstate = rileyLinkDevice.getBondState();        // Take action depending on the bond state
                        if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "Discoverying Services");
                            aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name());
                            if (connectionStatus == ConnectionStatus.CONNECTING && bluetoothConnectionGatt != null) {
                                synchronized (bluetoothConnectionGatt) {
                                    changeConnectionStatus(ConnectionStatus.DISCOVERING);
                                    isDiscovering = true;
                                    bluetoothConnectionGatt.discoverServices();
                                }
                            }
                        }


                    } else if ((newState == BluetoothProfile.STATE_CONNECTING) || //
                            (newState == BluetoothProfile.STATE_DISCONNECTING)) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "We are in {} state.", status == BluetoothProfile.STATE_CONNECTING ? "Connecting" :
                                "Disconnecting");
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (latestReceivedAnswer > 0 && !hasCommandsToExecute()) {
                            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.CommandCompleted, context);
                            latestReceivedAnswer = 0l;
                            //TODO fix handling this events
                        } else if (currentCommand != null && currentCommand.getNrRetries() > MAX_TRIES) {
                            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnected, context);
                        }
                        commandQueueBusy = false;
                        close(true);
                        aapsLogger.warn(LTag.PUMPBTCOMM, "MedLink Disconnected.");
                    } else {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Some other state: (status={},newState={})", status, newState);
                    }
                } else {
                    commandQueueBusy = false;
                    close(true);
                    aapsLogger.warn(LTag.PUMPBTCOMM, "BT State connected, GATT status {} ({})", status, getGattStatusMessage(status));
                }
            }

            private boolean failureThatShouldTriggerBonding(final int gattStatus) {
                if (gattStatus == GATT_REQUEST_NOT_SUPPORTED
                        || gattStatus == GATT_INSUFFICIENT_AUTHENTICATION
                        || gattStatus == GATT_INSUFFICIENT_ENCRYPTION) {
                    // Characteristic/descriptor is encrypted and needs bonding, bonding should be in progress already
                    // Operation must be retried after bonding is completed.
                    // This only seems to happen on Android 5/6/7.
                    // On newer versions Android will do retry internally
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "operation will be retried after bonding, bonding should be in progress");
                        return true;
                    }
                }
                return false;
            }

            byte[] nonnullOf(final byte[] source) {
                return (source == null) ? new byte[0] : source;
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.getUuid());
                final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
                if (status != GATT_SUCCESS) {
                    aapsLogger.error(LTag.PUMPBTCOMM, String.format(
                            "failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ",
                            currentCommand.nextCommand().code, parentCharacteristic.getUuid(), "Medlink", status));
                    if (failureThatShouldTriggerBonding(status)) return;
                }

                // Check if this was the Client Characteristic Configuration Descriptor
                if (descriptor.getUuid().equals(CCC_DESCRIPTOR_UUID)) {
                    if (status == GATT_SUCCESS) {
                        final byte[] value = nonnullOf(descriptor.getValue());
                        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                                Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                            notifyingCharacteristics.add(parentCharacteristic);
                        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                            notifyingCharacteristics.remove(parentCharacteristic);
                        }
                    }

                    long lastCommand = lastExecutedCommand;
                    long lastConf = lastConfirmedCommand;
                    long currentTime = System.currentTimeMillis();
//                    bleHandler.post(
//                    new Thread() {
//                        @Override public void run() {
//                            aapsLogger.info(LTag.PUMPBTCOMM, "retrying");
//                            long currentSleep = System.currentTimeMillis() - currentTime;
//                            aapsLogger.info(LTag.PUMPBTCOMM, "currentSleep "+currentSleep);
//                            if (currentSleep < 6000L) {
//                                SystemClock.sleep(6000L - currentSleep);
//                            }
//                            if (nrTries <= MAX_TRIES && lastExecutedCommand == lastCommand && lastConf == lastConfirmedCommand) {
//                                nrTries++;
//                                nextCommand();
//                            } else if (nrTries >= MAX_TRIES) {
//                                close();
//                            }
//                        }
//                    }.start();
//                });
//                    trd.start();
                    aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.getUuid());
//                    SystemClock.sleep(6000);

                    aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.getUuid());
                    changeConnectionStatus(ConnectionStatus.DISCOVERING);
                    currentCommand.commandExecuted();
                    completedCommand();
                }
            }


            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                         int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorRead ");
                mCurrentOperation.gattOperationCompletionCallback(descriptor.getUuid(), descriptor.getValue());
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorRead " + getGattStatusMessage(status) + " status " + descriptor);
                }
            }


            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                aapsLogger.info(LTag.PUMPBTCOMM, "onMtuchanged ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onMtuChanged " + mtu + " status " + status);
                }
                completedCommand();
            }


            @Override
            public void onReadRemoteRssi(final BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                aapsLogger.info(LTag.PUMPBTCOMM, "onReadRemoteRssi ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi);
                }
                completedCommand();
            }


            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                aapsLogger.info(LTag.PUMPBTCOMM, "onReliableWriteCompleted ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReliableWriteCompleted status " + status);
                }
            }


            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered ");
                if (connectionStatus != ConnectionStatus.DISCOVERING) {
                    return;
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    boolean medLinkFound =
                            //MedLinkConst.DEVICE_NAME.contains(gatt.getDevice().getName()) &&
                            gatt.getDevice().getAddress().equals(sp.getString(MedLinkConst.Prefs.MedLinkAddress, ""));

                    if (gattDebugEnabled) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    }

                    aapsLogger.info(LTag.PUMPBTCOMM, "Gatt device is MedLink device: " +
                            medLinkFound + " " + gatt.getDevice().getName() + " " +
                            gatt.getDevice().getAddress());

                    if (medLinkFound) {
                        mIsConnected = true;
                        Intent message = new Intent();
                        message.setAction(MedLinkReady);
                        message.putExtra("BatteryLevel", batteryLevel);
                        message.putExtra("FirmwareVersion", firmwareVersion);
                        medLinkUtil.sendBroadcastMessage(message, context);
                        servicesDiscovered = true;
                        commandQueueBusy = false;
                        enableNotifications();
//                        nextCommand();
                    } else {
                        mIsConnected = false;
                        if (System.currentTimeMillis() - latestReceivedAnswer > 600000) {
                            medLinkServiceData.setServiceState(MedLinkServiceState.MedLinkError,
                                    MedLinkError.DeviceIsNotMedLink);
                        } else {
                            aapsLogger.info(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                            disconnect();
                        }
                    }

                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    disconnect();
                    medLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed, context);
                }
            }
        }

        ;
    }

    private void removeNotificationCommand() {
        if (!priorityExecutionCommandQueue.isEmpty() &&
                MedLinkCommandType.Notification == priorityExecutionCommandQueue.getFirst().getCurrentCommand()) {
            priorityExecutionCommandQueue.pollFirst();
        }
    }

    private void changeConnectionStatus(ConnectionStatus nesStatus) {
        connectionStatus = nesStatus;
        connectionStatusChange = System.currentTimeMillis();

    }

    //TODO 28/03 removed readcharacteristic from code test to see if it affect the execution
    public BLECommOperationResult readCharacteristic_blocking(UUID serviceUUID, UUID charaUUID) {
        aapsLogger.info(LTag.PUMPBTCOMM, "readCharacteristic_blocking");
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        } else {

            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    List<BluetoothGattService> services = bluetoothConnectionGatt.getServices();

                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID).getCharacteristic(
                            charaUUID);

                    // Check if characteristic is valid
                    if (chara == null) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "ERROR: Characteristic is 'null', ignoring read request");
                        return rval;
                    } else
                        // Check if this characteristic actually has READ property
                        if ((chara.getProperties() & PROPERTY_READ) == 0) {
                            aapsLogger.error(LTag.PUMPBTCOMM, "ERROR: Characteristic cannot be read");
                            return rval;
                        } else {
                            // Enqueue the read command now that all checks have been passed

                            boolean result = executionCommandQueue.add(new CommandExecutor(MedLinkCommandType.ReadCharacteristic, aapsLogger, medLinkServiceData) {
                                @Override
                                public void run() {
                                    commandConfirmed = false;
                                    try {
                                        synchronized (commandQueueBusy) {
                                            lastExecutedCommand = System.currentTimeMillis();
                                            if (!bluetoothConnectionGatt.readCharacteristic(chara)) {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: readCharacteristic failed for characteristic: %s", chara.getUuid()));
                                                completedCommand();
                                                if (chara.getValue() != null) {
                                                    aapsLogger.info(LTag.PUMPBTCOMM, new String(mCurrentOperation.getValue(), UTF_8));
                                                }
                                            } else {
                                                aapsLogger.info(LTag.PUMPBTCOMM,
                                                        String.format("reading characteristic <%s>",
                                                                chara.getUuid()));
                                                needRetry = true;
//                                        nrTries++;
                                            }
                                            commandQueueBusy = false;
                                        }
                                    } catch (NullPointerException e) {
                                        aapsLogger.info(LTag.PUMPBTCOMM, "npe need retry");
                                        needRetry = true;
                                    }
                                }
                            });
                            if (result) {
                                nextCommand();
                            } else {
                                aapsLogger.error(LTag.PUMPBTCOMM, "ERROR: Could not enqueue read characteristic command");
                            }
                            return rval;
                        }
                }
            }
            mCurrentOperation = null;
        }
        return rval;
    }

    public void addWriteCharacteristic(UUID serviceUUID, UUID charaUUID,
                                       MedLinkPumpMessage<?> command,
                                       CommandPriority commandPriority) {
//        this.latestReceivedCommand = System.currentTimeMillis();
        aapsLogger.info(LTag.PUMPBTCOMM, "commands");
        aapsLogger.info(LTag.PUMPBTCOMM, command.getCommandType().code);
//        Esta verificação não é mais necessário já que daqui pra frente a gente não usa mais estes comandos, talvez fazer ela em outro lugar, mas ao adicionar comandos não precisa
//        if (bluetoothConnectionGatt != null) {
//            rval.value = command.getCommandData();
//            aapsLogger.info(LTag.PUMPBTCOMM, bluetoothConnectionGatt.getDevice().toString());
        if (mCurrentOperation != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "busy for the command " + command.getCommandType().code);
//                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
        } else {

//                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
            // Catch if the service is not supported by the BLE device
            // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
            // app that created the bluetoothConnectionGatt has been destroyed/created,
            // e.g. when the user switches from portrait to landscape.
//                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
//                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
//                    close();
//                    return ;
            // TODO: 11/07/2016 UI update for user
            // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
//                } else {
            if (isBolus(command.getCommandType()) ||
                    command.getCommandType().isSameCommand(MedLinkCommandType.StopStartPump) ||
                    executionCommandQueue.stream().noneMatch(f -> f.matches(command))) {
                CommandExecutor commandExecutor;
//                        synchronized (bluetoothConnectionGatt) {
                synchronized (commandQueueBusy) {
//                                BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
//                                        .getCharacteristic(charaUUID);
//                                int mWriteType;
//                                if ((chara.getProperties() & PROPERTY_WRITE_NO_RESPONSE) != 0) {
//                                    mWriteType = WRITE_TYPE_NO_RESPONSE;
//                                } else {
//                                    mWriteType = WRITE_TYPE_DEFAULT;
//                                }
//                                int writeProperty;
//                                switch (mWriteType) {
//                                    case WRITE_TYPE_DEFAULT:
//                                        writeProperty = PROPERTY_WRITE;
//                                        break;
//                                    case WRITE_TYPE_NO_RESPONSE:
//                                        writeProperty = PROPERTY_WRITE_NO_RESPONSE;
//                                        break;
//                                    case WRITE_TYPE_SIGNED:
//                                        writeProperty = PROPERTY_SIGNED_WRITE;
//                                        break;
//                                    default:
//                                        writeProperty = 0;
//                                        break;
//                                }
//
//                                if ((chara.getProperties() & writeProperty) == 0) {
//                                    aapsLogger.error(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "ERROR: Characteristic <%s> does not support writeType '%s'", chara.getUuid(), mWriteType));
//                                    return rval;
//                                }
//                            RemainingBleCommand remCom = new RemainingBleCommand(command,
//                                    func, hasArg);

                    commandExecutor = buildCommandExecutor(charaUUID, serviceUUID, command);
                }
//                        }
//                    if (commandExecutor != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "adding command" + command.getCommandType().code);
                if (commandPriority == CommandPriority.HIGH || command.isPriority()) {
                    if (commandPriority == CommandPriority.HIGH && command.getCommandType() == MedLinkCommandType.Connect) {
                        this.priorityExecutionCommandQueue.addFirst(commandExecutor);
                    } else if (commandPriority == CommandPriority.HIGH) {
                        if (!this.priorityExecutionCommandQueue.contains(commandExecutor)) {
                            this.priorityExecutionCommandQueue.add(commandExecutor);
                        }
                    } else {
                        this.executionCommandQueue.addFirst(commandExecutor);
                    }
                } else if (commandPriority == CommandPriority.NORMAL) {
                    this.executionCommandQueue.add(commandExecutor);
                } else if (!this.lowPriorityExecutionCommandQueue.contains(commandExecutor)) {
                    this.lowPriorityExecutionCommandQueue.add(commandExecutor);
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
        return;
    }

    private CommandExecutor buildCommandExecutor(UUID charaUUID, UUID serviceUUID, MedLinkPumpMessage<?> message) {
//        if (prepend && MedLinkCommandType.BolusStatus.isSameCommand(message.getCommandType())) {
//            return new ContinuousCommandExecutor(message, aapsLogger) {
//                @Override public void run() {
//                    commandConfirmed = false;
//                    long lastReceived = lastReceivedCharacteristic;
//                    lastExecutedCommand = System.currentTimeMillis();
//                    if (bluetoothConnectionGatt != null) {
//                        BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
//                                .getCharacteristic(charaUUID);
//                        chara.setValue(this.nextCommand().getRaw());
////                                    chara.setWriteType(PROPERTY_WRITE); //TODO validate
//                        nrRetries++;
//                        aapsLogger.debug(LTag.PUMPBTCOMM, "running command");
//                        aapsLogger.debug(LTag.PUMPBTCOMM, message.getCommandType().code);
//                        int count = 0;
////                        while (lastReceived == lastReceivedCharacteristic && count < MAX_TRIES) {
//
//                        if (bluetoothConnectionGatt == null || !bluetoothConnectionGatt.writeCharacteristic(chara)) {
//                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeCharacteristic failed for characteristic: %s", chara.getUuid()));
//                            needRetry = true;
//                            commandQueueBusy = false;
////                                break;
//                        } else {
//                            needRetry = false;
//                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("writing <%s> to characteristic <%s>", message.getCommandType().code, chara.getUuid()));
//                        }
////                            count++;
////                            SystemClock.sleep(4000);
////                        }
//
//                    } else {
//                        aapsLogger.info(LTag.PUMPBTCOMM, "connection gatt is null need retry");
//                        needRetry = true;
//                    }
//                    aapsLogger.info(LTag.PUMPBTCOMM, "ZZZZZZZZZZZZZZZZZZZZZ");
//                    if (needRetry && connectionStatus != ConnectionStatus.DISCOVERING) {
//                        aapsLogger.info(LTag.PUMPBTCOMM, "needretry");
//                        if (currentCommand != null) {
//                            currentCommand.clearExecutedCommand();
//                        }
//                        needRetry = false;
//                        disconnect();
//                    }
//                }
//            };
//        } else {
        return new CommandExecutor(message, aapsLogger) {
            @Override public void run() {
                commandConfirmed = false;
                long lastReceived = lastReceivedCharacteristic;
                lastExecutedCommand = System.currentTimeMillis();
                if (bluetoothConnectionGatt != null && bluetoothConnectionGatt.getService(serviceUUID) != null) {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    chara.setValue(this.nextCommandData());
//                                    chara.setWriteType(PROPERTY_WRITE); //TODO validate
//                    nrRetries++;
                    aapsLogger.debug(LTag.PUMPBTCOMM, "running command");
                    aapsLogger.debug(LTag.PUMPBTCOMM, new String(this.nextCommandData()));
                    int count = 0;
//                        while (lastReceived == lastReceivedCharacteristic && count < MAX_TRIES) {

                    if (bluetoothConnectionGatt == null || !bluetoothConnectionGatt.writeCharacteristic(chara)) {
                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeCharacteristic failed for characteristic: %s", chara.getUuid()));
                        needRetry = true;
                        commandQueueBusy = false;
//                                break;
                    } else {
                        needRetry = false;
                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("writing <%s> to characteristic <%s>", new String(chara.getValue(), UTF_8), chara.getUuid()));
                    }
//                            count++;
//                            SystemClock.sleep(4000);
//                        }

                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "connection gatt is null need retry");
                    needRetry = true;
                }
                aapsLogger.info(LTag.PUMPBTCOMM, "ZZZZZZZZZZZZZZZZZZZZZ");
                if (needRetry && connectionStatus != ConnectionStatus.DISCOVERING) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "needretry");
                    if (currentCommand != null) {
                        currentCommand.clearExecutedCommand();
                    }
                    needRetry = false;
                    disconnect();
                }
            }
        };
//        }
    }

    protected byte[] getPumpResponse() {
        byte[] result = StringUtils.join(this.pumpResponse, ",").getBytes();
        this.pumpResponse = new StringBuffer();
        return result;
    }

    public synchronized void addWriteCharacteristic(UUID serviceUUID, UUID charaUUID, MedLinkPumpMessage msg) {

        aapsLogger.info(LTag.PUMPBTCOMM, "writeCharblocking");
        aapsLogger.info(LTag.PUMPBTCOMM, msg.getCommandType().code);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt);
        aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name());
        aapsLogger.info(LTag.PUMPBTCOMM, "" + msg.getBtSleepTime());
        if (connectionStatus == ConnectionStatus.DISCOVERING && System.currentTimeMillis() - connectionStatusChange > 60000) {
            disconnect();
        }
        if (msg.getBtSleepTime() > 0) {
            this.sleepSize = msg.getBtSleepTime();
        }

        addCommands(serviceUUID, charaUUID, msg, isBolus(msg.getCommandType()));

        needToBeStarted(serviceUUID, charaUUID, msg.getCommandType());
        aapsLogger.info(LTag.PUMPBTCOMM, "before connect");
        if (!connectionStatus.isConnecting()) {
            medLinkConnect();
        }
    }

    public void needToBeStarted(UUID serviceUUID, UUID charaUUID, MedLinkCommandType command) {
        if (isBolus(command)) {
            if (currentCommand != null && currentCommand.getMedLinkPumpMessage().getArgument() == MedLinkCommandType.StopPump) {
                if (!currentCommand.hasFinished()) {
                    currentCommand.clearExecutedCommand();
                    currentCommand = null;
                }
            }
            if (!containsStart()) {
                MedLinkPumpMessage startMsg = new MedLinkPumpMessage<String>(MedLinkCommandType.StopStartPump,
                        MedLinkCommandType.StartPump,
                        new BleStartCommand(aapsLogger, medLinkServiceData));
                addWriteCharacteristic(serviceUUID, charaUUID, startMsg, CommandPriority.HIGH);
            }
        }
        if (lastPumpStatus == PumpStatusType.Suspended && lowPriorityExecutionCommandQueue.isEmpty()) {
            MedLinkPumpMessage stopMsg = new MedLinkPumpMessage<String>(MedLinkCommandType.StopStartPump,
                    MedLinkCommandType.StopPump,
                    new BleStopCommand(aapsLogger, medLinkServiceData));
            addWriteCharacteristic(serviceUUID, charaUUID, stopMsg, CommandPriority.LOWER);
        }
    }


    private boolean containsStart() {
        MedLinkPumpMessage stopMsg = new MedLinkPumpMessage<String>(MedLinkCommandType.StopStartPump,
                MedLinkCommandType.StopPump,
                new BleStopCommand(aapsLogger, medLinkServiceData));
        CommandExecutor exec = new CommandExecutor(stopMsg, aapsLogger) {
            @Override public void run() {

            }
        };
        return priorityExecutionCommandQueue.contains(exec);
    }


    public boolean isBolus(MedLinkCommandType commandType) {
        return MedLinkCommandType.Bolus.isSameCommand(commandType) ||
                MedLinkCommandType.SMBBolus.isSameCommand(commandType) ||
                MedLinkCommandType.TBRBolus.isSameCommand(commandType);

    }

    private void addCommands(UUID serviceUUID, UUID charaUUID, MedLinkPumpMessage msg,
                             boolean first) {

        synchronized (commandsToAdd) {
            CommandsToAdd command = new CommandsToAdd(serviceUUID, charaUUID, msg);
            addCommand(command, first);
//            if (msg.getArgument() != MedLinkCommandType.NoCommand) {
//                if (msg.getArgument() == MedLinkCommandType.BaseProfile || msg.getArgument() == MedLinkCommandType.IsigHistory) {
//                    addCommand(new CommandsToAdd(serviceUUID, charaUUID,
//                            msg.getArgumentData(),
//                            msg.getArgCallback(), msg.getArgument() != null), first);
//                } else {
//                    addCommand(new CommandsToAdd(serviceUUID, charaUUID, msg.getArgumentData(),
//                            msg.getBaseCallback(), msg.getArgument() != null), first);
//                }
//            }
        }
    }

    private void addCommand(CommandsToAdd command, boolean first) {
        aapsLogger.info(LTag.PUMPBTCOMM, "adding Command " + command.command.getCommandType().code);
        aapsLogger.info(LTag.PUMPBTCOMM, "adding Command " + first);
        if (first) {
            this.commandsToAdd.addFirst(command);
        } else {
            this.commandsToAdd.add(command);
        }
    }

    @Override public void connectGatt() {
        lastGattConnection = System.currentTimeMillis();
        changeConnectionStatus(ConnectionStatus.CONNECTING);
        aapsLogger.info(LTag.PUMPBTCOMM, "Connecting gatt");
        if (this.rileyLinkDevice == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device is null, can't do connectGatt.");
            return;
        }

        bluetoothConnectionGatt = rileyLinkDevice.connectGatt(context, false,
                bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to connect to Bluetooth Low Energy device at " + bluetoothAdapter.getAddress());
        } else {
            gattConnected = true;
            if (gattDebugEnabled) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Gatt Connected.");
            }

            String deviceName = bluetoothConnectionGatt.getDevice().getName();
            if (StringUtils.isNotEmpty(deviceName)) {
                // Update stored name upon connecting (also for backwards compatibility for device where a name was not yet stored)
                sp.putString(RileyLinkConst.Prefs.RileyLinkName, deviceName);
            } else {
                sp.remove(RileyLinkConst.Prefs.RileyLinkName);
            }
            medLinkServiceData.rileylinkName = deviceName;
            medLinkServiceData.rileylinkAddress = bluetoothConnectionGatt.getDevice().getAddress();
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


    public void addExecuteConnectCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "ad ok conn command ");
        addWriteCharacteristic(UUID.fromString(GattAttributes.SERVICE_UUID),
                UUID.fromString(GattAttributes.GATT_UUID),
                new MedLinkPumpMessage<String>(MedLinkCommandType.Connect,
                        new BleConnectCommand(aapsLogger, medLinkServiceData)),
                CommandPriority.HIGH);
    }

    private int connectTries = 0;

    private void medLinkConnect() {
        if (connectTries > MAX_TRIES) {
            aapsLogger.info(LTag.PUMPBTCOMM, "max connection tries");
            return;
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "connecting medlink");
        aapsLogger.info(LTag.PUMPBTCOMM, "" + (System.currentTimeMillis() - lastCloseAction));
        aapsLogger.info(LTag.PUMPBTCOMM, "" + (System.currentTimeMillis() - lastExecutedCommand));
        aapsLogger.info(LTag.PUMPBTCOMM, "" + gattConnected);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected());
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isDiscovering);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + connectionStatus);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + sleepSize);


        if (connectionStatus == ConnectionStatus.CLOSED && !connectionStatus.isConnecting() &&
                System.currentTimeMillis() - lastCloseAction <= 5000 &&
                !gattConnected && bluetoothConnectionGatt != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
            commandQueueBusy = false;
            close();
            return;
        } else if (connectionStatus != ConnectionStatus.CLOSED && !connectionStatus.isConnecting() &&
                System.currentTimeMillis() - lastCloseAction > 200000 &&
                bluetoothConnectionGatt != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
            commandQueueBusy = false;
            close();
            return;
        } else if (connectionStatus == ConnectionStatus.EXECUTING ||
                connectionStatus == ConnectionStatus.CONNECTED ||
                (connectionStatus == ConnectionStatus.CONNECTING &&
                        hasCommandsToExecute() &&
                        priorityExecutionCommandQueue.peekFirst().nextCommand() == MedLinkCommandType.Notification)) {
            aapsLogger.info(LTag.PUMPBTCOMM, "nextcommand");
            nextCommand();
            return;
        } else if (connectionStatus == ConnectionStatus.EXECUTING &&
                (lastExecutedCommand - System.currentTimeMillis() > 500000 ||
                        lastCloseAction - System.currentTimeMillis() > 500000)) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
            commandQueueBusy = false;
            disconnect();
        } else if (connectionStatus.isConnecting()) {
            return;
        }
        synchronized (connectionStatus) {
            lastCharacteristic = "";
            if (connectionStatus == ConnectionStatus.CLOSED) {
                changeConnectionStatus(ConnectionStatus.CONNECTING);
                long sleep = System.currentTimeMillis() - lastCloseAction;
                if (sleep < sleepSize) {
                    SystemClock.sleep(sleepSize - sleep);
                }
                connectGatt();
//                if(currentCommand)
                if (bluetoothConnectionGatt == null) {
                    connectTries++;
                    medLinkConnect();
                } else {
                    connectTries = 0;
                }

            } else {
                pumpResponse = new StringBuffer();
            }
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "ending medlinkconnect");
    }

    public boolean enableNotifications() {
        BLECommOperationResult result = setNotification_blocking(UUID.fromString(GattAttributes.SERVICE_UUID), //
                UUID.fromString(GattAttributes.GATT_UUID));
        if (result.resultCode != RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error setting response count notification");
            return false;
        }
        return true;
    }

    protected BLECommOperationResult setNotification_blocking(UUID serviceUUID, UUID charaUUID) {
        aapsLogger.debug("Enable medlink notification");
        aapsLogger.info(LTag.PUMPBTCOMM, "Enable medlink notification");
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + mCurrentOperation);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + connectionStatus);
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt == null) {
            medLinkConnect();
        }
        if (bluetoothConnectionGatt != null) {
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
//                    close();
                    disconnect();
                } else {
                    BluetoothGattCharacteristic characteristic = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    // Tell Android that we want the notifications
                    bluetoothConnectionGatt.setCharacteristicNotification(characteristic, true);
                    List<BluetoothGattDescriptor> list = characteristic.getDescriptors();
//                    if (gattDebugEnabled) {
                    for (int i = 0; i < list.size(); i++) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "Found descriptor: " + list.get(i).getUuid());
                        aapsLogger.info(LTag.PUMPBTCOMM, "Found descriptor: " + list.get(i).getCharacteristic().getUuid());
                    }
//                    }

                    BluetoothGattDescriptor descriptor = list.get(0);
                    // Tell the remote device to send the notifications

                    /////////////////////////////////////////////////////
                    // Check if characteristic is valid
                    if (characteristic == null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "ERROR: Characteristic is 'null', ignoring setNotify request");
                        rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
                        return rval;
                    }

                    // Get the CCC Descriptor for the characteristic
//                    final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID));
                    if (descriptor == null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.getUuid()));
                        rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
                        return rval;
                    }

                    // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
                    byte[] value;
                    int properties = characteristic.getProperties();
                    if ((properties & PROPERTY_NOTIFY) > 0) {
                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                    } else if ((properties & PROPERTY_INDICATE) > 0) {
                        value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
                        rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
                        return rval;
                    }

//                    if (needToAddConnectCommand()) {
//                        addExecuteConnectCommand();
//                    }

                    // Queue Runnable to turn on/off the notification now that all checks have been passed
                    if (!notificationEnabled
                            && priorityExecutionCommandQueue.stream().noneMatch(f ->
                            f.getMedLinkPumpMessage().getCommandType().equals(MedLinkCommandType.Notification))
                    ) {
//                        if (executionCommandQueue.stream().noneMatch(f -> f.contains(MedLinkCommandType.Notification))) {
                        executionCommandQueue.stream().findFirst().map(f -> {
                            f.clearExecutedCommand();
                            return false;
                        });
                        priorityExecutionCommandQueue.addFirst(new CommandExecutor(
                                MedLinkCommandType.Notification, aapsLogger, medLinkServiceData) {
                            @Override
                            public void run() {
                                commandConfirmed = false;
                                // First set notification for Gatt object
                                synchronized (commandQueueBusy) {
                                    if (bluetoothConnectionGatt != null) {
                                        try {
                                            if (!bluetoothConnectionGatt.setCharacteristicNotification(descriptor.getCharacteristic(), true)) {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.getUuid()));
                                            }
                                            nrRetries++;
                                            // Then write to descriptor
                                            descriptor.setValue(value);
                                            boolean result;
                                            result = bluetoothConnectionGatt.writeDescriptor(descriptor);

                                            notificationEnabled = true;
                                            if (!result) {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                                                needRetry = true;
                                            } else {
                                                aapsLogger.info(LTag.PUMPBTCOMM, String.format(" descriptor written: %s", descriptor.getUuid()));
                                                needRetry = false;
                                                commandQueueBusy = false;

                                                priorityExecutionCommandQueue.poll();
//                                            SystemClock.sleep(6000);
//                                            if (needToAddConnectCommand()) {
//                                                addExecuteConnectCommand();
//                                            }


                                            }
                                        } catch (NullPointerException e) {
                                            aapsLogger.info(LTag.PUMPBTCOMM, "npe  need retry");
                                            needRetry = true;
                                        }
                                    } else {
                                        aapsLogger.info(LTag.PUMPBTCOMM, "connection gatt is null need retry");
                                        needRetry = true;
                                    }
                                }
                            }
                        });
//                        }
                    }
//                    if (result) {
                    nextCommand();
                    rval.resultCode = RESULT_SUCCESS;

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
                    aapsLogger.info(LTag.PUMPBTCOMM, "nulling currentoperation");
                    mCurrentOperation = null;
                    return rval;
                }
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "setNotification_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }


    @Override public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
        gattConnected = connected;
        if (!connected) {
            this.pumpResponse = new StringBuffer();
        } else {
            lastConnection = System.currentTimeMillis();
            changeConnectionStatus(ConnectionStatus.CONNECTED);
        }
    }


    public void setPumpModel(String pumpModel) {
        this.pumpModel = pumpModel;
    }

    public void findMedLink(String medLinkAddress) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "MedLink address: " + medLinkAddress);
        // Must verify that this is a valid MAC, or crash.

        if (characteristicChanged == null) {
            characteristicChanged = new BleCommand(aapsLogger, medLinkServiceData);
        }
        rileyLinkDevice = bluetoothAdapter.getRemoteDevice(medLinkAddress);
        // if this succeeds, we get a connection state change callback?

        if (rileyLinkDevice != null) {
            connectGatt();
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device not found with address: " + medLinkAddress);
        }
    }


    public void disconnect() {
        changeConnectionStatus(ConnectionStatus.DISCONNECTING);
        servicesDiscovered = false;
        notificationEnabled = false;
        super.disconnect();
        aapsLogger.info(LTag.PUMPBTCOMM, "Post disconnect");
        setConnected(false);
        isDiscovering = false;
        commandQueueBusy = false;
    }


    public void close() {
        close(false);
    }

    public void close(boolean force) {
        if (currentCommand != null && currentCommand.hasFinished() && currentCommand.getMedLinkPumpMessage() != null) {
            currentCommand.getMedLinkPumpMessage().apply(this);
            removeFirstCommand(true);
        }
        if (force) {
            disconnect();
            if (System.currentTimeMillis() - lastConfirmedCommand < 6000 &&
                    currentCommand != null && MedLinkCommandType.Connect.isSameCommand(
                    currentCommand.getCurrentCommand())
            ) {
                connectGatt();
                return;
            }
        }
//        this.tryingToClose++;
        aapsLogger.info(LTag.EVENTS, "" + commandQueueBusy);
        aapsLogger.info(LTag.EVENTS, "" + (lastConnection - System.currentTimeMillis()));
        if (commandQueueBusy) {
            aapsLogger.info(LTag.EVENTS, "trying to close to close");
            return;
        }
        changeConnectionStatus(ConnectionStatus.CLOSING);
        setConnected(false);
        servicesDiscovered = false;
        isDiscovering = false;
        notificationEnabled = false;
        aapsLogger.info(LTag.EVENTS, "closing");
        super.close();
        changeConnectionStatus(ConnectionStatus.CLOSED);
        lastCloseAction = System.currentTimeMillis();
        previousLine = "";
        if ((!hasCommandsToExecute() && commandsToAdd.isEmpty())
        ) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Remaining commands");
            this.resultActivity.clear();
            this.addedCommands.clear();
            this.pumpResponse = new StringBuffer();
            if (currentCommand != null) {
                if (currentCommand.hasFinished()) {
                    removeFirstCommand(false);
                } else {
                    currentCommand.clearExecutedCommand();
                }
            }
            this.currentCommand = null;
            latestReceivedAnswer = 0L;
            lastCharacteristic = "";
        } else {
            if (currentCommand != null) {
                if (currentCommand.hasFinished()) {
                    removeFirstCommand(false);
                } else {
                    currentCommand.clearExecutedCommand();
                }

            }
            currentCommand = null;
            medLinkConnect();
        }
        aapsLogger.info(LTag.EVENTS, "ending close");
    }


    public void completedCommand() {
        if (currentCommand != null && currentCommand.getNrRetries() > MAX_TRIES) {
            aapsLogger.info(LTag.PUMPBTCOMM, "maxtries");
            aapsLogger.info(LTag.PUMPBTCOMM, "removing command");
            removeFirstCommand(true);
            disconnect();
            return;
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "completed command");
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + priorityExecutionCommandQueue.size());
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + executionCommandQueue.size());
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + lowPriorityExecutionCommandQueue.size());
        commandQueueBusy = false;
        isRetrying = false;
        removeFirstCommand(false);
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + priorityExecutionCommandQueue.size());
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + executionCommandQueue.size());
        aapsLogger.info(LTag.PUMPBTCOMM, "queue size " + lowPriorityExecutionCommandQueue.size());

        currentCommand = null;
        lastCharacteristic = "";
        CommandExecutor com = getNextCommand();
        if (hasCommandsToExecute() &&
                MedLinkCommandType.Connect.equals(com.getMedLinkPumpMessage().getCommandType())
                && isConnected) {
            aapsLogger.info(LTag.PUMPBTCOMM, "completed command");
            priorityExecutionCommandQueue.remove(com);
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
        processCommandToAdd();
        nextCommand();

    }

    private CommandExecutor getNextCommand() {
        if (!hasCommandsToExecute()) {
            return null;
        } else if (!priorityExecutionCommandQueue.isEmpty()) {
            return priorityExecutionCommandQueue.peek();
        } else if (!executionCommandQueue.isEmpty()) {
            return executionCommandQueue.peek();
        } else if (!lowPriorityExecutionCommandQueue.isEmpty()) {
            return lowPriorityExecutionCommandQueue.peek();
        }
        return null;
    }

    private void processCommandToAdd() {
        aapsLogger.info(LTag.PUMPBTCOMM, "processing commands to add");
        for (CommandsToAdd toAdd : commandsToAdd) {
            addWriteCharacteristic(toAdd.serviceUUID, toAdd.charaUUID,
                    toAdd.command, CommandPriority.NORMAL);

        }
        commandsToAdd.clear();
    }
//
//    public void retryCommand(MedLinkCommandType commandType) {
//        if (arguments.get(commandType) != null) {
//            MedLinkPumpMessage msg = arguments.remove(commandType);
//
//        }
//    }

    public void retryCommand() {
        commandQueueBusy = false;
        if (currentCommand != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Retrying " + currentCommand.nrRetries);
        }

        if (hasCommandsToExecute()) {
            if (currentCommand != null && currentCommand.getNrRetries() >= MAX_TRIES) {
//                currentCommand.clearExecutedCommand();
                // Max retries reached, give up on this one and proceed
                aapsLogger.error(LTag.PUMPBTCOMM, "Max number of tries reached");
                removeFirstCommand(true);
                completedCommand();
                currentCommand = null;
//            } else if (currentCommand == null) {

//            } else if (currentCommand.getCommand().isSameCommand(MedLinkCommandType.Bolus)) {
//                completedCommand();
            } else {
                nextCommand();
                isRetrying = true;
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

    public boolean needToAddConnectCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "" + commandsToAdd.size());
        aapsLogger.info(LTag.PUMPBTCOMM, "" + executionCommandQueue.size());

        return (
                lastConnection == 0L && !hasCommandsToExecute()
//                connectionStatus != ConnectionStatus.DISCOVERING &&
                        || (!isConnected
                        &&
                        hasCommandsToExecute() &&
                        getNextCommand().nextCommand() != null && (
//                        !isBolus(executionCommandQueue.peek().nextCommand()) &&
                        !MedLinkCommandType.ReadCharacteristic.isSameCommand(getNextCommand().nextCommand()) &&
//                                !MedLinkCommandType.StopStartPump.isSameCommand(executionCommandQueue.peek().nextCommand()) &&
                                !MedLinkCommandType.Notification.isSameCommand(getNextCommand().nextCommand())) &&
                        priorityExecutionCommandQueue.stream().noneMatch(f -> f.contains(MedLinkCommandType.Connect))
                ));
    }

    public synchronized void nextCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand " + servicesDiscovered);
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand " + commandQueueBusy);
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand " + connectionStatus);
        if (currentCommand != null && (currentCommand.hasFinished() || currentCommand.getNrRetries() > MAX_TRIES)) {
            removeFirstCommand(false);
        }
        printBuffer();
        if (connectionStatus == ConnectionStatus.EXECUTING ||
                connectionStatus == ConnectionStatus.CONNECTED ||
                connectionStatus == ConnectionStatus.DISCOVERING) {
            // If there is still a command being executed then bail out
            aapsLogger.info(LTag.PUMPBTCOMM, "CommandQueueBusy " + commandQueueBusy);
            aapsLogger.info(LTag.PUMPBTCOMM, "bluetoothConnectionGatt " + bluetoothConnectionGatt);
            if (commandQueueBusy) {
                return;
            }
            // Check if we still have a valid gatt object
            try {
                if (bluetoothConnectionGatt == null) {
                    aapsLogger.error(LTag.PUMPBTCOMM, String.format("ERROR: GATT is 'null' for peripheral '%s', clearing command queue", "Medlink"));
                    priorityExecutionCommandQueue.clear();
                    executionCommandQueue.clear();

//                commandQueueBusy = false;
                    return;
                }
                processCommandToAdd();
                // Execute the next command in the queue
                if (getNextCommand() != null) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "Queue size greater than 0 " + executionCommandQueue.size());
                    currentCommand = getNextCommand();
//                    aapsLogger.info(LTag.PUMPBTCOMM, "" + currentCommand.hasFinished());
//                    if (currentCommand != null && currentCommand.nextCommand() != null &&
//                            connectionStatus == ConnectionStatus.EXECUTING &&
//                            currentCommand.nextCommand().isSameCommand(MedLinkCommandType.Connect)) {
//                        nextCommand();
//                        return;
//                    } else {

                    if (needToAddConnectCommand()) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "need to add");
                        addExecuteConnectCommand();
                        currentCommand = getNextCommand();
                        nextCommand();
                        return;
                    }
                    commandQueueBusy = true;
                    if (currentCommand != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());
                        bleHandler.post(currentCommand);
                    }

                } else if (isConnected) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "empty execution queue");
                    disconnect();
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                return;
            }
        }
    }


    public Stream<CommandExecutor> getAllCommands() {
        return Stream.concat(Stream.concat(priorityExecutionCommandQueue.stream(),
                executionCommandQueue.stream()), lowPriorityExecutionCommandQueue.stream());
    }

    public void printBuffer() {
        StringBuilder buf = new StringBuilder("Print buffer");
        buf.append("\n");
        Iterator<CommandExecutor> it = getAllCommands().map(f -> {
            if (f.nextCommand() != null) return f;
            else return null;
        }).filter(Objects::nonNull).iterator();
        while (it.hasNext()) {
            buf.append(it.next().toString());
            buf.append("\n");
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "commands to add");
        Iterator<CommandsToAdd> it1 = commandsToAdd.iterator();
        while (it1.hasNext()) {
            buf.append(it1.next().command.getCommandType().code);
            buf.append("\n");
        }
        aapsLogger.info(LTag.PUMPBTCOMM, buf.toString());
    }

    public CommandExecutor getCurrentCommand() {
        return currentCommand;
    }

    @Override public boolean discoverServices() {
        aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name());
        if (connectionStatus == ConnectionStatus.CLOSED) {
            return super.discoverServices();
        } else {
            return false;
        }

    }
}