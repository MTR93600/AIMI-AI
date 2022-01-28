package info.nightscout.androidaps.di

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import info.nightscout.androidaps.Aaps
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidInjectionModule::class,
        WearModule::class,
        ServicesModule::class
    ]
)
interface AppComponent : AndroidInjector<Aaps> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(aaps: Aaps): Builder

        fun build(): AppComponent
    }
}