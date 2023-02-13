package info.nightscout.pump.common.di

import dagger.Module
import dagger.Provides
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.pump.common.sync.PumpSyncStorage
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Singleton

@Module
@Suppress("unused")
class PumpCommonModule {

    @Provides
    @Singleton
    fun providesPumpSyncStorage(
        pumpSync: PumpSync,
        sp: SP,
        aapsLogger: AAPSLogger,
        xDripBroadcast: XDripBroadcast
    ): PumpSyncStorage {
        return PumpSyncStorage(pumpSync, sp, aapsLogger, xDripBroadcast )
    }

}