package info.nightscout.androidaps.plugin.general.openhumans.dagger

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugin.general.openhumans.OpenHumansWorker
import info.nightscout.androidaps.plugin.general.openhumans.delegates.OHStateDelegate
import info.nightscout.androidaps.plugin.general.openhumans.ui.OHFragment
import info.nightscout.androidaps.plugin.general.openhumans.ui.OHLoginActivity
import info.nightscout.androidaps.plugin.general.openhumans.ui.OHLoginViewModel

@Module
abstract class OpenHumansModule {

    @ContributesAndroidInjector
    abstract fun contributesOHLoginActivity(): OHLoginActivity

    @ContributesAndroidInjector
    abstract fun contributesOHFragment(): OHFragment

    @ContributesAndroidInjector abstract fun contributesOpenHumansWorker(): OpenHumansWorker

    @Binds
    @IntoMap
    @ViewModelKey(OHLoginViewModel::class)
    internal abstract fun bindLoginViewModel(viewModel: OHLoginViewModel): ViewModel

    companion object {

        @BaseUrl
        @Provides
        fun providesBaseUrl(): String = "https://www.openhumans.org"

        @ClientId
        @Provides
        fun providesClientId(): String = "oie6DvnaEOagTxSoD6BukkLPwDhVr6cMlN74Ihz1"

        @ClientSecret
        @Provides
        fun providesClientSecret(): String =
            "jR0N8pkH1jOwtozHc7CsB1UPcJzFN95ldHcK4VGYIApecr8zGJox0v06xLwPLMASScngT12aIaIHXAVCJeKquEXAWG1XekZdbubSpccgNiQBmuVmIF8nc1xSKSNJltCf"

        @RedirectUrl
        @Provides
        fun providesRedirectUri(): String = "androidaps://setup-openhumans"

        @AuthUrl
        @Provides
        internal fun providesAuthUrl(@ClientId clientId: String): String =
            "https://www.openhumans.org/direct-sharing/projects/oauth2/authorize/?client_id=$clientId&response_type=code"

        @Provides
        internal fun providesStateLiveData(ohStateDelegate: OHStateDelegate) = ohStateDelegate.value
    }
}