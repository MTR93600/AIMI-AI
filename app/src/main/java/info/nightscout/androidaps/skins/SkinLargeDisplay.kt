package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.OverviewFragmentBinding
import info.nightscout.androidaps.interfaces.Config
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLargeDisplay @Inject constructor(private val config: Config): SkinInterface {

    override val description: Int get() = R.string.largedisplay_description
    override val mainGraphHeight: Int get() = 400
    override val secondaryGraphHeight: Int get() = 150

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        super.preProcessLandscapeOverviewLayout(dm, binding, isLandscape, isTablet, isSmallHeight)
        if (!config.NSCLIENT && (isSmallHeight || isLandscape)) moveButtonsLayout(binding.root)
    }
}
