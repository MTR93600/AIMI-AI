package info.nightscout.plugins.aps.openAPSaiSMB


import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.extensions.target
import info.nightscout.core.utils.MidnightUtils
import info.nightscout.database.ValueWrapper
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.aps.DetermineBasalAdapter
import info.nightscout.interfaces.aps.SMBDefaults
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton


@OpenForTesting
@Singleton
class OpenAPSaiSMBPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintChecker: Constraints,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    override val context: Context,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    private val profiler: Profiler,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val repository: AppRepository,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val config: Config,
    private val bgQualityCheck: BgQualityCheck
) : OpenAPSSMBPlugin(
    injector,
    aapsLogger,
    rxBus,
    constraintChecker,
    rh,
    profileFunction,
    context,
    activePlugin,
    iobCobCalculator,
    hardLimits,
    profiler,
    sp,
    dateUtil,
    repository,
    glucoseStatusProvider,
    bgQualityCheck
) {

    init {
        pluginDescription
            .pluginName(R.string.openapsaismb)
            .description(R.string.description_aismb)
            .shortName(R.string.oaps_aismb_shortname)
            .preferencesId(R.xml.pref_openapsaismb)
            .setDefault(false)
            .showInList(config.isEngineeringMode() && config.isDev())
    }

    override fun specialEnableCondition(): Boolean = config.isEngineeringMode() && config.isDev()

    override fun provideDetermineBasalAdapter(): DetermineBasalAdapter = DetermineBasalAdapteraiSMB(injector)

}
