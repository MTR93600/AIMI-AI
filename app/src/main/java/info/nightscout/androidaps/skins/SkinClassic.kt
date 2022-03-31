package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.view.View
import android.widget.LinearLayout
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.OverviewFragmentBinding
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinClassic @Inject constructor(private val config: Config): SkinInterface {

    override val description: Int get() = R.string.classic_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        super.preProcessLandscapeOverviewLayout(dm, binding, isLandscape, isTablet, isSmallHeight)
        if (!config.NSCLIENT && (isSmallHeight || isLandscape)) moveButtonsLayout(binding.root)
    }
}
