package info.nightscout.implementation.plugin

import info.nightscout.interfaces.Config
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.Overview
import info.nightscout.interfaces.aps.APS
import info.nightscout.interfaces.aps.Sensitivity
import info.nightscout.interfaces.constraints.Objectives
import info.nightscout.interfaces.constraints.Safety
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.smoothing.Smoothing
import info.nightscout.interfaces.source.BgSource
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.interfaces.sync.Sync
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginStore @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val config: Config
) : ActivePlugin {

    lateinit var plugins: List<@JvmSuppressWildcards PluginBase>

    private var activeBgSourceStore: BgSource? = null
    private var activePumpStore: Pump? = null
    private var activeProfile: ProfileSource? = null
    private var activeAPSStore: APS? = null
    private var activeInsulinStore: Insulin? = null
    private var activeSensitivityStore: Sensitivity? = null
    private var activeSmoothingStore: Smoothing? = null

    override fun loadDefaults() {
        verifySelectionInCategories()
    }

    private fun getDefaultPlugin(type: PluginType): PluginBase {
        for (p in plugins)
            if (p.getType() == type && p.isDefault()) return p
        throw IllegalStateException("Default plugin not found")
    }

    override fun getSpecificPluginsList(type: PluginType): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.getType() == type) newList.add(p)
        }
        return newList
    }

    override fun getSpecificPluginsListByInterface(interfaceClass: Class<*>): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (!interfaceClass.isAssignableFrom(ConfigBuilder::class.java) && interfaceClass.isAssignableFrom(p.javaClass)) newList.add(p)
        }
        return newList
    }

    override fun getSpecificPluginsVisibleInList(type: PluginType): ArrayList<PluginBase> {
        val newList = ArrayList<PluginBase>()
        for (p in plugins) {
            if (p.getType() == type) if (p.showInList(type)) newList.add(p)
        }
        return newList
    }

    override fun verifySelectionInCategories() {
        var pluginsInCategory: ArrayList<PluginBase>?

        // PluginType.APS
        if (!config.NSCLIENT && !config.PUMPCONTROL) {
            pluginsInCategory = getSpecificPluginsList(PluginType.APS)
            activeAPSStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.APS) as APS?
            if (activeAPSStore == null) {
                activeAPSStore = getDefaultPlugin(PluginType.APS) as APS
                (activeAPSStore as PluginBase).setPluginEnabled(PluginType.APS, true)
                aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting APSInterface")
            }
            setFragmentVisibilities((activeAPSStore as PluginBase).name, pluginsInCategory, PluginType.APS)
        }

        // PluginType.INSULIN
        pluginsInCategory = getSpecificPluginsList(PluginType.INSULIN)
        activeInsulinStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.INSULIN) as Insulin?
        if (activeInsulinStore == null) {
            activeInsulinStore = getDefaultPlugin(PluginType.INSULIN) as Insulin
            (activeInsulinStore as PluginBase).setPluginEnabled(PluginType.INSULIN, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting InsulinInterface")
        }
        setFragmentVisibilities((activeInsulinStore as PluginBase).name, pluginsInCategory, PluginType.INSULIN)

        // PluginType.SENSITIVITY
        pluginsInCategory = getSpecificPluginsList(PluginType.SENSITIVITY)
        activeSensitivityStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SENSITIVITY) as Sensitivity?
        if (activeSensitivityStore == null) {
            activeSensitivityStore = getDefaultPlugin(PluginType.SENSITIVITY) as Sensitivity
            (activeSensitivityStore as PluginBase).setPluginEnabled(PluginType.SENSITIVITY, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SensitivityInterface")
        }
        setFragmentVisibilities((activeSensitivityStore as PluginBase).name, pluginsInCategory, PluginType.SENSITIVITY)

        // PluginType.SMOOTHING
        pluginsInCategory = getSpecificPluginsList(PluginType.SMOOTHING)
        activeSmoothingStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.SMOOTHING) as Smoothing?
        if (activeSmoothingStore == null) {
            activeSmoothingStore = getDefaultPlugin(PluginType.SMOOTHING) as Smoothing
            (activeSmoothingStore as PluginBase).setPluginEnabled(PluginType.SMOOTHING, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting SmoothingInterface")
        }
        setFragmentVisibilities((activeSmoothingStore as PluginBase).name, pluginsInCategory, PluginType.SMOOTHING)

        // PluginType.PROFILE
        pluginsInCategory = getSpecificPluginsList(PluginType.PROFILE)
        activeProfile = getTheOneEnabledInArray(pluginsInCategory, PluginType.PROFILE) as ProfileSource?
        if (activeProfile == null) {
            activeProfile = getDefaultPlugin(PluginType.PROFILE) as ProfileSource
            (activeProfile as PluginBase).setPluginEnabled(PluginType.PROFILE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting ProfileInterface")
        }
        setFragmentVisibilities((activeProfile as PluginBase).name, pluginsInCategory, PluginType.PROFILE)

        // PluginType.BGSOURCE
        pluginsInCategory = getSpecificPluginsList(PluginType.BGSOURCE)
        activeBgSourceStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.BGSOURCE) as BgSource?
        if (activeBgSourceStore == null) {
            activeBgSourceStore = getDefaultPlugin(PluginType.BGSOURCE) as BgSource
            (activeBgSourceStore as PluginBase).setPluginEnabled(PluginType.BGSOURCE, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting BgInterface")
        }
        setFragmentVisibilities((activeBgSourceStore as PluginBase).name, pluginsInCategory, PluginType.BGSOURCE)

        // PluginType.PUMP
        pluginsInCategory = getSpecificPluginsList(PluginType.PUMP)
        activePumpStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP) as Pump?
        if (activePumpStore == null) {
            activePumpStore = getDefaultPlugin(PluginType.PUMP) as Pump
            (activePumpStore as PluginBase).setPluginEnabled(PluginType.PUMP, true)
            aapsLogger.debug(LTag.CONFIGBUILDER, "Defaulting PumpInterface")
        }
        setFragmentVisibilities((activePumpStore as PluginBase).name, pluginsInCategory, PluginType.PUMP)
    }

    private fun setFragmentVisibilities(
        activePluginName: String, pluginsInCategory: ArrayList<PluginBase>,
        pluginType: PluginType
    ) {
        aapsLogger.debug(LTag.CONFIGBUILDER, "Selected interface: $activePluginName")
        for (p in pluginsInCategory)
            if (p.name != activePluginName)
                p.setFragmentVisible(pluginType, false)
    }

    private fun getTheOneEnabledInArray(pluginsInCategory: ArrayList<PluginBase>, type: PluginType): PluginBase? {
        var found: PluginBase? = null
        for (p in pluginsInCategory) {
            if (p.isEnabled(type) && found == null) {
                found = p
            } else if (p.isEnabled(type)) {
                // set others disabled
                p.setPluginEnabled(type, false)
            }
        }
        return found
    }

    // ***** Interface *****

    override val activeBgSource: BgSource
        get() = activeBgSourceStore ?: checkNotNull(activeBgSourceStore) { "No bg source selected" }

    override val activeProfileSource: ProfileSource
        get() = activeProfile ?: checkNotNull(activeProfile) { "No profile selected" }

    override val activeInsulin: Insulin
        get() = activeInsulinStore ?: checkNotNull(activeInsulinStore) { "No insulin selected" }

    override val activeAPS: APS
        get() = activeAPSStore ?: checkNotNull(activeAPSStore) { "No APS selected" }

    override val activePump: Pump
        get() = activePumpStore ?: checkNotNull(activePumpStore) { "No pump selected" }

    override val activeSensitivity: Sensitivity
        get() = activeSensitivityStore
            ?: checkNotNull(activeSensitivityStore) { "No sensitivity selected" }

    override val activeSmoothing: Smoothing
        get() = activeSmoothingStore
            ?: checkNotNull(activeSmoothingStore) { "No smoothing selected" }

    override val activeOverview: Overview
        get() = getSpecificPluginsListByInterface(Overview::class.java).first() as Overview

    override val activeSafety: Safety
        get() = getSpecificPluginsListByInterface(Safety::class.java).first() as Safety

    override val activeIobCobCalculator: IobCobCalculator
        get() = getSpecificPluginsListByInterface(IobCobCalculator::class.java).first() as IobCobCalculator
    override val activeObjectives: Objectives?
        get() = getSpecificPluginsListByInterface(Objectives::class.java).firstOrNull() as Objectives?
    override val activeNsClient: NsClient?
        get() = getTheOneEnabledInArray(getSpecificPluginsListByInterface(NsClient::class.java), PluginType.SYNC) as NsClient?

    @Suppress("UNCHECKED_CAST")
    override val firstActiveSync: Sync?
        get() = (getSpecificPluginsList(PluginType.SYNC) as ArrayList<Sync>).firstOrNull { it.connected }

    @Suppress("UNCHECKED_CAST")
    override val activeSyncs: ArrayList<Sync>
        get() = getSpecificPluginsList(PluginType.SYNC) as ArrayList<Sync>

    override fun getPluginsList(): ArrayList<PluginBase> = ArrayList(plugins)

}