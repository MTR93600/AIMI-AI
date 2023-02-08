package info.nightscout.androidaps.plugins.pump.danaRKorean

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRKoreanPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var instantiator: Instantiator
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private lateinit var danaRPlugin: DanaRKoreanPlugin

    val injector = HasAndroidInjector {
        AndroidInjector { }
    }

    @BeforeEach
    fun prepareMocks() {
        `when`(sp.getString(info.nightscout.pump.dana.R.string.key_danars_address, "")).thenReturn("")
        `when`(sp.getString(info.nightscout.pump.dana.R.string.key_danar_bt_name, "")).thenReturn("")
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(info.nightscout.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(info.nightscout.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, sp, dateUtil, instantiator)
        danaRPlugin = DanaRKoreanPlugin(injector, aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePluginProvider, sp, commandQueue, danaPump, dateUtil, fabricPrivacy,
                                        pumpSync, uiInteraction, danaHistoryDatabase)
    }

    @Test @Throws(Exception::class)
    fun basalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHBASALRATE)
        danaRPlugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(0.8, c.value(), 0.01)
        Assertions.assertEquals("DanaRKorean: Limiting max basal rate to 0.80 U/h because of pump limit", c.getReasons(aapsLogger))
        Assertions.assertEquals("DanaRKorean: Limiting max basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons(aapsLogger))
    }

    @Test @Throws(Exception::class)
    fun percentBasalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHPERCENTBASALRATE)
        danaRPlugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DanaRKorean: Limiting max percent rate to 200% because of pump limit", c.getReasons(aapsLogger))
        Assertions.assertEquals("DanaRKorean: Limiting max percent rate to 200% because of pump limit", c.getMostLimitedReasons(aapsLogger))
    }
}