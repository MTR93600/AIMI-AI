package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUITask
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask

@Module
@Suppress("unused")
abstract class MedLinkMedtronicModule {
    @ContributesAndroidInjector abstract fun medtronicCommunicationManagerProvider(): MedLinkMedtronicCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicUITaskProvider(): MedLinkMedtronicUITask
}