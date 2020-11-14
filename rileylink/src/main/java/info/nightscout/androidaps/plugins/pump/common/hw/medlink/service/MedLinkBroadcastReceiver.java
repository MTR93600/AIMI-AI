package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.android.DaggerBroadcastReceiver;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.InitializePumpManagerTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by Dirceu on 29/09/20.
 */
public class MedLinkBroadcastReceiver extends DaggerBroadcastReceiver {

    @Inject MedLinkServiceData medLinkServiceData;
    @Inject HasAndroidInjector injector;
    @Inject SP sp;
    @Inject AAPSLogger aapsLogger;

    @Inject ServiceTaskExecutor serviceTaskExecutor;
    @Inject ActivePluginProvider activePlugin;

    MedLinkService serviceInstance;
    protected Map<String, List<String>> broadcastIdentifiers = null;

    public MedLinkBroadcastReceiver(MedLinkService serviceInstance) {
        this.serviceInstance = serviceInstance;

        createBroadcastIdentifiers();
    }

    private void createBroadcastIdentifiers() {
        this.broadcastIdentifiers = new HashMap<>();

        // Bluetooth
        this.broadcastIdentifiers.put("Bluetooth", Arrays.asList( //
                RileyLinkConst.Intents.BluetoothConnected, //
                RileyLinkConst.Intents.BluetoothReconnected));

        // TuneUp
        this.broadcastIdentifiers.put("TuneUp", Arrays.asList( //
                RileyLinkConst.IPC.MSG_PUMP_tunePump, //
                RileyLinkConst.IPC.MSG_PUMP_quickTune));


        // MedLink
        this.broadcastIdentifiers.put("MedLink", Arrays.asList( //
                MedLinkConst.Intents.RileyLinkDisconnected, //
                MedLinkConst.Intents.MedLinkReady, //
                MedLinkConst.Intents.RileyLinkDisconnected, //
                MedLinkConst.Intents.RileyLinkNewAddressSet, //
                MedLinkConst.Intents.RileyLinkDisconnect));
    }

     protected MedLinkService getServiceInstance() {
        PumpInterface pump = activePlugin.getActivePump();
        MedLinkPumpDevice pumpDevice = (MedLinkPumpDevice) pump;
        return pumpDevice.getMedLinkService();
    }


    public boolean processBluetoothBroadcasts(String action) {

        if (action.equals(RileyLinkConst.Intents.BluetoothConnected)) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Bluetooth - Connected");
            serviceTaskExecutor.startTask(new DiscoverGattServicesTask(injector));

            return true;

        } else if (action.equals(RileyLinkConst.Intents.BluetoothReconnected)) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Bluetooth - Reconnecting");

            getServiceInstance().bluetoothInit();
            serviceTaskExecutor.startTask(new DiscoverGattServicesTask(injector, true));

            return true;
        } else {

            return false;
        }
    }


    private boolean processTuneUpBroadcasts(String action) {

        if (this.broadcastIdentifiers.get("TuneUp").contains(action)) {
            if (serviceInstance.getRileyLinkTargetDevice().isTuneUpEnabled()) {
                serviceTaskExecutor.startTask(new WakeAndTuneTask(injector));
            }
            return true;
        } else {
            return false;
        }
    }



    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (intent == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "onReceive: received null intent");
        } else {
            String action = intent.getAction();
            if (action == null) {
                aapsLogger.error("onReceive: null action");
            } else {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Received Broadcast: " + action);

                if (!processBluetoothBroadcasts(action) && //
                        !processMedLinkBroadcasts(action, context) && //
                        !processTuneUpBroadcasts(action) && //
                        !processApplicationSpecificBroadcasts(action, intent) //
                ) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Unhandled broadcast: action=" + action);
                }
            }
        }
    }

    public boolean processApplicationSpecificBroadcasts(String action, Intent intent) {
        aapsLogger
                .debug("Applicationspecificbroadcasts "+action);
        return false;
    }

    protected boolean processMedLinkBroadcasts(String action, Context context) {
        MedLinkService medLinkService = getServiceInstance();
        aapsLogger.debug("processMedLinkBroadcasts "+action );
        if (action.equals(MedLinkConst.Intents.RileyLinkDisconnected)) {
            aapsLogger.debug("M");
            if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                medLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.RileyLinkUnreachable);
            } else {
                medLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled);
            }
            return true;
        } else if (action.equals(MedLinkConst.Intents.MedLinkReady)) {
            aapsLogger.warn(LTag.PUMPCOMM, "MedtronicConst.Intents.MedLinkReady");
            // sendIPCNotification(RT2Const.IPC.MSG_note_WakingPump);

            medLinkService.getMedLinkBLE().enableNotifications();
            medLinkService.getMedLinkRFSpy().startReader(); // call startReader from outside?

            medLinkService.getMedLinkRFSpy().initializeRileyLink();
            String bleVersion = medLinkService.getMedLinkRFSpy().getBLEVersionCached();
            RileyLinkFirmwareVersion rlVersion = medLinkServiceData.firmwareVersion;

//            if (isLoggingEnabled())
            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy version (BLE113): " + bleVersion);
            medLinkService.getMedLinkServiceData().versionBLE113 = bleVersion;

//            if (isLoggingEnabled())
            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy Radio version (CC110): " + rlVersion.name());
            this.medLinkServiceData.versionCC110 = rlVersion;

            ServiceTask task = new InitializePumpManagerTask(injector, context);
            serviceTaskExecutor.startTask(task);
            aapsLogger.info(LTag.PUMPCOMM, "Announcing MedLink open For business");

            return true;
        } else if (action.equals(MedLinkConst.Intents.RileyLinkNewAddressSet)) {
            String medLinkBLEAddress = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "");
            if (medLinkBLEAddress.equals("")) {
                aapsLogger.error("No MedLink BLE Address saved in app");
                aapsLogger.error(sp.toString());
                aapsLogger.error("ERROR" + sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, ""));
            } else {
                aapsLogger.error("MedLink BLE Address saved in app");
                // showBusy("Configuring Service", 50);
                // rileyLinkBLE.findRileyLink(medLinkBLEAddress);
                medLinkService.reconfigureCommunicator(medLinkBLEAddress);
//                MainApp.getServiceClientConnection().setThisRileylink(medLinkBLEAddress);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnect)) {
            medLinkService.disconnectRileyLink();

            return true;
        } else if(action.equals(MedLinkConst.Intents.MedLinkConnected)){
            medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);
            return true;
        }else if(action.equals(MedLinkConst.Intents.MedLinkConnectionError)){
            medLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError,
                    RileyLinkError.NoContactWithDevice);
            processMedLinkBroadcasts(RileyLinkConst.IPC.MSG_PUMP_tunePump, context);
            return true;
        } else {
            return false;
        }

    }

    public void registerBroadcasts(Context context) {

        IntentFilter intentFilter = new IntentFilter();

        for (Map.Entry<String, List<String>> stringListEntry : broadcastIdentifiers.entrySet()) {

            for (String intentKey : stringListEntry.getValue()) {
                intentFilter.addAction(intentKey);
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(this, intentFilter);
    }


    public void unregisterBroadcasts(Context context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
    }

}
