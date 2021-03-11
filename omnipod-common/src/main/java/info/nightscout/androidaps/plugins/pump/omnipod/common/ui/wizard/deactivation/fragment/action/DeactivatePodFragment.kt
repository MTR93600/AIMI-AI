package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.fragment.action

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.fragment.ActionFragmentBase
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import javax.inject.Inject

class DeactivatePodFragment : ActionFragmentBase() {

    @Inject
    @OmnipodPluginQualifier
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var buttonDiscardPod: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: DeactivatePodViewModel by viewModels { viewModelFactory }
        this.viewModel = vm
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.omnipod_wizard_button_discard_pod)?.setOnClickListener {
            context?.let {
                AlertDialog.Builder(it)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(getString(R.string.omnipod_common_pod_deactivation_wizard_discard_pod))
                    .setMessage(getString(R.string.omnipod_common_pod_deactivation_wizard_discard_pod_confirmation))
                    .setPositiveButton(getString(R.string.omnipod_common_yes)) { _, _ ->
                        (viewModel as DeactivatePodViewModel).discardPod()
                        findNavController().navigate(R.id.action_deactivatePodFragment_to_podDiscardedFragment)
                    }
                    .setNegativeButton(getString(R.string.omnipod_common_no), null)
                    .show()
            }
        }
    }

    override fun onFailure() {
        buttonDiscardPod.visibility = View.VISIBLE
    }

    @IdRes
    override fun getNextPageActionId(): Int = R.id.action_deactivatePodFragment_to_podDeactivatedFragment

    override fun getIndex(): Int = 2
}