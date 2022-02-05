package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService;
import info.nightscout.shared.logging.LTag;

/**
 * Created by geoff on 7/9/16.
 */
public class DiscoverGattServicesTask extends ServiceTask {


    @Inject AAPSLogger aapsLogger;

    public boolean needToConnect = false;


    public DiscoverGattServicesTask(HasAndroidInjector injector) {
        super(injector);
    }


    public DiscoverGattServicesTask(HasAndroidInjector injector, boolean needToConnect) {
        super(injector);
        this.needToConnect = needToConnect;
    }


    @Override
    public void run() {

        if (!isRileyLinkDevice()) {
            return;
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "Discovering services");
        CommunicatorPumpDevice pumpDevice = (CommunicatorPumpDevice) activePlugin.getActivePump();
        boolean isRiley = pumpDevice.getService() instanceof RileyLinkService;
        boolean isMedLink = pumpDevice.getService() instanceof MedLinkService;

        if (needToConnect) {
            if(isRiley){
                ((RileyLinkService)pumpDevice.getService()).getRileyLinkBLE().connectGatt();
            }else if(isMedLink){
                ((MedLinkService)pumpDevice.getService()).getMedLinkBLE().connectGatt();
            }
        }
        if(isRiley) {
            ((RileyLinkService)pumpDevice.getService()).getRileyLinkBLE().discoverServices();
        }else if (isMedLink){
            ((MedLinkService)pumpDevice.getService()).getMedLinkBLE().discoverServices();
        }
    }
}