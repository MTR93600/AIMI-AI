
package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.RemainingBleCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.BLECommOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION;
import static android.bluetooth.BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
import static info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult.RESULT_NOT_CONFIGURED;
import static info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult.RESULT_SUCCESS;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by Dirceu on 30/09/20.
 */
@Singleton
public class MedLinkBLE extends RileyLinkBLE {
    private final ResourceHelper resourceHelper;
    private boolean needRetry;
    private long lastCloseAction = 0L;
    private Map<MedLinkCommandType, MedLinkPumpMessage> arguments = new HashMap<>();
    private long lastExecutedCommand;
    private boolean isDiscoverying;
    private long sleepSize;
    private long lastConnection;

    private ConnectionStatus connectionStatus = ConnectionStatus.CLOSED;
    private boolean notificationEnabled;

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

    public void setSleepSize(long sleepSize) {
        this.sleepSize = sleepSize;
    }

    public void applyClose() {
        aapsLogger.info(LTag.PUMPBTCOMM, "applying close");
        if (executionCommandQueue.isEmpty() && connectionStatus != ConnectionStatus.CLOSED
                && connectionStatus != ConnectionStatus.CLOSING &&
                connectionStatus != ConnectionStatus.CONNECTING) {
            disconnect();
        }
    }

    public void clearCommands() {
        executionCommandQueue.clear();
    }

//    public void clearCommands() {
//        executionCommandQueueN.clear();
//        executionCommandQueue.clear();
//        close(true);
//    }

    private class CommandsToAdd {
        private final boolean hasArg;
        private UUID serviceUUID;
        private UUID charaUUID;
        private byte[] command;
        private Function func;

        public CommandsToAdd(UUID serviceUUID, UUID charaUUID, byte[] command, Function func, boolean hasArg) {
            this.serviceUUID = serviceUUID;
            this.charaUUID = charaUUID;
            this.command = command;
            this.func = func;
            this.hasArg = hasArg;
        }
    }

    private final Set<BluetoothGattCharacteristic> notifyingCharacteristics = new HashSet<>();
    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private int nrTries;
    private Boolean commandQueueBusy;
    private int MAX_TRIES = 5;
    private boolean isRetrying;
    private Handler bleHandler = new Handler();
    private Function function;
    private ConcurrentLinkedDeque<CommandsToAdd> commandsToAdd = new ConcurrentLinkedDeque<>();


    private enum BLEOperationType {
        READ_CHARACTERISTIC_BLOCKING,
        WRITE_CHARACTERISTIC_BLOCKING,
        SEND_NOTIFICATION_BLOCKING,
    }

    private BleBolusCommand characteristicChanged;
    private int tryingToClose = 0;
    private Set<String> remainingCommandsSet = new HashSet<>();


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

    private long latestReceivedCommand = 0l;
    private long latestReceivedAnswer = 0l;
    private boolean isConnected = false;
    private String pumpModel = null;
    private Set<String> addedCommands = new HashSet<>();
    private ConcurrentLinkedDeque<CommandExecutor> executionCommandQueue = new ConcurrentLinkedDeque<>();
    //    private ConcurrentLinkedDeque<String> executionCommandQueueN = new ConcurrentLinkedDeque<>();
    private RemainingBleCommand currentCommand;
    private BLECommOperation mCurrentOperation;
    List<Resp> resultActivity = new ArrayList<>();
    private boolean servicesDiscovered = false;

    private String lastCharacteristic = "";

