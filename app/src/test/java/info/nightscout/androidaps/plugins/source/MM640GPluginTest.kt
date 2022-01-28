package info.nightscout.androidaps.plugins.source

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class MM640GPluginTest : TestBase() {

    private lateinit var mM640gPlugin: MM640gPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP

    @Before
    fun setup() {
        mM640gPlugin = MM640gPlugin(HasAndroidInjector { AndroidInjector { } }, rh, aapsLogger, sp)
    }

    @Test fun advancedFilteringSupported() {
        Assert.assertEquals(false, mM640gPlugin.advancedFilteringSupported())
    }
}