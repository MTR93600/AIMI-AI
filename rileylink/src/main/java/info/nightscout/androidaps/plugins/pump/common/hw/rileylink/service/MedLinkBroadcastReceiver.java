package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;

/**
 * Created by Dirceu on 29/09/20.
 */
public class MedLinkBroadcastReceiver extends RileyLinkBroadcastReceiver {

    public MedLinkBroadcastReceiver(RileyLinkService serviceInstance) {
        super(serviceInstance);
    }

    @Override protected RileyLinkService getServiceInstance() {
        PumpInterface pump = activePlugin.getActivePump();
        CommunicatorPumpDevice pumpDevice = (CommunicatorPumpDevice) pump;
        return pumpDevice.getService();
    }

    @Override protected boolean processRileyLinkBroadcasts(String action, Context context) {
        RileyLinkService rileyLinkService = getServiceInstance();

        if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnected)) {
            aapsLogger.debug("");
            if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.RileyLinkUnreachable);
            } else {
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkReady)) {

            aapsLogger.warn(LTag.PUMPCOMM, "MedtronicConst.Intents.RileyLinkReady");
            // sendIPCNotification(RT2Const.IPC.MSG_note_WakingPump);

            rileyLinkService.rileyLinkBLE.enableNotifications();
            rileyLinkService.rfspy.startReader(); // call startReader from outside?

            rileyLinkService.rfspy.initializeRileyLink();
            String bleVersion = rileyLinkService.rfspy.getBLEVersionCached();
            RileyLinkFirmwareVersion rlVersion = rileyLinkServiceData.firmwareVersion;

//            if (isLoggingEnabled())
            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy version (BLE113): " + bleVersion);
            rileyLinkService.rileyLinkServiceData.versionBLE113 = bleVersion;

//            if (isLoggingEnabled())
            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy Radio version (CC110): " + rlVersion.name());
            this.rileyLinkServiceData.versionCC110 = rlVersion;

            ServiceTask task = new InitializePumpManagerTask(injector, context);
            serviceTaskExecutor.startTask(task);
            aapsLogger.info(LTag.PUMPCOMM, "Announcing RileyLink open For business");

            return true;
        } else if (action.equals(MedLinkConst.Intents.RileyLinkNewAddressSet)) {
            String medLinkBLEAddress = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "");
            if (medLinkBLEAddress.equals("")) {
                aapsLogger.error("No MedLink BLE Address saved in app");
                aapsLogger.error(sp.toString());
                aapsLogger.error("ERROR" +sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, ""));
            } else {
                aapsLogger.error("MedLink BLE Address saved in app");
                // showBusy("Configuring Service", 50);
                // rileyLinkBLE.findRileyLink(medLinkBLEAddress);
                rileyLinkService.reconfigureRileyLink(medLinkBLEAddress);
                // MainApp.getServiceClientConnection().setThisRileylink(medLinkBLEAddress);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnect)) {
            rileyLinkService.disconnectRileyLink();

            return true;
        } else {
            return false;
        }

    }
}
