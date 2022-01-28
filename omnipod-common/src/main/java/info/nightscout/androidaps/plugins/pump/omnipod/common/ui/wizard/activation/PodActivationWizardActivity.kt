package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation

import android.os.Bundle
import androidx.annotation.IdRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.activity.OmnipodWizardActivityBase

abstract class PodActivationWizardActivity : OmnipodWizardActivityBase() {
    companion object {

        const val KEY_TYPE = "wizardType"
        const val KEY_START_DESTINATION = "startDestination"
    }

    enum class Type {
        SHORT,
        LONG
    }

    @IdRes
    private var startDestination: Int = R.id.startPodActivationFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_common_pod_activation_wizard_activity)

        startDestination = savedInstanceState?.getInt(KEY_START_DESTINATION, R.id.startPodActivationFragment)
            ?: if (intent.getSerializableExtra(KEY_TYPE) as Type == Type.LONG) {
                R.id.startPodActivationFragment
            } else {
                R.id.attachPodFragment
            }

        setStartDestination(startDestination)
    }

    private fun setStartDestination(@IdRes startDestination: Int) {
        this.startDestination = startDestination
        val navController = getNavController()
        val navInflater = navController.navInflater
        val graph = navInflater.inflate(R.navigation.omnipod_common_pod_activation_wizard_navigation_graph)
        graph.startDestination = startDestination
        navController.graph = graph
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_START_DESTINATION, startDestination)
    }

    override fun getTotalDefinedNumberOfSteps(): Int = 5

    override fun getActualNumberOfSteps(): Int {
        if (startDestination == R.id.attachPodFragment) {
            return 3
        }
        return 5
    }

}