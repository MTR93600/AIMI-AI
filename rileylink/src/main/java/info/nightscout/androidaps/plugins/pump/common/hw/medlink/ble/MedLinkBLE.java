package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BasalMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.BLECommOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.CharacteristicReadOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.CharacteristicWriteOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.operations.DescriptorWriteOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;

import static info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult.RESULT_INTERRUPTED;
import static info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult.RESULT_SUCCESS;
import static info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult.RESULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by Dirceu on 30/09/20.
 */
@Singleton
public class MedLinkBLE extends RileyLinkBLE {

    private boolean executing;

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
    private List<RemainingCommand> remainingCommands = new ArrayList<>();
    private RemainingCommand currentCommand;
    private BLECommOperation mCurrentOperation;
    List<Resp> resultActivity = new ArrayList<>();
    private CharacteristicWriteOperation nextCurrentOperation;

    @Inject
    public MedLinkBLE(final Context context) {
        super(context);

        bluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt,
                                                final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                latestReceivedAnswer = System.currentTimeMillis();
//                if(StringUtil.fromBytes(characteristic.getValue()).toLowerCase().contains("ready")) {
//                 aapsLogger.info(LTag.PUMPBTCOMM, StringUtil.fromBytes(characteristic.getValue()));
//                }
//                if (gattDebugEnabled) {
//                    aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicChanged "
//                            + GattAttributes.lookup(characteristic.getUuid()) + " "
//                            + ByteUtil.getHex(characteristic.getValue()) + " " +
//                            StringUtil.fromBytes(characteristic.getValue()));
////                    if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT))) {
////                        aapsLogger.debug(LTag.PUMPBTCOMM, "Response Count is " + ByteUtil.shortHexString(characteristic.getValue()));
////                    }
//                }
                if (radioResponseCountNotified != null) {
                    radioResponseCountNotified.run();
                }
                String answer = new String(characteristic.getValue()).toLowerCase();
                if(pumpResponse.length()==0){
                    pumpResponse.append(System.currentTimeMillis());
                    pumpResponse.append("\n");
                }
                pumpResponse.append(answer);
                if (answer.trim().equals("powerdown")) {
                    if(currentCommand!= null && currentCommand.command ==
                            MedLinkCommandType.BolusHistory.getRaw()) {
                        applyResponse(answer);
                    }
                    aapsLogger.debug("MedLink off " + answer);
                    close();
                    return;
                }
                if (answer.trim().equals("confirmed pump wake-up")) {
                    aapsLogger.debug("MedLink waked " + answer);
                }
                if (answer.contains("error communication")) {
                    aapsLogger.debug("MedLink waked " + answer);
                    medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkError);
                }
                if (answer.contains("medtronic")) {
                    setPumpModel(answer);
                }
                if (answer.contains("eomeomeom")) {
                    release();
                    aapsLogger.info(LTag.PUMPBTCOMM, pumpResponse.toString());
                    aapsLogger.info(LTag.PUMPBTCOMM, "Pump Apply response");
                    applyResponse(pumpResponse.toString());
                    pumpResponse = new StringBuffer();
                    currentCommand = null;
                    return;
//                    medLinkUtil.sendBroadcastMessage(pumpResp.toString(), context);
                }
                if (answer.trim().contains("ok+conn")) {
                    //medLinkUtil.sendBroadcastMessage();
                    release();
                    setConnected(false);
                    aapsLogger.debug("MedLink answering " + answer);
                    aapsLogger.debug("MedLink answering " + remainingCommands.size());
                    aapsLogger.debug("MedLink answering " + remainingCommands);
                    if (!remainingCommands.isEmpty()) {
                        if (currentCommand == null && isFirstRemaningConnect()) {
                            medLinkConnect();
                        } else {
                            processRemainingCommand();
                        }
                    }
                    return;
                }
                if (answer.trim().contains("ready")) {
                    release();
                    setConnected(true);
                    aapsLogger.info(LTag.PUMPBTCOMM,"MedLink Ready");
                    if (currentCommand != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM,"Applying");
                        aapsLogger.info(LTag.PUMPBTCOMM,new String(currentCommand.command));
                        applyResponse(pumpResponse.toString());
                        pumpResponse = new StringBuffer();
                        currentCommand = null;
                    }
                    medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);
                    processRemainingCommand();
                    return;
                }
                if (answer.trim().toLowerCase().contains("set bolus")) {
//                    removeFirstRemaningCommand();
                    processRemainingCommand();
                }

            }


            @Override
            public void onCharacteristicRead(final BluetoothGatt gatt,
                                             final BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                aapsLogger.debug(LTag.PUMPBTCOMM, "onCharRead ");
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
                aapsLogger.debug("oncharwrite");
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
                    disconnect();
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
                        stateMessage = "DISCONNECTED";
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                        stateMessage = "DISCONNECTING";
                    } else {
                        stateMessage = "UNKNOWN newState (" + newState + ")";
                    }

                    aapsLogger.warn(LTag.PUMPBTCOMM, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage);
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.BluetoothConnected, context);
                    } else {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "BT State connected, GATT status {} ({})", status, getGattStatusMessage(status));
                    }

                } else if ((newState == BluetoothProfile.STATE_CONNECTING) || //
                        (newState == BluetoothProfile.STATE_DISCONNECTING)) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "We are in {} state.", status == BluetoothProfile.STATE_CONNECTING ? "Connecting" :
                            "Disconnecting");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (latestReceivedAnswer > 0) {
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.CommandCompleted, context);
                        //TODO fix handling this events
                    } else {
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnected, context);
                    }
                    acquire();
                    close();
                    release();
                    aapsLogger.warn(LTag.PUMPBTCOMM, "MedLink Disconnected.");
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "Some other state: (status={},newState={})", status, newState);
                }
            }


            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                aapsLogger.debug(LTag.PUMPBTCOMM, "onDescriptorWrite ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorWrite " + GattAttributes.lookup(descriptor.getUuid()) + " "
                            + getGattStatusMessage(status) + " written: " + ByteUtil.getHex(descriptor.getValue()));
                }
                if (mCurrentOperation != null) {
                    mCurrentOperation.gattOperationCompletionCallback(descriptor.getUuid(), descriptor.getValue());
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
                aapsLogger.debug(LTag.PUMPBTCOMM, "onMtuchanged ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onMtuChanged " + mtu + " status " + status);
                }
            }


            @Override
            public void onReadRemoteRssi(final BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                aapsLogger.debug(LTag.PUMPBTCOMM, "onReadRemoteRssi ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi);
                }
            }


            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                aapsLogger.debug(LTag.PUMPBTCOMM, "onReliableWriteCompleted ");
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReliableWriteCompleted status " + status);
                }
            }


            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered ");
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    boolean medLinkFound = MedLinkConst.DEVICE_NAME.contains(gatt.getDevice().getName());

                    if (gattDebugEnabled) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    }

                    aapsLogger.info(LTag.PUMPBTCOMM, "Gatt device is MedLink device: " + medLinkFound);

                    if (medLinkFound) {
                        mIsConnected = true;
                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkReady, context);
                    } else {
                        mIsConnected = false;
                        medLinkServiceData.setServiceState(RileyLinkServiceState.RileyLinkError,
                                RileyLinkError.DeviceIsNotRileyLink);
                    }

                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    medLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed, context);
                }
            }
