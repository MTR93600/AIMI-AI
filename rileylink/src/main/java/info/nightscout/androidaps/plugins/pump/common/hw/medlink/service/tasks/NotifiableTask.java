package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;

/**
 * Created by Dirceu on 16/11/20.
 */
public abstract class NotifiableTask extends ServiceTask {

    public NotifiableTask(HasAndroidInjector injector) {
        super(injector);
    }

    public NotifiableTask(HasAndroidInjector injector, ServiceTransport transport) {
        super(injector, transport);
    }

    public abstract void notiFyAnswer(String answer);
}