    @Inject
    public MedLinkBLE(final Context context, ResourceHelper resourceHelper) {
        super(context);
        MedLinkBLE that = this;
        this.resourceHelper = resourceHelper;
        bluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt,
                                                final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                String answer = new String(characteristic.getValue()).toLowerCase();

                latestReceivedAnswer = System.currentTimeMillis();
                characteristicChanged.characteristicChanged(answer, that, lastCharacteristic);
                //                if (answer.contains("bolus"))
                aapsLogger.info(LTag.PUMPBTCOMM, answer);
                lastCharacteristic = answer;
                if (radioResponseCountNotified != null) {
                    radioResponseCountNotified.run();
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
                        connectionStatus = ConnectionStatus.CLOSED;
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
                        lastConnection = System.currentTimeMillis();
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.BluetoothConnected, context);
                        int bondstate = rileyLinkDevice.getBondState();        // Take action depending on the bond state
                        if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "Discoverying Services");
                            aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name());
                            if (connectionStatus == ConnectionStatus.CONNECTING && bluetoothConnectionGatt != null) {
                                synchronized (bluetoothConnectionGatt) {
                                    connectionStatus = ConnectionStatus.DISCOVERING;
                                    isDiscoverying = true;
                                    bluetoothConnectionGatt.discoverServices();
                                }
                            }
                        }


                    } else if ((newState == BluetoothProfile.STATE_CONNECTING) || //
                            (newState == BluetoothProfile.STATE_DISCONNECTING)) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "We are in {} state.", status == BluetoothProfile.STATE_CONNECTING ? "Connecting" :
                                "Disconnecting");
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (latestReceivedAnswer > 0 && executionCommandQueue.isEmpty()) {
                            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.CommandCompleted, context);
                            latestReceivedAnswer = 0l;
                            //TODO fix handling this events
                        } else if (nrTries > 4) {
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
                            new String(currentCommand.getCommand()), parentCharacteristic.getUuid(), "Medlink", status));
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
                    aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.getUuid());
                    SystemClock.sleep(6000);
                    aapsLogger.info(LTag.PUMPBTCOMM, "descriptor written " + descriptor.getUuid());
                    connectionStatus = ConnectionStatus.CONNECTED;
                    completedCommand(true);
                }
            }


            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
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

                    boolean medLinkFound = MedLinkConst.DEVICE_NAME.contains(gatt.getDevice().getName()) &&
                            gatt.getDevice().getAddress().equals(sp.getString(MedLinkConst.Prefs.MedLinkAddress, ""));

                    if (gattDebugEnabled) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    }

                    aapsLogger.info(LTag.PUMPBTCOMM, "Gatt device is MedLink device: " +
                            medLinkFound + " " + gatt.getDevice().getName() + " " +
                            gatt.getDevice().getAddress());

                    if (medLinkFound) {
                        mIsConnected = true;
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkReady, context);
                        servicesDiscovered = true;
                        commandQueueBusy = false;
