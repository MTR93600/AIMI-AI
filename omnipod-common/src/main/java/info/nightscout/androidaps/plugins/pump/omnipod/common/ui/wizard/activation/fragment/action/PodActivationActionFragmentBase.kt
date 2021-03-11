package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.fragment.action

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.PodActivationActionViewModelBase
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.fragment.ActionFragmentBase
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.PodDeactivationWizardActivity

abstract class PodActivationActionFragmentBase : ActionFragmentBase() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.omnipod_wizard_button_deactivate_pod).setOnClickListener {
            activity?.let {
                startActivity(Intent(it, PodDeactivationWizardActivity::class.java))
                it.finish()
            }
        }
    }

    override fun onFailure() {
        (viewModel as? PodActivationActionViewModelBase)?.let { viewModel ->
            if (viewModel.isPodDeactivatable() and (viewModel.isPodInAlarm() or viewModel.isPodActivationTimeExceeded())) {
                view?.let {
                    it.findViewById<Button>(R.id.omnipod_wizard_button_retry)?.visibility = View.GONE
                    it.findViewById<Button>(R.id.omnipod_wizard_button_deactivate_pod)?.visibility = View.VISIBLE
                }
            }
        }
    }
}