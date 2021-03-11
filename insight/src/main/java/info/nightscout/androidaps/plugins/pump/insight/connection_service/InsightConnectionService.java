package info.nightscout.androidaps.plugins.pump.insight.connection_service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import org.spongycastle.crypto.InvalidCipherTextException;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.android.DaggerService;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.ReadParameterBlockMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ActivateServiceMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.BindMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ConnectMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.DisconnectMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ServiceChallengeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.SystemIdentificationBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetFirmwareVersionsMessage;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SystemIdentification;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.ConnectionFailedException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.ConnectionLostException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.DisconnectedException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InsightException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidNonceException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidSatlCommandException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.ReceivedPacketInInvalidStateException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.TimeoutException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.TooChattyPumpException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlCompatibleStateErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlDecryptVerifyFailedErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlIncompatibleVersionErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidCRCErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidCommIdErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidMacErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidMessageTypeErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidNonceErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidPacketErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlInvalidPayloadLengthErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlNoneErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlPairingRejectedException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlUndefinedErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlWrongStateException;
import info.nightscout.androidaps.plugins.pump.insight.ids.ServiceIDs;
import info.nightscout.androidaps.plugins.pump.insight.satl.ConnectionRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.ConnectionResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.DataMessage;
import info.nightscout.androidaps.plugins.pump.insight.satl.ErrorMessage;
import info.nightscout.androidaps.plugins.pump.insight.satl.KeyRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.KeyResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.SatlMessage;
import info.nightscout.androidaps.plugins.pump.insight.satl.SynAckResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.SynRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyConfirmRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyConfirmResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyDisplayRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyDisplayResponse;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;
import info.nightscout.androidaps.plugins.pump.insight.utils.ConnectionEstablisher;
import info.nightscout.androidaps.plugins.pump.insight.utils.DelayedActionThread;
import info.nightscout.androidaps.plugins.pump.insight.utils.InputStreamReader;
import info.nightscout.androidaps.plugins.pump.insight.utils.Nonce;
import info.nightscout.androidaps.plugins.pump.insight.utils.OutputStreamWriter;
import info.nightscout.androidaps.plugins.pump.insight.utils.PairingDataStorage;
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.Cryptograph;
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.DerivedKeys;
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.KeyPair;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class InsightConnectionService extends DaggerService implements ConnectionEstablisher.Callback, InputStreamReader.Callback, OutputStreamWriter.Callback {

    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;

    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_DURING_HANDSHAKE_NOTIFICATION_THRESHOLD = 3;
    private static final long RESPONSE_TIMEOUT = 6000;

    private final List<StateCallback> stateCallbacks = new ArrayList<>();
    private final List<Object> connectionRequests = new ArrayList<>();
    private final List<ExceptionCallback> exceptionCallbacks = new ArrayList<>();
    private final LocalBinder localBinder = new LocalBinder();
    private PairingDataStorage pairingDataStorage;
    private InsightState state;
    private PowerManager.WakeLock wakeLock;
    private DelayedActionThread disconnectTimer;
    private DelayedActionThread recoveryTimer;
    private DelayedActionThread timeoutTimer;
    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private ConnectionEstablisher connectionEstablisher;
    private InputStreamReader inputStreamReader;
    private OutputStreamWriter outputStreamWriter;
    private KeyRequest keyRequest;
    private final ByteBuf buffer = new ByteBuf(BUFFER_SIZE);
    private String verificationString;
    private KeyPair keyPair;
    private byte[] randomBytes;
    private final MessageQueue messageQueue = new MessageQueue();
    private final List<info.nightscout.androidaps.plugins.pump.insight.app_layer.Service> activatedServices = new ArrayList<>();
    private long lastDataTime;
    private long lastConnected;
    private long recoveryDuration = 0;
    private int timeoutDuringHandshakeCounter;

    KeyPair getKeyPair() {
        if (keyPair == null) keyPair = Cryptograph.generateRSAKey();
        return keyPair;
    }

    byte[] getRandomBytes() {
        if (randomBytes == null) {
            randomBytes = new byte[28];
            new SecureRandom().nextBytes(randomBytes);
        }
        return randomBytes;
    }

    public synchronized long getRecoveryDuration() {
        return recoveryDuration;
    }

    private void increaseRecoveryDuration() {
        long maxRecoveryDuration = sp.getInt("insight_max_recovery_duration", 20);
        maxRecoveryDuration = Math.min(maxRecoveryDuration, 20);
        maxRecoveryDuration = Math.max(maxRecoveryDuration, 0);
        long minRecoveryDuration = sp.getInt("insight_min_recovery_duration", 5);
        minRecoveryDuration = Math.min(minRecoveryDuration, 20);
        minRecoveryDuration = Math.max(minRecoveryDuration, 0);
        recoveryDuration += 1000;
        recoveryDuration = Math.max(recoveryDuration, minRecoveryDuration * 1000);
        recoveryDuration = Math.min(recoveryDuration, maxRecoveryDuration * 1000);
    }

    public long getLastConnected() {
        return lastConnected;
    }

    public long getLastDataTime() {
        return lastDataTime;
    }

    public FirmwareVersions getPumpFirmwareVersions() {
        return pairingDataStorage.getFirmwareVersions();
    }

    public SystemIdentification getPumpSystemIdentification() {
        return pairingDataStorage.getSystemIdentification();
    }

    public String getBluetoothAddress() {
        return pairingDataStorage.getMacAddress();
    }

    public synchronized String getVerificationString() {
        return verificationString;
    }

    public synchronized void registerStateCallback(StateCallback stateCallback) {
        stateCallbacks.add(stateCallback);
    }

    public synchronized void unregisterStateCallback(StateCallback stateCallback) {
        stateCallbacks.remove(stateCallback);
    }

    public synchronized void registerExceptionCallback(ExceptionCallback exceptionCallback) {
        exceptionCallbacks.add(exceptionCallback);
    }

    public synchronized void unregisterExceptionCallback(ExceptionCallback exceptionCallback) {
        exceptionCallbacks.remove(exceptionCallback);
    }

    public synchronized void confirmVerificationString() {
        setState(InsightState.SATL_VERIFY_CONFIRM_REQUEST);
        sendSatlMessage(new VerifyConfirmRequest());
    }

    public synchronized void rejectVerificationString() {
        handleException(new SatlPairingRejectedException());
    }

    public synchronized boolean isPaired() {
        return pairingDataStorage.isPaired();
    }

    public synchronized <T extends AppLayerMessage> MessageRequest<T> requestMessage(T message) {
        MessageRequest<T> messageRequest;
        if (getState() != InsightState.CONNECTED) {
            messageRequest = new MessageRequest<>(message);
            messageRequest.exception = new DisconnectedException();
            return messageRequest;
        }
        if (message instanceof WriteConfigurationBlockMessage) {
            MessageRequest<OpenConfigurationWriteSessionMessage> openRequest = new MessageRequest<>(new OpenConfigurationWriteSessionMessage());
            MessageRequest<CloseConfigurationWriteSessionMessage> closeRequest = new MessageRequest<>(new CloseConfigurationWriteSessionMessage());
            messageRequest = new ConfigurationMessageRequest<>(message, openRequest, closeRequest);
            messageQueue.enqueueRequest(openRequest);
            messageQueue.enqueueRequest(messageRequest);
            messageQueue.enqueueRequest(closeRequest);
        } else {
            messageRequest = new MessageRequest<>(message);
            messageQueue.enqueueRequest(messageRequest);
        }
        requestNextMessage();
        return messageRequest;
    }

    private void requestNextMessage() {
        while (messageQueue.getActiveRequest() == null && messageQueue.hasPendingMessages()) {
            messageQueue.nextRequest();
            info.nightscout.androidaps.plugins.pump.insight.app_layer.Service service = messageQueue.getActiveRequest().request.getService();
            if (service != info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.CONNECTION && !activatedServices.contains(service)) {
                if (service.getServicePassword() == null) {
                    ActivateServiceMessage activateServiceMessage = new ActivateServiceMessage();
                    activateServiceMessage.setServiceID(ServiceIDs.IDS.getID(service));
                    activateServiceMessage.setVersion(service.getVersion());
                    activateServiceMessage.setServicePassword(new byte[16]);
                    sendAppLayerMessage(activateServiceMessage);
                } else {
                    ServiceChallengeMessage serviceChallengeMessage = new ServiceChallengeMessage();
                    serviceChallengeMessage.setServiceID(ServiceIDs.IDS.getID(service));
                    serviceChallengeMessage.setVersion(service.getVersion());
                    sendAppLayerMessage(serviceChallengeMessage);
                }
            } else sendAppLayerMessage(messageQueue.getActiveRequest().request);
        }
    }

    public synchronized InsightState getState() {
        return state;
    }

    @Override
    public synchronized void onCreate() {
        super.onCreate();
        pairingDataStorage = new PairingDataStorage(this);
        state = pairingDataStorage.isPaired() ? InsightState.DISCONNECTED : InsightState.NOT_PAIRED;
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:InsightConnectionService");
    }

    private void setState(InsightState state) {
        if (this.state == state) return;
        if (this.state == InsightState.CONNECTED) lastConnected = System.currentTimeMillis();
        if ((state == InsightState.DISCONNECTED || state == InsightState.NOT_PAIRED) && wakeLock.isHeld())
            wakeLock.release();
        else if (!wakeLock.isHeld()) wakeLock.acquire();
        this.state = state;
        for (StateCallback stateCallback : stateCallbacks) stateCallback.onStateChanged(state);
        aapsLogger.info(LTag.PUMP, "Insight state changed: " + state.name());
    }

    public synchronized void requestConnection(Object lock) {
        if (connectionRequests.contains(lock)) return;
        connectionRequests.add(lock);
        if (disconnectTimer != null) {
            disconnectTimer.interrupt();
            disconnectTimer = null;
        }
        if (state == InsightState.DISCONNECTED && pairingDataStorage.isPaired()) {
            recoveryDuration = 0;
            timeoutDuringHandshakeCounter = 0;
            connect();
        }
    }

    public synchronized void withdrawConnectionRequest(Object lock) {
        if (!connectionRequests.contains(lock)) return;
        connectionRequests.remove(lock);
        if (connectionRequests.size() == 0) {
            if (state == InsightState.RECOVERING) {
                recoveryTimer.interrupt();
                recoveryTimer = null;
                setState(InsightState.DISCONNECTED);
                cleanup(true);
            } else if (state != InsightState.DISCONNECTED) {
                long disconnectTimeout = sp.getInt("insight_disconnect_delay", 5);
                disconnectTimeout = Math.min(disconnectTimeout, 15);
                disconnectTimeout = Math.max(disconnectTimeout, 0);
                aapsLogger.info(LTag.PUMP, "Last connection lock released, will disconnect in " + disconnectTimeout + " seconds");
                disconnectTimer = DelayedActionThread.runDelayed("Disconnect Timer", disconnectTimeout * 1000, this::disconnect);
            }
        }
    }

    public synchronized boolean hasRequestedConnection(Object lock) {
        return connectionRequests.contains(lock);
    }

    private void cleanup(boolean closeSocket) {
        messageQueue.completeActiveRequest(new ConnectionLostException());
        messageQueue.completePendingRequests(new ConnectionLostException());
        if (recoveryTimer != null) {
            recoveryTimer.interrupt();
            recoveryTimer = null;
        }
        if (disconnectTimer != null) {
            disconnectTimer.interrupt();
            disconnectTimer = null;
        }
        if (inputStreamReader != null) {
            inputStreamReader.close();
            inputStreamReader = null;
        }
        if (outputStreamWriter != null) {
            outputStreamWriter.close();
            outputStreamWriter = null;
        }
        if (connectionEstablisher != null) {
            if (closeSocket) {
                connectionEstablisher.close(closeSocket);
                bluetoothSocket = null;
            }
            connectionEstablisher = null;
        }
        if (timeoutTimer != null) {
            timeoutTimer.interrupt();
            timeoutTimer = null;
        }
        buffer.clear();
        verificationString = null;
        keyPair = null;
        randomBytes = null;
        activatedServices.clear();
        if (!pairingDataStorage.isPaired()) {
            bluetoothSocket = null;
            bluetoothDevice = null;
            pairingDataStorage.reset();
        }
    }

    private synchronized void handleException(Exception e) {
        switch (state) {
            case NOT_PAIRED:
            case DISCONNECTED:
            case RECOVERING:
                return;
        }
        aapsLogger.info(LTag.PUMP, "Exception occurred: " + e.getClass().getSimpleName());
        if (pairingDataStorage.isPaired()) {
            if (e instanceof TimeoutException && (state == InsightState.SATL_SYN_REQUEST || state == InsightState.APP_CONNECT_MESSAGE)) {
                if (++timeoutDuringHandshakeCounter == TIMEOUT_DURING_HANDSHAKE_NOTIFICATION_THRESHOLD) {
                    for (StateCallback stateCallback : stateCallbacks) {
                        stateCallback.onTimeoutDuringHandshake();
                    }
                }
            }
            setState(connectionRequests.size() != 0 ? InsightState.RECOVERING : InsightState.DISCONNECTED);
            if (e instanceof ConnectionFailedException) {
                cleanup(((ConnectionFailedException) e).getDurationOfConnectionAttempt() <= 1000);
            } else cleanup(true);
            messageQueue.completeActiveRequest(e);
            messageQueue.completePendingRequests(e);
            if (connectionRequests.size() != 0) {
                if (!(e instanceof ConnectionFailedException)) {
                    connect();
                } else {
                    increaseRecoveryDuration();
                    if (recoveryDuration == 0) connect();
                    else {
                        recoveryTimer = DelayedActionThread.runDelayed("RecoveryTimer", recoveryDuration, () -> {
                            recoveryTimer = null;
                            synchronized (InsightConnectionService.this) {
                                if (!Thread.currentThread().isInterrupted()) connect();
                            }
                        });
                    }
                }
            }
        } else {
            setState(InsightState.NOT_PAIRED);
            cleanup(true);
        }
        for (ExceptionCallback exceptionCallback : exceptionCallbacks)
            exceptionCallback.onExceptionOccur(e);
    }

    private synchronized void disconnect() {
        if (state == InsightState.CONNECTED) {
            sendAppLayerMessage(new DisconnectMessage());
            sendSatlMessageAndWait(new info.nightscout.androidaps.plugins.pump.insight.satl.DisconnectMessage());
        }
        cleanup(true);
        setState(pairingDataStorage.isPaired() ? InsightState.DISCONNECTED : InsightState.NOT_PAIRED);
    }

    public synchronized void reset() {
        pairingDataStorage.reset();
        disconnect();
    }

    public synchronized void pair(String macAddress) {
        if (pairingDataStorage.isPaired())
            throw new IllegalStateException("Pump must be unbonded first.");
        if (connectionRequests.size() == 0)
            throw new IllegalStateException("A connection lock must be hold for pairing");
        aapsLogger.info(LTag.PUMP, "Pairing initiated");
        cleanup(true);
        pairingDataStorage.setMacAddress(macAddress);
        connect();
    }

    private synchronized void connect() {
        if (bluetoothDevice == null)
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(pairingDataStorage.getMacAddress());
        setState(InsightState.CONNECTING);
        connectionEstablisher = new ConnectionEstablisher(this, !pairingDataStorage.isPaired(), bluetoothAdapter, bluetoothDevice, bluetoothSocket);
        connectionEstablisher.start();
    }

    @Override
    public synchronized void onSocketCreated(BluetoothSocket bluetoothSocket) {
        this.bluetoothSocket = bluetoothSocket;
    }

    @Override
    public synchronized void onConnectionSucceed() {
        try {
            recoveryDuration = 0;
            inputStreamReader = new InputStreamReader(bluetoothSocket.getInputStream(), this);
            outputStreamWriter = new OutputStreamWriter(bluetoothSocket.getOutputStream(), this);
            inputStreamReader.start();
            outputStreamWriter.start();
            if (pairingDataStorage.isPaired()) {
                setState(InsightState.SATL_SYN_REQUEST);
                sendSatlMessage(new SynRequest());
            } else {
                setState(InsightState.SATL_CONNECTION_REQUEST);
                sendSatlMessage(new ConnectionRequest());
            }
        } catch (IOException e) {
            handleException(e);
        }
    }

    @Override
    public synchronized void onReceiveBytes(byte[] buffer, int bytesRead) {
        this.buffer.putBytes(buffer, bytesRead);
        try {
            while (SatlMessage.hasCompletePacket(this.buffer)) {
                SatlMessage satlMessage = SatlMessage.deserialize(this.buffer, pairingDataStorage.getLastNonceReceived(), pairingDataStorage.getIncomingKey());
                if (pairingDataStorage.getIncomingKey() != null
                        && pairingDataStorage.getLastNonceReceived() != null
                        && !pairingDataStorage.getLastNonceReceived().isSmallerThan(satlMessage.getNonce())) {
                    throw new InvalidNonceException();
                } else processSatlMessage(satlMessage);
            }
        } catch (InsightException e) {
            handleException(e);
        }
    }

    private byte[] prepareSatlMessage(SatlMessage satlMessage) {
        satlMessage.setCommID(pairingDataStorage.getCommId());
        Nonce nonce = pairingDataStorage.getLastNonceSent();
        if (nonce != null) {
            nonce.increment();
            pairingDataStorage.setLastNonceSent(nonce);
            satlMessage.setNonce(nonce);
        }
        ByteBuf serialized = satlMessage.serialize(satlMessage.getClass(), pairingDataStorage.getOutgoingKey());
        if (timeoutTimer != null) timeoutTimer.interrupt();
        timeoutTimer = DelayedActionThread.runDelayed("TimeoutTimer", RESPONSE_TIMEOUT, () -> {
            timeoutTimer = null;
            handleException(new TimeoutException());
        });
        return serialized.getBytes();
    }

    private void sendSatlMessage(SatlMessage satlMessage) {
        if (outputStreamWriter == null) return;
        outputStreamWriter.write(prepareSatlMessage(satlMessage));
    }

    private void sendSatlMessageAndWait(SatlMessage satlMessage) {
        if (outputStreamWriter == null) return;
        outputStreamWriter.writeAndWait(prepareSatlMessage(satlMessage));
    }

    private void processSatlMessage(SatlMessage satlMessage) {
        if (timeoutTimer != null) {
            timeoutTimer.interrupt();
            timeoutTimer = null;
        }
        pairingDataStorage.setLastNonceReceived(satlMessage.getNonce());
        if (satlMessage instanceof ConnectionResponse) processConnectionResponse();
        else if (satlMessage instanceof KeyResponse) processKeyResponse((KeyResponse) satlMessage);
        else if (satlMessage instanceof VerifyDisplayResponse) processVerifyDisplayResponse();
        else if (satlMessage instanceof VerifyConfirmResponse)
            processVerifyConfirmResponse((VerifyConfirmResponse) satlMessage);
        else if (satlMessage instanceof DataMessage) processDataMessage((DataMessage) satlMessage);
        else if (satlMessage instanceof SynAckResponse) processSynAckResponse();
        else if (satlMessage instanceof ErrorMessage)
            processErrorMessage((ErrorMessage) satlMessage);
        else handleException(new InvalidSatlCommandException());
    }

    private void processConnectionResponse() {
        if (state != InsightState.SATL_CONNECTION_REQUEST) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        keyRequest = new KeyRequest();
        keyRequest.setPreMasterKey(getKeyPair().getPublicKeyBytes());
        keyRequest.setRandomBytes(getRandomBytes());
        setState(InsightState.SATL_KEY_REQUEST);
        sendSatlMessage(keyRequest);
    }

    private void processKeyResponse(KeyResponse keyResponse) {
        if (state != InsightState.SATL_KEY_REQUEST) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        try {
            DerivedKeys derivedKeys = Cryptograph.deriveKeys(Cryptograph.combine(keyRequest.getSatlContent(), keyResponse.getSatlContent()),
                    Cryptograph.decryptRSA(getKeyPair().getPrivateKey(), keyResponse.getPreMasterSecret()),
                    getRandomBytes(),
                    keyResponse.getRandomData());
            pairingDataStorage.setCommId(keyResponse.getCommID());
            keyRequest = null;
            randomBytes = null;
            keyPair = null;
            verificationString = derivedKeys.getVerificationString();
            pairingDataStorage.setOutgoingKey(derivedKeys.getOutgoingKey());
            pairingDataStorage.setIncomingKey(derivedKeys.getIncomingKey());
            pairingDataStorage.setLastNonceSent(new Nonce());
            setState(InsightState.SATL_VERIFY_DISPLAY_REQUEST);
            sendSatlMessage(new VerifyDisplayRequest());
        } catch (InvalidCipherTextException e) {
            handleException(e);
        }
    }

    private void processVerifyDisplayResponse() {
        if (state != InsightState.SATL_VERIFY_DISPLAY_REQUEST) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        setState(InsightState.AWAITING_CODE_CONFIRMATION);
    }

    private void processVerifyConfirmResponse(VerifyConfirmResponse verifyConfirmResponse) {
        if (state != InsightState.SATL_VERIFY_CONFIRM_REQUEST) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        switch (verifyConfirmResponse.getPairingStatus()) {
            case CONFIRMED:
                verificationString = null;
                setState(InsightState.APP_BIND_MESSAGE);
                sendAppLayerMessage(new BindMessage());
                break;
            case PENDING:
                try {
                    Thread.sleep(200);
                    sendSatlMessage(new VerifyConfirmRequest());
                } catch (InterruptedException e) {
                    //Redirect interrupt flag
                    Thread.currentThread().interrupt();
                }
                break;
            case REJECTED:
                handleException(new SatlPairingRejectedException());
                break;
        }
    }

    private void processSynAckResponse() {
        if (state != InsightState.SATL_SYN_REQUEST) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        setState(InsightState.APP_CONNECT_MESSAGE);
        sendAppLayerMessage(new ConnectMessage());
    }

    private void processErrorMessage(ErrorMessage errorMessage) {
        switch (errorMessage.getError()) {
            case INVALID_NONCE:
                handleException(new SatlInvalidNonceErrorException());
                break;
            case INVALID_CRC:
                handleException(new SatlInvalidCRCErrorException());
                break;
            case INVALID_MAC_TRAILER:
                handleException(new SatlInvalidMacErrorException());
                break;
            case DECRYPT_VERIFY_FAILED:
                handleException(new SatlDecryptVerifyFailedErrorException());
                break;
            case INVALID_PAYLOAD_LENGTH:
                handleException(new SatlInvalidPayloadLengthErrorException());
                break;
            case INVALID_MESSAGE_TYPE:
                handleException(new SatlInvalidMessageTypeErrorException());
                break;
            case INCOMPATIBLE_VERSION:
                handleException(new SatlIncompatibleVersionErrorException());
                break;
            case COMPATIBLE_STATE:
                handleException(new SatlCompatibleStateErrorException());
                break;
            case INVALID_COMM_ID:
                handleException(new SatlInvalidCommIdErrorException());
                break;
            case INVALID_PACKET:
                handleException(new SatlInvalidPacketErrorException());
                break;
            case WRONG_STATE:
                handleException(new SatlWrongStateException());
                break;
            case UNDEFINED:
                handleException(new SatlUndefinedErrorException());
                break;
            case NONE:
                handleException(new SatlNoneErrorException());
                break;
        }
    }

    private void processDataMessage(DataMessage dataMessage) {
        switch (state) {
            case CONNECTED:
            case APP_BIND_MESSAGE:
            case APP_CONNECT_MESSAGE:
            case APP_ACTIVATE_PARAMETER_SERVICE:
            case APP_ACTIVATE_STATUS_SERVICE:
            case APP_FIRMWARE_VERSIONS:
            case APP_SYSTEM_IDENTIFICATION:
                break;
            default:
                handleException(new ReceivedPacketInInvalidStateException());
        }
        try {
            AppLayerMessage appLayerMessage = AppLayerMessage.unwrap(dataMessage);
            if (appLayerMessage instanceof BindMessage) processBindMessage();
            else if (appLayerMessage instanceof ConnectMessage) processConnectMessage();
            else if (appLayerMessage instanceof ActivateServiceMessage)
                processActivateServiceMessage();
            else if (appLayerMessage instanceof DisconnectMessage) ;
            else if (appLayerMessage instanceof ServiceChallengeMessage)
                processServiceChallengeMessage((ServiceChallengeMessage) appLayerMessage);
            else if (appLayerMessage instanceof GetFirmwareVersionsMessage)
                processFirmwareVersionsMessage((GetFirmwareVersionsMessage) appLayerMessage);
            else if (appLayerMessage instanceof ReadParameterBlockMessage)
                processReadParameterBlockMessage((ReadParameterBlockMessage) appLayerMessage);
            else processGenericAppLayerMessage(appLayerMessage);
        } catch (Exception e) {
            if (state != InsightState.CONNECTED) {
                handleException(e);
            } else {
                if (messageQueue.getActiveRequest() == null) {
                    handleException(new TooChattyPumpException());
                } else {
                    messageQueue.completeActiveRequest(e);
                    requestNextMessage();
                }
            }
        }
    }

    private void processBindMessage() {
        if (state != InsightState.APP_BIND_MESSAGE) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        setState(InsightState.APP_ACTIVATE_STATUS_SERVICE);
        ActivateServiceMessage activateServiceMessage = new ActivateServiceMessage();
        activateServiceMessage.setServiceID(ServiceIDs.IDS.getID(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.STATUS));
        activateServiceMessage.setServicePassword(new byte[16]);
        activateServiceMessage.setVersion(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.STATUS.getVersion());
        sendAppLayerMessage(activateServiceMessage);
    }

    private void processFirmwareVersionsMessage(GetFirmwareVersionsMessage message) {
        if (state != InsightState.APP_FIRMWARE_VERSIONS) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        pairingDataStorage.setFirmwareVersions(message.getFirmwareVersions());
        setState(InsightState.APP_ACTIVATE_PARAMETER_SERVICE);
        ActivateServiceMessage activateServiceMessage = new ActivateServiceMessage();
        activateServiceMessage.setServiceID(ServiceIDs.IDS.getID(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.PARAMETER));
        activateServiceMessage.setServicePassword(new byte[16]);
        activateServiceMessage.setVersion(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.PARAMETER.getVersion());
        sendAppLayerMessage(activateServiceMessage);
    }

    private void processConnectMessage() {
        if (state != InsightState.APP_CONNECT_MESSAGE) {
            handleException(new ReceivedPacketInInvalidStateException());
            return;
        }
        setState(InsightState.CONNECTED);
    }

    private void processActivateServiceMessage() {
        if (state == InsightState.APP_ACTIVATE_PARAMETER_SERVICE) {
            activatedServices.add(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.PARAMETER);
            setState(InsightState.APP_SYSTEM_IDENTIFICATION);
            ReadParameterBlockMessage message = new ReadParameterBlockMessage();
            message.setParameterBlockId(SystemIdentificationBlock.class);
            message.setService(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.PARAMETER);
            sendAppLayerMessage(message);
        } else if (state == InsightState.APP_ACTIVATE_STATUS_SERVICE) {
            activatedServices.add(info.nightscout.androidaps.plugins.pump.insight.app_layer.Service.STATUS);
            setState(InsightState.APP_FIRMWARE_VERSIONS);
            sendAppLayerMessage(new GetFirmwareVersionsMessage());
        } else {
            if (messageQueue.getActiveRequest() == null) {
                handleException(new TooChattyPumpException());
            } else {
                activatedServices.add(messageQueue.getActiveRequest().request.getService());
                sendAppLayerMessage(messageQueue.getActiveRequest().request);
            }
        }
    }

    private void processReadParameterBlockMessage(ReadParameterBlockMessage message) {
        if (state == InsightState.APP_SYSTEM_IDENTIFICATION) {
            if (!(message.getParameterBlock() instanceof SystemIdentificationBlock))
                handleException(new TooChattyPumpException());
            else {
                SystemIdentification systemIdentification = ((SystemIdentificationBlock) message.getParameterBlock()).getSystemIdentification();
                pairingDataStorage.setSystemIdentification(systemIdentification);
                pairingDataStorage.setPaired(true);
                aapsLogger.info(LTag.PUMP, "Pairing completed YEE-HAW ♪ ┏(・o･)┛ ♪ ┗( ･o･)┓ ♪");
                setState(InsightState.CONNECTED);
                for (StateCallback stateCallback : stateCallbacks) stateCallback.onPumpPaired();
            }
        } else processGenericAppLayerMessage(message);
    }

    private void processServiceChallengeMessage(ServiceChallengeMessage serviceChallengeMessage) {
        if (messageQueue.getActiveRequest() == null) {
            handleException(new TooChattyPumpException());
        } else {
            info.nightscout.androidaps.plugins.pump.insight.app_layer.Service service = messageQueue.getActiveRequest().request.getService();
            ActivateServiceMessage activateServiceMessage = new ActivateServiceMessage();
            activateServiceMessage.setServiceID(ServiceIDs.IDS.getID(service));
            activateServiceMessage.setVersion(service.getVersion());
            activateServiceMessage.setServicePassword(Cryptograph.getServicePasswordHash(service.getServicePassword(), serviceChallengeMessage.getRandomData()));
            sendAppLayerMessage(activateServiceMessage);
        }
    }

    private void processGenericAppLayerMessage(AppLayerMessage appLayerMessage) {
        if (messageQueue.getActiveRequest() == null) handleException(new TooChattyPumpException());
        else {
            try {
                messageQueue.completeActiveRequest(appLayerMessage);
                lastDataTime = System.currentTimeMillis();
            } catch (Exception e) {
                messageQueue.completeActiveRequest(e);
            }
            requestNextMessage();
        }
    }

    void sendAppLayerMessage(AppLayerMessage appLayerMessage) {
        sendSatlMessage(AppLayerMessage.wrap(appLayerMessage));
    }

    @Override
    public synchronized void onConnectionFail(Exception e, long duration) {
        handleException(new ConnectionFailedException(duration));
    }

    @Override
    public synchronized void onErrorWhileReading(Exception e) {
        handleException(new ConnectionLostException());
    }

    @Override
    public synchronized void onErrorWhileWriting(Exception e) {
        handleException(new ConnectionLostException());
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    public class LocalBinder extends Binder {
        public InsightConnectionService getService() {
            return InsightConnectionService.this;
        }
    }

    public interface StateCallback {
        void onStateChanged(InsightState state);

        default void onPumpPaired() {
        }

        default void onTimeoutDuringHandshake() {
        }
    }

    public interface ExceptionCallback {
        void onExceptionOccur(Exception e);
    }
}
