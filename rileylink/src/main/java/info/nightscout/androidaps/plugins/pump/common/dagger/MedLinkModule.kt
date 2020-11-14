package info.nightscout.androidaps.plugins.pump.common.dagger

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.MedLinkRFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.SendAndListen
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.InitializePumpManagerTask
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.WakeAndTuneTask


@Module
@Suppress("unused")
abstract class MedLinkModule {
    @ContributesAndroidInjector
    abstract fun medLinkCommunicationManagerProvider(): MedLinkCommunicationManager
    @ContributesAndroidInjector abstract fun medLinkBLEProvider(): MedLinkBLE
    @ContributesAndroidInjector abstract fun medLinkRFSpyProvider(): MedLinkRFSpy
    @ContributesAndroidInjector abstract fun wakeAndTuneTask(): WakeAndTuneTask
    @ContributesAndroidInjector abstract fun initializePumpManagerTask(): InitializePumpManagerTask
    @ContributesAndroidInjector abstract fun radioPacket(): RadioPacket
    @ContributesAndroidInjector abstract fun sendAndListen(): SendAndListen

}