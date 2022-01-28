package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation

import android.os.Bundle
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.activity.OmnipodWizardActivityBase

abstract class PodDeactivationWizardActivity : OmnipodWizardActivityBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.omnipod_common_pod_deactivation_wizard_activity)
    }

    override fun getTotalDefinedNumberOfSteps(): Int = 3

    override fun getActualNumberOfSteps(): Int = 3

}