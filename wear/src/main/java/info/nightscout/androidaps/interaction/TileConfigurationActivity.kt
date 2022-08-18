package info.nightscout.androidaps.interaction

import android.os.Bundle
import android.view.ViewGroup
import androidx.wear.tiles.TileService
import dagger.android.AndroidInjection
import info.nightscout.androidaps.tile.ActionsTileService
import info.nightscout.androidaps.tile.TempTargetTileService
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import preference.WearPreferenceActivity
import javax.inject.Inject

class TileConfigurationActivity : WearPreferenceActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    private var configFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        title = "Tile"
        configFileName = intent.action
        val resXmlId = resources.getIdentifier(configFileName, "xml", applicationContext.packageName)
        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::onCreate --->> getIntent().getAction() $configFileName")
        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::onCreate --->> resXmlId $resXmlId")
        addPreferencesFromResource(resXmlId)
        val view = window.decorView as ViewGroup
        view.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note that TileService updates are hard limited to once every 20 seconds.
        when {
            configFileName === "tile_configuration_activity" -> {
                aapsLogger.info(LTag.WEAR, "onDestroy a: requestUpdate")
                TileService.getUpdater(this).requestUpdate(ActionsTileService::class.java)
            }

            configFileName === "tile_configuration_tempt"    -> {
                aapsLogger.info(LTag.WEAR, "onDestroy tt: requestUpdate")
                TileService.getUpdater(this).requestUpdate(TempTargetTileService::class.java)
            }

            else                                             -> {
                aapsLogger.info(LTag.WEAR, "onDestroy : NO tile service available for $configFileName")
            }
        }
    }
}