//            @Override
//            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
//
//                super.onServicesDiscovered(gatt, status);
//
//                if (status == BluetoothGatt.GATT_SUCCESS) {
//                    final List<BluetoothGattService> services = gatt.getServices();
//
//                    boolean medLinkFound = MedLinkConst.DEVICE_NAME.contains(gatt.getDevice().getName());
//                    if (medLinkFound) {
//                        mIsConnected = true;
//                        medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkReady, context);
//                    } else {
//                        mIsConnected = false;
//                        medLinkServiceData.setServiceState(RileyLinkServiceState.RileyLinkError,
//                                RileyLinkError.DeviceIsNotRileyLink);
//                    }
//
//                }
//            }
        };
    }

    private void applyResponse(String pumpResp) {
        Supplier<Stream<String>> sup = () -> Arrays.stream(pumpResp.split("\n"));
        if (currentCommand != null && currentCommand.getFunction() != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, String.join(", ", pumpResp));
            currentCommand.getFunction().apply(sup);
        } else if (!this.resultActivity.isEmpty()) {
            Resp resp = this.resultActivity.get(0);
            aapsLogger.info(LTag.PUMPBTCOMM, resp.toString());
            resp.getFunc().apply(sup);
        }
        if (!this.resultActivity.isEmpty()) {
            this.resultActivity.remove(0);
        }
    }

    private void callback() {
//        medLinkUtil.sendAnswer(pumpResponse.toString());
    }

    private String buildResp() {
        return new StringBuffer(MedLinkConst.Intents.CommandCompleted).
                append("\n").append(pumpResponse).toString();
    }


    public BLECommOperationResult readCharacteristic_blocking(UUID serviceUUID, UUID charaUUID) {
        aapsLogger.info(LTag.PUMPBTCOMM, "readCharacteristic_blocking");
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            try {
                gattOperationSema.acquire();
                this.executing = true;
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: Interrupted waiting for gattOperationSema");
                return rval;
            }
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID).getCharacteristic(
                            charaUUID);
                    if (chara != null) {
                        mCurrentOperation = new CharacteristicReadOperation(aapsLogger, bluetoothConnectionGatt, chara);

                        mCurrentOperation.execute(this);
                        aapsLogger.debug(LTag.PUMPBTCOMM, "Bluetooth communication");
                        if (mCurrentOperation.getValue() != null) {
                            aapsLogger.debug(LTag.PUMPBTCOMM, new String(mCurrentOperation.getValue(), UTF_8));
                        } else {
                            aapsLogger.debug(LTag.PUMPBTCOMM, mCurrentOperation.toString());
                        }
                        if (mCurrentOperation.timedOut) {
                            rval.resultCode = RESULT_TIMEOUT;
                        } else if (mCurrentOperation.interrupted) {
                            rval.resultCode = RESULT_INTERRUPTED;
                        } else {
                            rval.resultCode = RESULT_SUCCESS;
                            rval.value = mCurrentOperation.getValue();
                        }
                    }
