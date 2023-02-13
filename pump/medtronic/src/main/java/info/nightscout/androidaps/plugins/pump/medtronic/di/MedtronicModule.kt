package info.nightscout.androidaps.plugins.pump.medtronic.di

import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicFragment
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicFragment
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUIComm
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIComm
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedLinkMedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.service.MedLinkMedtronicService
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService
import info.nightscout.pump.core.utils.ByteUtil

@Module
@Suppress("unused")
abstract class MedtronicModule {

    @ContributesAndroidInjector
    abstract fun contributesMedLinkMedtronicService(): MedLinkMedtronicService

    @ContributesAndroidInjector
    abstract fun contributesMedLinkMedtronicCommunicationManager(): MedLinkMedtronicCommunicationManager
    // @ContributesAndroidInjector
    // abstract fun medLinkMedtronicUITaskProvider(): MedLinkMedtronicUITask
    // @ContributesAndroidInjector
    // abstract fun medLinkMedtronicUITaskCpProvider(): MedLinkMedtronicUITaskCp<Any,Any>

    @ContributesAndroidInjector
    abstract fun medLinkMedtronicUICommProvider(): MedLinkMedtronicUIComm
    @ContributesAndroidInjector
    abstract fun medLinkMedtronicFragmentProvider(): MedLinkMedtronicFragment
    @ContributesAndroidInjector
    abstract fun medLinkMedtronicHistoryActivityProvider(): MedLinkMedtronicHistoryActivity

    @ContributesAndroidInjector
    abstract fun contributesMedtronicHistoryActivity(): MedtronicHistoryActivity
    @ContributesAndroidInjector abstract fun contributesMedtronicFragment(): MedtronicFragment

    @ContributesAndroidInjector
    abstract fun contributesRileyLinkMedtronicService(): RileyLinkMedtronicService

    @ContributesAndroidInjector
    abstract fun medtronicCommunicationManagerProvider(): MedtronicCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicUITaskProvider(): MedtronicUITask
    @ContributesAndroidInjector abstract fun medtronicUICommProvider(): MedtronicUIComm

    companion object {

        @Provides
        fun byteUtilProvider(): ByteUtil = ByteUtil()
    }
}