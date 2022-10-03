package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.widget.WidgetConfigureActivity
import info.nightscout.androidaps.skins.SkinListPreference
import info.nightscout.androidaps.plugins.aps.fullUAM.AIMIVariantPreference
import info.nightscout.androidaps.widget.Widget

@Module
@Suppress("unused")
abstract class UIModule {

    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
    @ContributesAndroidInjector abstract fun AIMIVariantPreferenceInjector(): AIMIVariantPreference
    @ContributesAndroidInjector abstract fun aapsWidgetInjector(): Widget
    @ContributesAndroidInjector abstract fun contributesWidgetConfigureActivity(): WidgetConfigureActivity

}