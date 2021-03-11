package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin
import info.nightscout.androidaps.utils.T
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective5(injector: HasAndroidInjector) : Objective(injector, "maxiobzero", R.string.objectives_maxiobzero_objective, R.string.objectives_maxiobzero_gate) {

    @Inject lateinit var safetyPlugin: SafetyPlugin

    init {
        tasks.add(MinimumDurationTask(this, T.days(5).msecs()))
        tasks.add(object : Task(this, R.string.closedmodeenabled) {
            override fun isCompleted(): Boolean {
                val closedLoopEnabled = Constraint(true)
                safetyPlugin.isClosedLoopAllowed(closedLoopEnabled)
                return closedLoopEnabled.value()
            }
        })
    }
}