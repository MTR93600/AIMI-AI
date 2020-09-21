package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.T;

public class Objective8 extends Objective {

    public Objective8(HasAndroidInjector injector) {
        super(injector, "ama", R.string.objectives_ama_objective, 0);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(28).msecs()));
    }
}