//                        enableNotifications();
//                        nextCommand();
                    } else {
                        mIsConnected = false;
                        if (System.currentTimeMillis() - latestReceivedAnswer > 600000) {
                            medLinkServiceData.setServiceState(MedLinkServiceState.MedLinkError,
                                    MedLinkError.DeviceIsNotMedLink);
                        } else {
                            aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                            disconnect();
                        }
                    }

                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    disconnect();
                    medLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed, context);
                }
            }
        };
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


                            boolean result = executionCommandQueue.add(new CommandExecutor("ReadCharacteristic", new RemainingBleCommand(UUID.fromString(GattAttributes.SERVICE_UUID),
                                    UUID.fromString(GattAttributes.GATT_UUID),
                                    null, null, false)) {
                                @Override
                                public void run() {
                                    synchronized (bluetoothConnectionGatt) {
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

    public BLECommOperationResult writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID,
                                                               byte[] command, Function func,
                                                               boolean prepend, boolean hasArg) {
        this.latestReceivedCommand = System.currentTimeMillis();
        aapsLogger.info(LTag.PUMPBTCOMM, "commands");
        aapsLogger.info(LTag.PUMPBTCOMM, new String(command));
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            rval.value = command;

            if (mCurrentOperation != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "busy for the command " + command);
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
                    // app that created the bluetoothConnectionGatt has been destroyed/created,
                    // e.g. when the user switches from portrait to landscape.
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    close();
                    return rval;
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    CommandExecutor commandExecutor;
                    synchronized (bluetoothConnectionGatt) {
                        synchronized (commandQueueBusy) {
                            BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                                    .getCharacteristic(charaUUID);
                            int mWriteType;
                            if ((chara.getProperties() & PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                mWriteType = WRITE_TYPE_NO_RESPONSE;
                            } else {
                                mWriteType = WRITE_TYPE_DEFAULT;
                            }
                            int writeProperty;
                            switch (mWriteType) {
                                case WRITE_TYPE_DEFAULT:
                                    writeProperty = PROPERTY_WRITE;
                                    break;
                                case WRITE_TYPE_NO_RESPONSE:
                                    writeProperty = PROPERTY_WRITE_NO_RESPONSE;
                                    break;
                                case WRITE_TYPE_SIGNED:
                                    writeProperty = PROPERTY_SIGNED_WRITE;
                                    break;
                                default:
                                    writeProperty = 0;
                                    break;
                            }

                            if ((chara.getProperties() & writeProperty) == 0) {
                                aapsLogger.error(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "ERROR: Characteristic <%s> does not support writeType '%s'", chara.getUuid(), mWriteType));
                                return rval;
                            }
                            MedLinkBLE that = this;
                            RemainingBleCommand remCom = new RemainingBleCommand(serviceUUID, charaUUID, command,
                                    func, hasArg);
                            commandExecutor = new CommandExecutor(new String(command, UTF_8), remCom) {
                                @Override public void run() {
                                    lastExecutedCommand = System.currentTimeMillis();
                                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                                            .getCharacteristic(charaUUID);
                                    chara.setValue(command);
//                                    chara.setWriteType(PROPERTY_WRITE); //TODO validate
                                    nrTries++;
                                    aapsLogger.debug(LTag.PUMPBTCOMM, "running command");
                                    aapsLogger.debug(LTag.PUMPBTCOMM, new String(command, UTF_8));

                                    if (!bluetoothConnectionGatt.writeCharacteristic(chara)) {
                                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeCharacteristic failed for characteristic: %s", chara.getUuid()));
                                        needRetry = true;
                                        commandQueueBusy = false;
                                    } else {
                                        that.setFunction(func);
                                        aapsLogger.info(LTag.PUMPBTCOMM, String.format("writing <%s> to characteristic <%s>", new String(command, UTF_8), chara.getUuid()));
                                    }
                                }

                            };
                        }
                    }
                    if (commandExecutor != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, "adding command" + new String(command, UTF_8));
                        if (prepend) {
                            this.executionCommandQueue.addFirst(commandExecutor);
                        } else {
                            this.executionCommandQueue.add(commandExecutor);
                        }
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, "not adding command" + new String(command, UTF_8));
                        if (bluetoothConnectionGatt == null) {
                            medLinkConnect();

                        }
                    }
                }
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!");
            rval.resultCode = RESULT_NOT_CONFIGURED;
        }
        if (rval.value == null) {
            rval.value = getPumpResponse();
        }
        return rval;
    }

    protected byte[] getPumpResponse() {
        byte[] result = StringUtils.join(this.pumpResponse, ",").getBytes();
        this.pumpResponse = new StringBuffer();
        return result;
    }

    public synchronized void writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID, MedLinkPumpMessage msg) {

//        updateActivity(msg.getBaseCallBack(), msg.getCommandType().code);
        aapsLogger.info(LTag.PUMPBTCOMM, "writeCharblocking");
        aapsLogger.info(LTag.PUMPBTCOMM, msg.getCommandType().code);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt);
        aapsLogger.info(LTag.PUMPBTCOMM, connectionStatus.name());
        aapsLogger.info(LTag.PUMPBTCOMM, ""+msg.getBtSleepTime());
        if(msg.getBtSleepTime()>0) {
            this.sleepSize = msg.getBtSleepTime();
        }
        if (!addedCommands.contains(msg.getCommandType().code)) {
            addedCommands.add(msg.getCommandType().code);
//            if (this.isConnected && bluetoothConnectionGatt != null) {
//                synchronized (executionCommandQueue) {
//                    writeCharacteristic_blocking(serviceUUID, charaUUID, msg.getCommandData(),
//                            msg.getBaseCallback(), false, msg.getArgument() != null);
//                    if (msg.getArgument() != MedLinkCommandType.NoCommand) {
//                        this.arguments.put(msg.getArgument(), msg);
//                        if (msg.getArgument() == MedLinkCommandType.BaseProfile) {
//                            writeCharacteristic_blocking(serviceUUID, charaUUID,
//                                    msg.getArgumentData(),
//                                    ((BasalMedLinkMessage<Profile>) msg).getArgCallback(),
//                                    false, msg.getArgument() != null);
//                        } else if (msg.getArgument() == MedLinkCommandType.IsigHistory) {
//                            writeCharacteristic_blocking(serviceUUID, charaUUID,
//                                    msg.getArgumentData(),
//                                    msg.getArgCallback(),
//                                    false, msg.getArgument() != null);
//                        } else {
//                            writeCharacteristic_blocking(serviceUUID, charaUUID, msg.getArgumentData(),
//                                    msg.getBaseCallback(), false, msg.getArgument() != null);
//                        }
//                    }
//                }
//
//                nextCommand();
//            } else {
//                if (msg.getCommandType().code != MedLinkCommandType.Connect.code &&
//                        needToAddConnectCommand()) {
//                    aapsLogger.info(LTag.PUMPBTCOMM, "adding connect command");
//                    addExecuteCommandToCommands();
//                }

            addCommands(serviceUUID, charaUUID, msg, false);
            aapsLogger.info(LTag.PUMPBTCOMM, "before connect");
        }
        if (!connectionStatus.isConnecting()) {
            medLinkConnect();
        }
