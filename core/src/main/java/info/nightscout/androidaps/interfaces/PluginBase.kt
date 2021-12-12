package info.nightscout.androidaps.interfaces

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.utils.resources.ResourceHelper

/**
 * Created by mike on 09.06.2016.
 */
abstract class PluginBase(
    val pluginDescription: PluginDescription,
    val aapsLogger: AAPSLogger,
    val rh: ResourceHelper,
    val injector: HasAndroidInjector
) {

    enum class State {
        NOT_INITIALIZED, ENABLED, DISABLED
    }

    private var state = State.NOT_INITIALIZED
    private var fragmentVisible = false

    open val menuIcon: Int
        get() = pluginDescription.pluginIcon
    open val menuIcon2: Int
        get() = pluginDescription.pluginIcon2

    open val name: String
        get() = if (pluginDescription.pluginName == -1) "UNKNOWN" else rh.gs(pluginDescription.pluginName)

    //only if translation exists
    // use long name as fallback
    val nameShort: String
        get() {
            if (pluginDescription.shortName == -1) return name
            val translatedName = rh.gs(pluginDescription.shortName)
            return if (translatedName.trim { it <= ' ' }.isNotEmpty()) translatedName else name
            // use long name as fallback
        }

    val description: String?
        get() = if (pluginDescription.description == -1) null else rh.gs(pluginDescription.description)

    fun getType(): PluginType = pluginDescription.mainType

    open val preferencesId: Int
        get() = pluginDescription.preferencesId

    open fun isEnabled() = isEnabled(pluginDescription.mainType)

    fun isEnabled(type: PluginType): Boolean {
        if (pluginDescription.alwaysEnabled && type == pluginDescription.mainType) return true
        if (pluginDescription.mainType == PluginType.CONSTRAINTS && type == PluginType.CONSTRAINTS) return true
        if (type == pluginDescription.mainType) return state == State.ENABLED && specialEnableCondition()
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.PUMP && isEnabled(PluginType.PUMP)) return true
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.APS && isEnabled(PluginType.APS)) return true
        return false
    }

    fun hasFragment(): Boolean {
        return pluginDescription.fragmentClass != null
    }

    fun isDefault() = pluginDescription.defaultPlugin

    /**
     * So far plugin can have it's main type + ConstraintInterface
     * ConstraintInterface is enabled if main plugin is enabled
     */
    fun setPluginEnabled(type: PluginType, newState: Boolean) {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    aapsLogger.debug(LTag.CORE, "Starting: $name")
                    onStart()
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    onStop()
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                }
            }
        }
    }

    fun setFragmentVisible(type: PluginType, fragmentVisible: Boolean) {
        if (type == pluginDescription.mainType) {
            this.fragmentVisible = fragmentVisible && specialEnableCondition()
        }
    }

    fun isFragmentVisible(): Boolean {
        if (pluginDescription.alwaysVisible) return true
        return if (pluginDescription.neverVisible) false else fragmentVisible
    }

    fun showInList(type: PluginType): Boolean {
        if (pluginDescription.mainType == type) return pluginDescription.showInList && specialShowInListCondition()
        return false
    }

    open fun specialEnableCondition(): Boolean {
        return true
    }

    open fun specialShowInListCondition(): Boolean {
        return true
    }

    protected open fun onStart() {}
    protected open fun onStop() {}
    protected open fun onStateChange(type: PluginType?, oldState: State?, newState: State?) {}
    open fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {}
    open fun updatePreferenceSummary(pref: Preference) {}
}