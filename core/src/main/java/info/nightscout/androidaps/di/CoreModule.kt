package info.nightscout.androidaps.di

import android.content.Context
import android.telephony.SmsManager
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.resources.ResourceHelperImplementation
import javax.inject.Singleton

@Module(includes = [
    CoreReceiversModule::class,
    CoreFragmentsModule::class,
    CoreDataClassesModule::class
])
open class CoreModule {

    @Provides
    @Singleton
    fun provideResources(context: Context): ResourceHelper = ResourceHelperImplementation(context)

    @Provides
    fun smsManager() : SmsManager = SmsManager.getDefault()
}