//        } else {
//            if (this.isConnected && bluetoothConnectionGatt != null) {
////                if (this.lastExecutedCommand - this.latestReceivedCommand > 60000) {
////                    close();
////                } else {
//                    nextCommand();
////                }
//            } else {
//                aapsLogger.info(LTag.PUMPBTCOMM, "before connect");
//                medLinkConnect();
//            }
//        }

        this.latestReceivedCommand = System.currentTimeMillis();
    }

    private void addCommands(UUID serviceUUID, UUID charaUUID, MedLinkPumpMessage msg,
                             boolean first) {

        synchronized (commandsToAdd) {
            CommandsToAdd command = new CommandsToAdd(serviceUUID, charaUUID, msg.getCommandData(),
                    msg.getBaseCallback(), msg.getArgument() != null);
            addCommand(command, first);
            if (msg.getArgument() != MedLinkCommandType.NoCommand) {
                if (msg.getArgument() == MedLinkCommandType.BaseProfile || msg.getArgument() == MedLinkCommandType.IsigHistory) {
                    addCommand(new CommandsToAdd(serviceUUID, charaUUID,
                            msg.getArgumentData(),
                            msg.getArgCallback(), msg.getArgument() != null), first);
                } else {
                    addCommand(new CommandsToAdd(serviceUUID, charaUUID, msg.getArgumentData(),
                            msg.getBaseCallback(), msg.getArgument() != null), first);
                }
            }
        }
    }

    private void addCommand(CommandsToAdd command, boolean first) {
        aapsLogger.info(LTag.PUMPBTCOMM, "adding Command " + new String(command.command));
        aapsLogger.info(LTag.PUMPBTCOMM, "adding Command " + first);
        if (first) {
            this.commandsToAdd.addFirst(command);
        } else {
            this.commandsToAdd.add(command);
        }
    }

    @Override public void connectGatt() {
        connectionStatus = ConnectionStatus.CONNECTING;
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

    private synchronized void answerTimeoutCommands() {
        this.resultActivity.forEach(f -> {
            Supplier<Stream<String>> supplier = () -> Stream.of("TimeoutError");
            f.getFunc().apply(supplier);
        });
        this.resultActivity.clear();
//        this.commandQueue.clear();
        this.remainingCommandsSet.clear();
    }

    private boolean addedTimeout() {
        long delta = System.currentTimeMillis() - this.latestReceivedCommand;
        return this.latestReceivedCommand > 0 && delta > 50000;
    }

    private boolean answerTimeout() {
        long delta = System.currentTimeMillis() - this.latestReceivedAnswer;
        return this.latestReceivedAnswer > 0 && delta > 10000;
    }

//    private void updateActivity(Function resultActivity, String command) {
//        if (this.resultActivity != null) {
//            aapsLogger.info(LTag.PUMPBTCOMM, command + " resultActivity added " + this.resultActivity.toString());
//        }
//        this.resultActivity.add(new Resp(resultActivity, command));
//    }

    public void addExecuteCommandToCommands() {
        Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> ret = s -> {
            return new MedLinkStandardReturn<String>(s, "");
        };
        MedLinkPumpMessage<String> msg = new MedLinkPumpMessage<String>(MedLinkCommandType.Connect,
                MedLinkCommandType.NoCommand,
                ret, medLinkServiceData, aapsLogger, sleepSize);
        addCommands(UUID.fromString(GattAttributes.SERVICE_UUID),
                UUID.fromString(GattAttributes.GATT_UUID),
                msg, true);
    }

    public void addExecuteConnectCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "ad ok conn command ");
        writeCharacteristic_blocking(UUID.fromString(GattAttributes.SERVICE_UUID),
                UUID.fromString(GattAttributes.GATT_UUID), MedLinkCommandType.Connect.getRaw(),
                s -> {
                    return s;
                }, true, false);
    }

    public void setMedlinkReconnectInterval() {

    }

    private void medLinkConnect() {

        aapsLogger.info(LTag.PUMPBTCOMM, "connecting medlink");
        aapsLogger.info(LTag.PUMPBTCOMM, "" + (System.currentTimeMillis() - lastCloseAction));
        aapsLogger.info(LTag.PUMPBTCOMM, "" + (System.currentTimeMillis() - lastExecutedCommand));
        aapsLogger.info(LTag.PUMPBTCOMM, "" + gattConnected);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + bluetoothConnectionGatt);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected());
        aapsLogger.info(LTag.PUMPBTCOMM, "" + isDiscoverying);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + connectionStatus);
        aapsLogger.info(LTag.PUMPBTCOMM, "" + sleepSize);


        if (connectionStatus == ConnectionStatus.CLOSED && !connectionStatus.isConnecting() &&
                System.currentTimeMillis() - lastCloseAction <= 5000 &&
                !gattConnected && bluetoothConnectionGatt != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
            commandQueueBusy = false;
            close();
            return;
        } else
        if (connectionStatus != ConnectionStatus.CLOSED && !connectionStatus.isConnecting() &&
                System.currentTimeMillis() - lastCloseAction > 200000 &&
                !gattConnected && bluetoothConnectionGatt != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "closing");
            commandQueueBusy = false;
            close();
            return;
        } else if (connectionStatus == ConnectionStatus.EXECUTING ||
                connectionStatus == ConnectionStatus.CONNECTED ||
                (connectionStatus == ConnectionStatus.CONNECTING &&
                        !executionCommandQueue.isEmpty() &&
                        executionCommandQueue.peekFirst().getCommand().equals("SetnotificationBlocking"))) {
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
                connectionStatus = ConnectionStatus.CONNECTING;
                long sleep = System.currentTimeMillis() - lastCloseAction;
                if (sleep < sleepSize) {
                    SystemClock.sleep(sleepSize);
                }
                connectGatt();

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

                    if (needToAddConnectCommand()) {
                        addExecuteConnectCommand();
                    }

                    // Queue Runnable to turn on/off the notification now that all checks have been passed
                    String commandName = "SetnotificationBlocking";
                    if (!notificationEnabled && !executionCommandQueue.stream().anyMatch(f -> f.getCommand().equals(commandName))) {
                        executionCommandQueue.addFirst(new CommandExecutor(commandName, new RemainingBleCommand(serviceUUID, charaUUID,
                                value, null, false)) {
                            @Override
                            public void run() {
                                // First set notification for Gatt object
                                synchronized (bluetoothConnectionGatt) {
                                    synchronized (commandQueueBusy) {
                                        if (!bluetoothConnectionGatt.setCharacteristicNotification(descriptor.getCharacteristic(), true)) {
                                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.getUuid()));
                                        }

                                        // Then write to descriptor
                                        descriptor.setValue(value);
                                        boolean result;
                                        result = bluetoothConnectionGatt.writeDescriptor(descriptor);
                                        executionCommandQueue.peek();
                                        notificationEnabled = true;
                                        if (!result) {
                                            aapsLogger.info(LTag.PUMPBTCOMM, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                                            needRetry = true;
                                            nrTries++;
                                        } else {
                                            aapsLogger.info(LTag.PUMPBTCOMM, String.format(" descriptor written: %s", descriptor.getUuid()));
                                            commandQueueBusy = false;
//                                        SystemClock.sleep(3000);
//                                        completedCommand(true);
                                        }

                                    }
                                }
                            }
                        });
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
        }
    }


    public void setPumpModel(String pumpModel) {
        this.pumpModel = pumpModel;
    }

    public void findMedLink(String medLinkAddress) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "MedLink address: " + medLinkAddress);
        // Must verify that this is a valid MAC, or crash.

        if (characteristicChanged == null) {
            characteristicChanged = new BleBolusCommand(aapsLogger, medLinkServiceData);
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
        connectionStatus = ConnectionStatus.DISCONNECTING;
        servicesDiscovered = false;
        notificationEnabled = false;
        super.disconnect();
        aapsLogger.info(LTag.PUMPBTCOMM, "Post disconnect");
        setConnected(false);
        isDiscoverying = false;
        commandQueueBusy = false;
    }


    public void close() {
        close(false);
    }

    public void close(boolean force) {
        this.tryingToClose++;
        aapsLogger.info(LTag.EVENTS, "" + commandQueueBusy);
        aapsLogger.info(LTag.EVENTS, "" + (lastConnection - System.currentTimeMillis()));
        if (commandQueueBusy) {
            aapsLogger.info(LTag.EVENTS, "trying to close to close");
            return;
        }
        connectionStatus = ConnectionStatus.CLOSING;
//        if (tryingToClose > 5 && !force) {
//            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnected, context);
//            return;
//        }
//        disconnect();
        setConnected(false);
        servicesDiscovered = false;
        isDiscoverying = false;
        notificationEnabled = false;
        aapsLogger.info(LTag.EVENTS, "closing");
        super.close();
        connectionStatus = ConnectionStatus.CLOSED;
        lastCloseAction = System.currentTimeMillis();
        if ((executionCommandQueue.isEmpty() && commandsToAdd.isEmpty()) || (
                remainingBolusCommand())) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Remaining commands");
            this.resultActivity.clear();
            this.executionCommandQueue.clear();
            this.remainingCommandsSet.clear();
            this.addedCommands.clear();
            this.pumpResponse = new StringBuffer();
            this.currentCommand = null;
            latestReceivedAnswer = 0L;
            latestReceivedCommand = 0L;
            tryingToClose = 0;
            lastCharacteristic = "";
        } else {
            medLinkConnect();
        }
        aapsLogger.info(LTag.EVENTS, "ending close");
    }

    private boolean remainingBolusCommand() {
        if (!executionCommandQueue.isEmpty()) {
            CommandExecutor remComm = executionCommandQueue.peek();
//            executionCommandQueueN.poll();
            if (remComm.getRemainingBleCommand() != null &&
                    remComm.getRemainingBleCommand().getCommand() != null) {
                String command = new String(remComm.getRemainingBleCommand().getCommand(), UTF_8);
                return command.contains("BOLUS");
            }
        }
        return false;
    }

    public void completedCommand() {
        completedCommand(true);
    }

    public void completedCommand(Boolean nextCommand) {
        aapsLogger.info(LTag.PUMPBTCOMM, "completed command");
        commandQueueBusy = false;
        isRetrying = false;
        currentCommand = null;
        lastCharacteristic = "";
        CommandExecutor com = executionCommandQueue.peek();
        if (com != null) {
            RemainingBleCommand remCom = com.getRemainingBleCommand();
            if (remCom != null) {
                String commandCode = new String(remCom.getCommand(), UTF_8);
                addedCommands.remove(commandCode);
                if (remCom.hasArg()) {
                    CommandExecutor commArg = executionCommandQueue.peek();
                    if (commArg != null && commArg.getRemainingBleCommand() != null) {
                        addedCommands.remove(new String(commArg.getRemainingBleCommand().getCommand(), UTF_8));
                    }
                }
            }
        }
        executionCommandQueue.poll();
        processCommandToAdd();
        nrTries = 0;
        if (nextCommand) {
            nextCommand();
        }
    }

    private void processCommandToAdd() {
        aapsLogger.info(LTag.PUMPBTCOMM, "processing commands to add");
        for (CommandsToAdd toAdd : commandsToAdd) {
            writeCharacteristic_blocking(toAdd.serviceUUID, toAdd.charaUUID,
                    toAdd.command, toAdd.func, false, toAdd.hasArg);
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
        aapsLogger.info(LTag.PUMPBTCOMM, "Retrying " + nrTries);

        if (!executionCommandQueue.isEmpty()) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                aapsLogger.error(LTag.PUMPBTCOMM, "Max number of tries reached");
                completedCommand();
            } else if (currentCommand == null) {
                nextCommand();
            } else if (new String(currentCommand.getCommand(), UTF_8).startsWith(MedLinkCommandType.BolusAmount.code)) {
                completedCommand();
            } else {
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
        printBuffer();
        Stream<RemainingBleCommand> remainingCommand = executionCommandQueue.stream().map(CommandExecutor::getRemainingBleCommand);
        return (!commandsToAdd.isEmpty() && commandsToAdd.stream().noneMatch( f -> Arrays.equals(f.command,
                        MedLinkCommandType.Connect.getRaw())))
                || remainingCommand.noneMatch(f -> f.getCommand().equals(MedLinkCommandType.Connect.code));
    }

    public synchronized void nextCommand() {
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand ");
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand " + servicesDiscovered);
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand " + commandQueueBusy);
        aapsLogger.info(LTag.PUMPBTCOMM, "nextCommand " + connectionStatus);
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
            synchronized (bluetoothConnectionGatt) {
                if (bluetoothConnectionGatt == null) {
                    aapsLogger.error(LTag.PUMPBTCOMM, String.format("ERROR: GATT is 'null' for peripheral '%s', clearing command queue", "Medlink"));
                    executionCommandQueue.clear();

//                commandQueueBusy = false;
                    return;
                }
                processCommandToAdd();
                // Execute the next command in the queue
                if (executionCommandQueue.size() > 0) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "Queue size greater than 0");
                    CommandExecutor runnable = executionCommandQueue.peek();
                    printBuffer();
                    currentCommand = runnable.getRemainingBleCommand();
                    if(currentCommand != null && currentCommand.getCommand() != null &&
                            connectionStatus == ConnectionStatus.EXECUTING &&
                    Arrays.equals(currentCommand.getCommand(), MedLinkCommandType.Connect.getRaw())){
                        nextCommand();
                        return;
                    } else {
                        aapsLogger.info(LTag.PUMPBTCOMM, currentCommand.toString());
                        commandQueueBusy = true;
                        runnable.run();
                        if (needRetry) {
//                    close(true);
                            SystemClock.sleep(1000);
                            disconnect();
//                    retryCommand();
                        }
                        needRetry = false;
                    }
                } else {
                    aapsLogger.info(LTag.PUMPBTCOMM, "empty execution queue");
//                close(true);
                    disconnect();
                }
            }
        }
    }

    public void printBuffer() {
        StringBuffer buf = new StringBuffer("Print buffer");
        buf.append("\n");
        Iterator<String> it = executionCommandQueue.stream().map(f -> f.getCommand()).iterator();
        while (it.hasNext()) {
            buf.append(it.next());
            buf.append("\n");
        }
        aapsLogger.info(LTag.PUMPBTCOMM, buf.toString());
        aapsLogger.info(LTag.PUMPBTCOMM, "commands to add");
        Iterator<CommandsToAdd> it1 = commandsToAdd.iterator();
        while (it.hasNext()) {
            buf.append(new String(it.next().getBytes(), UTF_8));
            buf.append("\n");
        }
    }

    public RemainingBleCommand getCurrentCommand() {
        return currentCommand;
    }

    public void setFunction(Function function) {
        this.function = function;
    }

    public Function getFunction() {
        return function;
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