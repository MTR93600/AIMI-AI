package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.view.View
import android.widget.LinearLayout
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLowRes @Inject constructor(private val config: Config) : SkinInterface {

    override val description: Int get() = R.string.lowres_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100

    override fun actionsLayout(isLandscape: Boolean, isSmallWidth: Boolean): Int =
        when {
            isLandscape                  -> R.layout.actions_fragment
            else                         -> R.layout.actions_fragment_lowres
        }

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, view: View, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        if (!config.NSCLIENT && isLandscape) moveButtonsLayout(view as LinearLayout)
    }
}
