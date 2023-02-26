package info.nightscout.plugins.aps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class AlgModule {

    @ContributesAndroidInjector abstract fun loggerCallbackInjector(): info.nightscout.plugins.aps.logger.LoggerCallback
    @ContributesAndroidInjector abstract fun determineBasalResultSMBInjector(): info.nightscout.plugins.aps.openAPSSMB.DetermineBasalResultSMB
    @ContributesAndroidInjector abstract fun determineBasalResultAMAInjector(): info.nightscout.plugins.aps.openAPSAMA.DetermineBasalResultAMA
    @ContributesAndroidInjector abstract fun determineBasalAdapterAMAJSInjector(): info.nightscout.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBJSInjector(): info.nightscout.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterAIMIJSInjector(): info.nightscout.plugins.aps.aimi.DetermineBasalAdapterAIMIJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBAutoISFJSInjector(): info.nightscout.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS
    @ContributesAndroidInjector abstract fun determineBasalAdapteraiSMBInjector(): info.nightscout.plugins.aps.openAPSaiSMB.DetermineBasalAdapteraiSMB
    @ContributesAndroidInjector abstract fun determineBasalResultaiSMBInjector(): info.nightscout.plugins.aps.openAPSaiSMB.DetermineBasalResultaiSMB
}