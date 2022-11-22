package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.ui.widget.WidgetConfigureActivity
import info.nightscout.androidaps.skins.SkinListPreference
import info.nightscout.ui.widget.Widget
import info.nightscout.androidaps.plugins.aps.loop.LoopVariantPreference
//import info.nightscout.androidaps.widget.Widget

@Module
@Suppress("unused")
abstract class UIModule {

    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
    @ContributesAndroidInjector abstract fun loopVariantPreferenceInjector(): LoopVariantPreference
    @ContributesAndroidInjector abstract fun contributesWidget(): Widget
    @ContributesAndroidInjector abstract fun contributesWidgetConfigureActivity(): WidgetConfigureActivity

}