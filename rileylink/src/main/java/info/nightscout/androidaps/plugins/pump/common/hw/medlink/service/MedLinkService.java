package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import dagger.android.DaggerService;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.interfaces.ResourceHelper;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;

/**
 * Created by Dirceu on 30/09/20.
 */
public abstract class MedLinkService extends DaggerService {

    @Inject protected AAPSLogger aapsLogger;
    @Inject protected SP sp;
    @Inject protected Context context;
    @Inject protected RxBus rxBus;
    @Inject protected MedLinkUtil medLinkUtil;
    @Inject protected HasAndroidInjector injector;
    @Inject protected ResourceHelper resourceHelper;
    @Inject protected MedLinkServiceData medLinkServiceData;
    @Inject protected ActivePlugin activePlugin;
    @Inject protected MedLinkBLE medLinkBLE; // android-bluetooth management

    protected BluetoothAdapter bluetoothAdapter;
    protected MedLinkBroadcastReceiver mBroadcastReceiver;
    protected MedLinkBluetoothStateReceiver bluetoothStateReceiver;


    @Override
    public void onCreate() {
        super.onCreate();

        aapsLogger.info(LTag.EVENTS,"OnCreate medlinkservice");
        medLinkUtil.setEncoding(getEncoding());
        initRileyLinkServiceData();

        mBroadcastReceiver = new MedLinkBroadcastReceiver(this);
        mBroadcastReceiver.registerBroadcasts(this);


        bluetoothStateReceiver = new MedLinkBluetoothStateReceiver();
        bluetoothStateReceiver.registerBroadcasts(this);
    }

    /**
     * Get Encoding for RileyLink communication
     */
    public abstract MedLinkEncodingType getEncoding();


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public abstract void initRileyLinkServiceData();


    @Override
    public boolean onUnbind(Intent intent) {
        //aapsLogger.warn(LTag.PUMPBTCOMM, "onUnbind");
        return super.onUnbind(intent);
    }


    @Override
    public void onRebind(Intent intent) {
        //aapsLogger.warn(LTag.PUMPBTCOMM, "onRebind");
        super.onRebind(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        //LOG.error("I die! I die!");

        // xyz rfspy.stopReader();
        medLinkBLE.disconnect(); // dispose of Gatt (disconnect and close)

        if (mBroadcastReceiver != null) {
            mBroadcastReceiver.unregisterBroadcasts(this);
        }

        if (bluetoothStateReceiver != null) {
            bluetoothStateReceiver.unregisterBroadcasts(this);
        }

    }


    public abstract MedLinkCommunicationManager getDeviceCommunicationManager();

    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return (START_STICKY);
    }


    public boolean bluetoothInit() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "bluetoothInit: attempting to get an adapter");
        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.BluetoothInitializing);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.");
            medLinkServiceData.setServiceState(MedLinkServiceState.BluetoothError,
                    MedLinkError.NoBluetoothAdapter);
        } else {

            if (!bluetoothAdapter.isEnabled()) {
                aapsLogger.error("Bluetooth is not enabled.");
                medLinkServiceData.setServiceState(MedLinkServiceState.BluetoothError,
                        MedLinkError.BluetoothDisabled);
            } else {
                medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.BluetoothReady);
                return true;
            }
        }

        return false;
    }


    // returns true if our Rileylink configuration changed
    public boolean reconfigureCommunicator(String deviceAddress) {

        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkInitializing);

        if (medLinkBLE.isConnected()) {
            if (deviceAddress.equals(medLinkServiceData.rileylinkAddress)) {
                aapsLogger.info(LTag.PUMPBTCOMM, "No change to RL address.  Not reconnecting.");
                return false;
            } else {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Disconnecting from old RL (" + medLinkServiceData.rileylinkAddress
                        + "), reconnecting to new: " + deviceAddress);

                medLinkBLE.disconnect();
                // prolly need to shut down listening thread too?
                // SP.putString(MedtronicConst.Prefs.RileyLinkAddress, deviceAddress);

                medLinkServiceData.rileylinkAddress = deviceAddress;
                medLinkBLE.findMedLink(medLinkServiceData.rileylinkAddress);
                return true;
            }
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using ML " + deviceAddress);

            if (medLinkServiceData.getMedLinkServiceState() == MedLinkServiceState.NotStarted) {
                if (!bluetoothInit()) {
                    aapsLogger.error("MedLink can't get activated, Bluetooth is not functioning correctly. {}",
                            getError() != null ? getError().name() : "Unknown error (null)");
                    return false;
                }
            }

            medLinkBLE.findMedLink(deviceAddress);

            return true;
        }
    }

    // FIXME: This needs to be run in a session so that is interruptable, has a separate thread, etc.
    public void doTuneUpDevice() {
        aapsLogger.debug("DoTuneIp");
        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.TuneUpDevice);
        setPumpDeviceState(PumpDeviceState.Sleeping);

        double lastGoodFrequency = 0.0d;

        if (medLinkServiceData.lastGoodFrequency == null) {
            lastGoodFrequency = sp.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0d);
        } else {
            lastGoodFrequency = medLinkServiceData.lastGoodFrequency;
        }

        double newFrequency;

        newFrequency = getDeviceCommunicationManager().tuneForDevice();

        if ((newFrequency != 0.0) && (newFrequency != lastGoodFrequency)) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Saving new pump frequency of {} MHz", newFrequency);
            sp.putDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, newFrequency);
            medLinkServiceData.lastGoodFrequency = newFrequency;
            medLinkServiceData.tuneUpDone = true;
            medLinkServiceData.lastTuneUpTime = System.currentTimeMillis();
        }
        aapsLogger.debug("New pump frequency "+newFrequency);
        if (newFrequency == 0.0d) {
            // error tuning pump, pump not present ??
            medLinkServiceData.setServiceState(MedLinkServiceState.PumpConnectorError,
                    MedLinkError.TuneUpOfDeviceFailed);
        } else {
            getDeviceCommunicationManager().clearNotConnectedCount();
            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);
        }
    }
    public abstract void setPumpDeviceState(PumpDeviceState pumpDeviceState);




    public void disconnectRileyLink() {

        if (medLinkBLE.isConnected()) {
            aapsLogger.info(LTag.PUMPBTCOMM, "disconnectingMedlink");
            medLinkBLE.disconnect();
            medLinkServiceData.rileylinkAddress = null;
        }

        medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.BluetoothReady);
    }

    @NotNull public MedLinkBLE getMedLinkBLE() {
        return medLinkBLE;
    }

    /**
     * Get Target Device for Service
     */
    public RileyLinkTargetDevice getRileyLinkTargetDevice() {
        return this.medLinkServiceData.targetDevice;
    }


    public void changeMedLinkEncoding(MedLinkEncodingType encodingType) {
    }

    public MedLinkError getError() {
        if (medLinkServiceData != null)
            return medLinkServiceData.medLinkError;
        else
            return null;
    }

    public abstract boolean verifyConfiguration();


    public MedLinkServiceData getMedLinkServiceData(){
        return this.medLinkServiceData;
    }
}
