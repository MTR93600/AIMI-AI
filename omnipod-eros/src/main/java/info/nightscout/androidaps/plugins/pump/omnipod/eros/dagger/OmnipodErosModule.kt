package info.nightscout.androidaps.plugins.pump.omnipod.eros.dagger

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.ActivityScope
import info.nightscout.androidaps.plugins.pump.omnipod.common.dagger.OmnipodWizardModule
import info.nightscout.androidaps.plugins.pump.omnipod.eros.data.RLHistoryItemOmnipod
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.ErosPodHistoryActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.ErosPodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.OmnipodErosOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.ErosPodActivationWizardActivity
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.ErosPodDeactivationWizardActivity

@Module
@Suppress("unused")
abstract class OmnipodErosModule {

    // ACTIVITIES

    @ContributesAndroidInjector
    abstract fun contributesPodManagementActivity(): ErosPodManagementActivity
    @ContributesAndroidInjector abstract fun contributesPodHistoryActivity(): ErosPodHistoryActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class, OmnipodErosWizardViewModelsModule::class])
    abstract fun contributesActivationWizardActivity(): ErosPodActivationWizardActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [OmnipodWizardModule::class, OmnipodErosWizardViewModelsModule::class])
    abstract fun contributesDeactivationWizardActivity(): ErosPodDeactivationWizardActivity

    // FRAGMENTS

    @ContributesAndroidInjector
    abstract fun contributesOmnipodFragment(): OmnipodErosOverviewFragment

    // SERVICES

    @ContributesAndroidInjector
    abstract fun omnipodCommunicationManagerProvider(): OmnipodRileyLinkCommunicationManager
    @ContributesAndroidInjector
    abstract fun contributesRileyLinkOmnipodService(): RileyLinkOmnipodService

    // DATA

    @ContributesAndroidInjector abstract fun rlHistoryItemOmnipod(): RLHistoryItemOmnipod

    companion object {

        @Provides
        fun podStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): PodStateManager = aapsErosPodStateManager
    }
}