//                    rval.resultCode
                }
            }
            mCurrentOperation.gattOperationCompletionCallback(charaUUID, new byte[0]);
            mCurrentOperation = null;

            gattOperationSema.release();
            this.executing = false;
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }

//    public BLECommOperationResult writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID,  MedLinkPumpMessage msg) {
//        aapsLogger.debug(LTag.PUMPBTCOMM,"commands");
//        aapsLogger.debug(LTag.PUMPBTCOMM,msg.getCommandType().code);
//        BLECommOperationResult rval = new BLECommOperationResult();
//        if (bluetoothConnectionGatt != null) {
//            rval.value = msg.getCommandData();
//            aapsLogger.debug(LTag.PUMPBTCOMM,"command writen");
//            aapsLogger.debug(LTag.PUMPBTCOMM,msg.getCommandType().code);
//            try {
//                aapsLogger.debug(LTag.PUMPBTCOMM,"before acquire");
//                gattOperationSema.acquire();
//                aapsLogger.debug(LTag.PUMPBTCOMM,"after acquire");
//                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
//            } catch (InterruptedException e) {
//                aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: interrupted waiting for gattOperationSema");
//                return rval;
//            }
//
//            if (mCurrentOperation != null) {
//                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
//            } else {
//                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
//                    // Catch if the service is not supported by the BLE device
//                    // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
//                    // app that created the bluetoothConnectionGatt has been destroyed/created,
//                    // e.g. when the user switches from portrait to landscape.
//                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
//                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
//                    // TODO: 11/07/2016 UI update for user
//                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
//                } else {
//                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
//                            .getCharacteristic(charaUUID);
//                    mCurrentOperation = new CharacteristicWriteOperation(aapsLogger, bluetoothConnectionGatt, chara, msg.getCommandData());
//                    int operations =0;
//                    aapsLogger.info(LTag.PUMPBTCOMM,msg.getCommandType().code);
//                    aapsLogger.info(LTag.PUMPBTCOMM,"before is connected");
//                    while(!isConnected() && msg.getCommandType() != MedLinkCommandType.Connect && operations <20 ) {
//                        //TODO try to change to future implementation
//                        SystemClock.sleep(500);
//                    }
//                    aapsLogger.info(LTag.PUMPBTCOMM,msg.getCommandType().code);
//                    aapsLogger.info(LTag.PUMPBTCOMM,"before execution");
//                    mCurrentOperation.execute(this);
//                    if (mCurrentOperation.timedOut) {
//                        rval.resultCode = BLECommOperationResult.RESULT_TIMEOUT;
//                    } else if (mCurrentOperation.interrupted) {
//                        rval.resultCode = BLECommOperationResult.RESULT_INTERRUPTED;
//                    } else {
//                        rval.resultCode = BLECommOperationResult.RESULT_SUCCESS;
//                    }
//                }
//                mCurrentOperation = null;
//                gattOperationSema.release();
//            }
//        } else {
//            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!");
//            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
//        }
//        if(rval.value == null){
//            rval.value = getPumpResponse();
//        }
//
//        return rval;
//    }

    private void release() {
        gattOperationSema.release();
        this.executing = false;
    }

    private void acquire() {
        try {
            gattOperationSema.acquire();
            this.executing = true;
            SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
        } catch (InterruptedException e) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: interrupted waiting for gattOperationSema");
        }
    }

    public BLECommOperationResult writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID, byte[] command, Function func) {
        this.latestReceivedCommand = System.currentTimeMillis();
        aapsLogger.info(LTag.PUMPBTCOMM, "commands");
        aapsLogger.info(LTag.PUMPBTCOMM, new String(command));
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            rval.value = command;
            aapsLogger.debug(LTag.PUMPBTCOMM, "command writen");
            aapsLogger.debug(LTag.PUMPBTCOMM, new String(command, UTF_8));
            this.currentCommand = new RemainingCommand(serviceUUID, charaUUID, command, func);

            if (mCurrentOperation != null) {
                aapsLogger.info(LTag.PUMPBTCOMM, "bysy for the command " + command);
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
                    // app that created the bluetoothConnectionGatt has been destroyed/created,
                    // e.g. when the user switches from portrait to landscape.
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    mCurrentOperation = new CharacteristicWriteOperation(aapsLogger, bluetoothConnectionGatt, chara, command);
                    int operations = 0;
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + command);
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + MedLinkCommandType.Connect);
                    aapsLogger.info(LTag.PUMPBTCOMM, "before is connected");
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + isConnected());
//                    while (!isConnected() && !Arrays.equals(command, MedLinkCommandType.Connect.getRaw()) && operations < 20) {
//                        //TODO try to change to future implementation
//                        SystemClock.sleep(500);
//                        operations++;
//                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(command));
                    aapsLogger.info(LTag.PUMPBTCOMM, "before execution");
                    mCurrentOperation.execute(this);
                    if (mCurrentOperation.getValue() != null) {
                        aapsLogger.info(LTag.PUMPBTCOMM, new String(mCurrentOperation.getValue(), UTF_8));
                    }
                    if (mCurrentOperation.timedOut) {
                        rval.resultCode = RESULT_TIMEOUT;
                    } else if (mCurrentOperation.interrupted) {
                        rval.resultCode = RESULT_INTERRUPTED;
                    } else {
                        rval.resultCode = RESULT_SUCCESS;
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, "" + rval.resultCode);
                }

                if(mCurrentOperation != null) {
                    mCurrentOperation.gattOperationCompletionCallback(charaUUID, new byte[0]);
                    aapsLogger.info(LTag.PUMPBTCOMM,"nulling currentoperation");
                    mCurrentOperation = null;
                }

            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        if (rval.value == null) {
            rval.value = getPumpResponse();
        }

        return rval;
    }

    private synchronized BLECommOperationResult doWriteCharacteristicBlocking(UUID serviceUUID, UUID charaUUID, byte[] command, Function baseResultActivity) {
        aapsLogger.info(LTag.PUMPBTCOMM, "command do " + new String(command));
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            rval.value = command;
            this.currentCommand = new RemainingCommand(serviceUUID, charaUUID, command, baseResultActivity);
            aapsLogger.debug(LTag.PUMPBTCOMM, "command writen");
            aapsLogger.debug(LTag.PUMPBTCOMM, new String(command, UTF_8));
            try {
                if (command == MedLinkCommandType.Connect.getRaw()) {
                    gattOperationSema.acquire();
                    this.executing = true;
                    SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
                }
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: interrupted waiting for gattOperationSema");
                return rval;
            }

            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
                    // app that created the bluetoothConnectionGatt has been destroyed/created,
                    // e.g. when the user switches from portrait to landscape.
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    mCurrentOperation = new CharacteristicWriteOperation(aapsLogger, bluetoothConnectionGatt, chara, command);
                    int operations = 0;
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(command));
                    aapsLogger.info(LTag.PUMPBTCOMM, "before is connected");
