package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUITaskCp

@Module
@Suppress("unused")
abstract class MedLinkMedtronicModule {
    @ContributesAndroidInjector abstract fun medtronicCommunicationManagerProvider(): MedLinkMedtronicCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicUITaskProviderCp(): MedLinkMedtronicUITaskCp
}