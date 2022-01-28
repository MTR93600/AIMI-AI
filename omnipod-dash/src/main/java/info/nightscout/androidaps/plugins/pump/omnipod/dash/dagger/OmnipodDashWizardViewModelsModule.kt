package info.nightscout.androidaps.plugins.pump.omnipod.dash.dagger

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.ViewModelKey
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.AttachPodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.PodActivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.StartPodActivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDeactivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDiscardedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.StartPodDeactivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action.DashInitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action.DashInsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.info.DashAttachPodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.info.DashPodActivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.info.DashStartPodActivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.action.DashDeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.info.DashPodDeactivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.info.DashPodDiscardedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.info.DashStartPodDeactivationViewModel

@Module
@Suppress("unused")
abstract class OmnipodDashWizardViewModelsModule {
    // #### VIEW MODELS ############################################################################

    // POD ACTIVATION

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(StartPodActivationViewModel::class)
    internal abstract fun startPodActivationViewModel(viewModel: DashStartPodActivationViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(InitializePodViewModel::class)
    internal abstract fun initializePodViewModel(viewModel: DashInitializePodViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(AttachPodViewModel::class)
    internal abstract fun attachPodViewModel(viewModel: DashAttachPodViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(InsertCannulaViewModel::class)
    internal abstract fun insertCannulaViewModel(viewModel: DashInsertCannulaViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PodActivatedViewModel::class)
    internal abstract fun podActivatedViewModel(viewModel: DashPodActivatedViewModel): ViewModel

    // POD DEACTIVATION
    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(StartPodDeactivationViewModel::class)
    internal abstract fun startPodDeactivationViewModel(viewModel: DashStartPodDeactivationViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(DeactivatePodViewModel::class)
    internal abstract fun deactivatePodViewModel(viewModel: DashDeactivatePodViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PodDeactivatedViewModel::class)
    internal abstract fun podDeactivatedViewModel(viewModel: DashPodDeactivatedViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PodDiscardedViewModel::class)
    internal abstract fun podDiscardedViewModel(viewModel: DashPodDiscardedViewModel): ViewModel
}