//                    while(!isConnected() && command != MedLinkCommandType.Connect.getRaw() && operations <20 ) {
//                        //TODO try to change to future implementation
//                        SystemClock.sleep(500);
//                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, new String(command));
                    aapsLogger.info(LTag.PUMPBTCOMM, "before execution do");
                    mCurrentOperation.execute(this);
                    if (mCurrentOperation.timedOut) {
                        rval.resultCode = RESULT_TIMEOUT;
                    } else if (mCurrentOperation.interrupted) {
                        rval.resultCode = RESULT_INTERRUPTED;
                    } else {
                        rval.resultCode = RESULT_SUCCESS;
                    }
                }
                if (remainingCommands.isEmpty()) {
                    mCurrentOperation.gattOperationCompletionCallback(charaUUID, new byte[0]);
                    aapsLogger.info(LTag.PUMPBTCOMM,"nulling currentoperation");
                    mCurrentOperation = null;
                    aapsLogger.info(LTag.PUMPBTCOMM, "before release");
//                    gattOperationSema.release();
//                    this.executing = false;
                }
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        if (rval.value == null) {
            rval.value = getPumpResponse();
        }

        return rval;
    }


    public synchronized void writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID, MedLinkPumpMessage msg) {
        updateActivity(msg.getBaseCallBack(), msg.getCommandType().code);
        aapsLogger.info(LTag.PUMPBTCOMM, "writeCharblocking");
        aapsLogger.info(LTag.PUMPBTCOMM, msg.getCommandType().code);
        if (this.isConnected && remainingCommands.isEmpty() && this.currentCommand == null && gattConnected) {
            if (msg.getArgument() == MedLinkCommandType.BaseProfile) {
                this.addCommand(serviceUUID, charaUUID, msg.getArgumentData(), ((BasalMedLinkMessage<Profile>) msg).getArgCallBack());
            } else if (msg.getArgument() != MedLinkCommandType.NoCommand) {
                this.addCommand(serviceUUID, charaUUID, msg.getArgumentData(), msg.getBaseCallBack());
            }
            doWriteCharacteristicBlocking(serviceUUID, charaUUID, msg.getCommandData(), msg.getBaseCallBack());

        } else {

            if (addedTimeout() && answerTimeout()) {
                answerTimeoutCommands();
            }
            if (!gattConnected) {
                connectGatt();
            }

            this.addCommand(serviceUUID, charaUUID, msg.getCommandData(), msg.getBaseCallBack());
            if (msg.getArgument() == MedLinkCommandType.BaseProfile) {
                this.addCommand(serviceUUID, charaUUID, msg.getArgumentData(), ((BasalMedLinkMessage<Profile>) msg).getArgCallBack());
            } else if (msg.getArgument() != MedLinkCommandType.NoCommand) {
                this.addCommand(serviceUUID, charaUUID, msg.getArgumentData(), msg.getBaseCallBack());
            }
        }
        this.latestReceivedCommand = System.currentTimeMillis();
    }

    private synchronized void answerTimeoutCommands() {
        this.resultActivity.forEach(f -> {
            Supplier<Stream<String>> supplier = () -> Stream.of("TimeoutError");
            f.getFunc().apply(supplier);
        });
        this.resultActivity.clear();
        this.remainingCommands.clear();

    }

    private boolean addedTimeout() {
        long delta = System.currentTimeMillis() - this.latestReceivedCommand;
        return this.latestReceivedCommand > 0 && delta > 50000;
    }

    private boolean answerTimeout() {
        long delta = System.currentTimeMillis() - this.latestReceivedAnswer;
        return this.latestReceivedAnswer > 0 && delta > 10000;
    }

    private boolean isFirstRemaningConnect() {
        return !remainingCommands.isEmpty() && remainingCommands.get(0).getCommand() == MedLinkCommandType.Connect.getRaw();
    }

    private void updateActivity(Function resultActivity, String command) {
        if (this.resultActivity != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "resultActivity added " + this.resultActivity.toString());
        }
        this.resultActivity.add(new Resp(resultActivity, command));

    }


    private BLECommOperationResult processRemainingCommand() {
        int index = 0;
        aapsLogger.info(LTag.PUMPBTCOMM, "is this connected" + this.isConnected);
        if (!this.isConnected) {
            medLinkConnect();
        }
        while (!this.isConnected) {
            SystemClock.sleep(500);
            index++;
            if (index > 20) {
                BLECommOperationResult ret = new BLECommOperationResult();
                ret.resultCode = RESULT_TIMEOUT;
                return ret;
            } else if (index == 7) {
                medLinkConnect();
            }
        }
        if (remainingCommands.isEmpty()) {
            BLECommOperationResult ret = new BLECommOperationResult();
            ret.resultCode = RESULT_SUCCESS;
            close();
            return ret;
        } else {
            RemainingCommand remain = remainingCommands.get(0);
            if (!remainingCommands.isEmpty()) {
                remainingCommands.remove(0);
            }
            this.currentCommand = remain;
            aapsLogger.info(LTag.PUMPBTCOMM, "remaining command " + new String(remain.command));
            return writeCharacteristic_blocking(remain.serviceUUID, remain.charaUUID, remain.command, remain.getFunction());
        }
    }

    private void medLinkConnect() {
        if (bluetoothConnectionGatt == null) {
            connectGatt();
        }
        acquire();
        pumpResponse = new StringBuffer();
        writeCharacteristic_blocking(UUID.fromString(GattAttributes.SERVICE_UUID),
                UUID.fromString(GattAttributes.GATT_UUID), MedLinkCommandType.Connect.getRaw(),
                s -> {
                    return s;
                });
    }


    private static class RemainingCommand {
        private final byte[] command;
        private final UUID charaUUID;

        private final UUID serviceUUID;
        private final Function function;

        private RemainingCommand(UUID serviceUUID, UUID charaUUID, byte[] command, Function func) {
            this.serviceUUID = serviceUUID;
            this.charaUUID = charaUUID;
            this.command = command;
            this.function = func;
        }

        public Function<Object, MedLinkStandardReturn> getFunction() {
            return function;
        }

        public byte[] getCommand() {
            return command;
        }

        public UUID getCharaUUID() {
            return charaUUID;
        }

        public UUID getServiceUUID() {
            return serviceUUID;
        }
    }

    private void addCommand(UUID serviceUUID, UUID charaUUID, byte[] command, Function baseResultActivity) {
        this.remainingCommands.add(new RemainingCommand(serviceUUID, charaUUID, command, baseResultActivity));
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
        aapsLogger.debug("Enable rileyLink notification");
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {

            try {
                gattOperationSema.acquire();
                this.executing = true;
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "setNotification_blocking: interrupted waiting for gattOperationSema");
                return rval;
            }
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    // Tell Android that we want the notifications
                    bluetoothConnectionGatt.setCharacteristicNotification(chara, true);
                    List<BluetoothGattDescriptor> list = chara.getDescriptors();
                    if (gattDebugEnabled) {
                        for (int i = 0; i < list.size(); i++) {
                            aapsLogger.debug(LTag.PUMPBTCOMM, "Found descriptor: " + list.get(i).toString());
                        }
                    }
                    BluetoothGattDescriptor descr = list.get(0);
                    // Tell the remote device to send the notifications
                    mCurrentOperation = new DescriptorWriteOperation(aapsLogger, bluetoothConnectionGatt, descr,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mCurrentOperation.execute(this);
                    if (mCurrentOperation.timedOut) {
                        rval.resultCode = BLECommOperationResult.RESULT_TIMEOUT;
                    } else if (mCurrentOperation.interrupted) {
                        rval.resultCode = BLECommOperationResult.RESULT_INTERRUPTED;
                    } else {
                        rval.resultCode = BLECommOperationResult.RESULT_SUCCESS;
                    }
                }
                aapsLogger.info(LTag.PUMPBTCOMM,"nulling currentoperation");
                mCurrentOperation = null;
                gattOperationSema.release();
                this.executing = false;
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

    public String getPumpModel() {
        return pumpModel;
    }

    public void setPumpModel(String pumpModel) {
        this.pumpModel = pumpModel;
//        medLinkUtil;
    }

    public void findMedLink(String RileyLinkAddress) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address: " + RileyLinkAddress);
        // Must verify that this is a valid MAC, or crash.

        rileyLinkDevice = bluetoothAdapter.getRemoteDevice(RileyLinkAddress);
        // if this succeeds, we get a connection state change callback?

        if (rileyLinkDevice != null) {
//            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkReady, context);
            connectGatt();
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device not found with address: " + RileyLinkAddress);
        }
    }


    public void disconnect() {
        super.disconnect();
        setConnected(false);
    }

    public void close() {
        disconnect();
        super.close();
        if (remainingCommands.isEmpty()) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Remaining commands");
            aapsLogger.info(LTag.PUMPBTCOMM, this.remainingCommands.stream().map(f -> new String(f.command, UTF_8)).collect(Collectors.joining()));
            this.resultActivity.clear();
            this.remainingCommands.clear();
            this.pumpResponse = new StringBuffer();
            this.currentCommand = null;
            latestReceivedAnswer = 0l;
            latestReceivedCommand = 0l;
            release();
//        mCurrentOperation = null;
//        gattOperationSema.release();
        } else {
            aapsLogger.info(LTag.PUMPBTCOMM, remainingCommands.stream().map(f ->
                    new String(f.command)).collect(Collectors.joining()));
            SystemClock.sleep(5000l);
            processRemainingCommand();
        }
    }

}