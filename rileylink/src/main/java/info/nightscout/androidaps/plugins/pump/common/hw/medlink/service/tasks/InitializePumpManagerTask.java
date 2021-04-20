package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks;

import android.content.Context;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by geoff on 7/9/16.
 * <p>
 * This class is intended to be run by the Service, for the Service. Not intended for clients to run.
 */
public class InitializePumpManagerTask extends NotifiableTask {

    @Inject AAPSLogger aapsLogger;
    @Inject ActivePluginProvider activePlugin;
    @Inject SP sp;
    @Inject MedLinkServiceData medLinkServiceData;
    @Inject MedLinkUtil medLinkUtil;

    private final Context context;

    public InitializePumpManagerTask(HasAndroidInjector injector, Context context) {
        super(injector);
        this.context = context;
    }

    public InitializePumpManagerTask(HasAndroidInjector injector, Context context, ServiceTransport transport) {
        super(injector, transport);
        this.context = context;
    }

    @Override
    public void run() {

        double lastGoodFrequency;

//        if (medLinkServiceData.lastGoodFrequency == null) {
//
//            lastGoodFrequency = sp.getDouble(RileyLinkConst.Prefs.LastGoodDeviceFrequency, 0.0d);
//            lastGoodFrequency = Math.round(lastGoodFrequency * 1000d) / 1000d;
//
//            medLinkServiceData.lastGoodFrequency = lastGoodFrequency;
//
////            if (RileyLinkUtil.getRileyLinkTargetFrequency() == null) {
////                String pumpFrequency = SP.getString(MedtronicConst.Prefs.PumpFrequency, null);
////            }
//        } else {
//            lastGoodFrequency = medLinkServiceData.lastGoodFrequency;
//        }

        MedLinkCommunicationManager medLinkCommunicationManager = ((MedLinkPumpDevice) activePlugin.getActivePump()).getMedLinkService().getDeviceCommunicationManager();

        aapsLogger.info(LTag.PUMPBTCOMM,activePlugin.getActivePump().manufacturer().getDescription());
        if (activePlugin.getActivePump().manufacturer() == ManufacturerType.Medtronic) {

//            if ((lastGoodFrequency > 0.0d)
//                    && medLinkCommunicationManager.isValidFrequency(lastGoodFrequency)) {

                medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.MedLinkReady);

//                aapsLogger.info(LTag.PUMPBTCOMM, "Setting radio frequency to {} MHz", lastGoodFrequency);

//                medLinkCommunicationManager.setRadioFrequencyForPump(lastGoodFrequency);

//                boolean foundThePump =
//            medLinkCommunicationManager.getPumpStatus().lastConnection;
                  if(System.currentTimeMillis() -
                          medLinkCommunicationManager.getPumpStatus().lastConnection > 300000){
                      medLinkCommunicationManager.wakeUp(false);
                  }

//                if (foundThePump) {
//                    medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.PumpConnectorReady);
//                } else {
//                    medLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError,
//                            RileyLinkError.NoContactWithDevice);
//                    medLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump, context);
//                }

//            } else {
//                medLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump, context);
//            }
        } else {

//            if (!Round.isSame(lastGoodFrequency, RileyLinkTargetFrequency.Omnipod.getScanFrequencies()[0])) {
//                lastGoodFrequency = RileyLinkTargetFrequency.Omnipod.getScanFrequencies()[0];
//                lastGoodFrequency = Math.round(lastGoodFrequency * 1000d) / 1000d;
//
//                medLinkServiceData.lastGoodFrequency = lastGoodFrequency;
//            }

//            medLinkServiceData.setRileyLinkServiceState(RileyLinkServiceState.RileyLinkReady);
//            medLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod; // TODO shouldn't be needed

//            aapsLogger.info(LTag.PUMPBTCOMM, "Setting radio frequency to {} MHz", lastGoodFrequency);

//            medLinkCommunicationManager.setRadioFrequencyForPump(lastGoodFrequency);

            medLinkServiceData.setMedLinkServiceState(MedLinkServiceState.PumpConnectorReady);

        }
    }

    @Override public void notiFyAnswer(String answer) {

    }
}
