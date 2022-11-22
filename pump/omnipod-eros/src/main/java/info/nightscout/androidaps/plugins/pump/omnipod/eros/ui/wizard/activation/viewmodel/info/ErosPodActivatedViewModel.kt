package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.info

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.PodActivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import javax.inject.Inject

class ErosPodActivatedViewModel @Inject constructor() : PodActivatedViewModel() {

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_pod_activated_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_activation_wizard_pod_activated_text
}