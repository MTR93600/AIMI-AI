package info.nightscout.androidaps.plugins.pump.omnipod.eros.manager;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodActiveAlertsChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodFaultEventChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodTbrChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodUncertainTbrRecovered;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AapsPodStateManager extends PodStateManager {
    private final SP sp;
    private final RxBusWrapper rxBus;

    @Inject
    public AapsPodStateManager(AAPSLogger aapsLogger, SP sp, RxBusWrapper rxBus) {
        super(aapsLogger);
        this.sp = sp;
        this.rxBus = rxBus;
    }

    @Override
    protected String readPodState() {
        return sp.getString(OmnipodStorageKeys.Preferences.POD_STATE, "");
    }

    @Override
    protected void storePodState(String podState) {
        sp.putString(OmnipodStorageKeys.Preferences.POD_STATE, podState);
    }

    @Override protected void onUncertainTbrRecovered() {
        rxBus.send(new EventOmnipodUncertainTbrRecovered());
    }

    @Override protected void onTbrChanged() {
        rxBus.send(new EventOmnipodTbrChanged());
    }

    @Override protected void onActiveAlertsChanged() {
        rxBus.send(new EventOmnipodActiveAlertsChanged());
    }

    @Override protected void onFaultEventChanged() {
        rxBus.send(new EventOmnipodFaultEventChanged());
    }

    @Override protected void onUpdatedFromResponse() {
        rxBus.send(new EventDismissNotification(Notification.OMNIPOD_STARTUP_STATUS_REFRESH_FAILED));
    }
}
