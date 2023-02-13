package info.nightscout.androidaps.plugins.pump.common.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.dialog.MedLinkBLEConfigActivity
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.SendAndListen
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.dialog.MedLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.dialog.MedLinkStatusGeneralFragment
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.dialog.MedLinkStatusHistoryFragment
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.WakeAndTuneTask
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.InitializeMedLinkPumpManagerTask

@Module
@Suppress("unused")
abstract class MedLinkModule {
    @ContributesAndroidInjector
    abstract fun medLinkCommunicationManagerProvider(): MedLinkCommunicationManager
    @ContributesAndroidInjector abstract fun medLinkBLEProvider(): MedLinkBLE
    @ContributesAndroidInjector abstract fun medLinkRFSpyProvider(): MedLinkRFSpy
    @ContributesAndroidInjector abstract fun wakeAndTuneTask(): WakeAndTuneTask
    @ContributesAndroidInjector abstract fun initializePumpManagerTask(): InitializeMedLinkPumpManagerTask
    @ContributesAndroidInjector abstract fun radioPacket(): RadioPacket
    @ContributesAndroidInjector abstract fun sendAndListen(): SendAndListen

    @ContributesAndroidInjector abstract fun medLinkService(): MedLinkService
    @ContributesAndroidInjector abstract fun medLinkBleScanActivity(): MedLinkBLEConfigActivity
    @ContributesAndroidInjector abstract fun contributesMedLinkStatusGeneral(): MedLinkStatusGeneralFragment
    @ContributesAndroidInjector abstract fun contributesMedLinkStatusHistoryFragment(): MedLinkStatusHistoryFragment
    @ContributesAndroidInjector abstract fun contributesMedLinkStatusActivity(): MedLinkStatusActivity
}