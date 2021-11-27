package info.nightscout.androidaps.plugins.configBuilder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.databinding.ConfigbuilderFragmentBinding
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.events.EventConfigBuilderUpdateGui
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class ConfigBuilderFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var config: Config
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var ctx: Context

    private var disposable: CompositeDisposable = CompositeDisposable()
    private val pluginViewHolders = ArrayList<PluginViewHolder>()

    private var _binding: ConfigbuilderFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = ConfigbuilderFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (protectionCheck.isLocked(ProtectionCheck.Protection.PREFERENCES))
            binding.mainLayout.visibility = View.GONE
        else
            binding.unlock.visibility = View.GONE

        binding.unlock.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, {
                    activity.runOnUiThread {
                        binding.mainLayout.visibility = View.VISIBLE
                        binding.unlock.visibility = View.GONE
                    }
                })
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventConfigBuilderUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                for (pluginViewHolder in pluginViewHolders) pluginViewHolder.update()
            }, fabricPrivacy::logException)
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGUI() {
        binding.categories.removeAllViews()
        createViewsForPlugins(R.string.configbuilder_profile, R.string.configbuilder_profile_description, PluginType.PROFILE, activePlugin.getSpecificPluginsVisibleInList(PluginType.PROFILE))
        if (config.APS || config.PUMPCONTROL || buildHelper.isEngineeringMode())
            createViewsForPlugins(R.string.configbuilder_insulin, R.string.configbuilder_insulin_description, PluginType.INSULIN, activePlugin.getSpecificPluginsVisibleInList(PluginType.INSULIN))
        if (!config.NSCLIENT) {
            createViewsForPlugins(R.string.configbuilder_bgsource, R.string.configbuilder_bgsource_description, PluginType.BGSOURCE, activePlugin.getSpecificPluginsVisibleInList(PluginType.BGSOURCE))
            createViewsForPlugins(R.string.configbuilder_pump, R.string.configbuilder_pump_description, PluginType.PUMP, activePlugin.getSpecificPluginsVisibleInList(PluginType.PUMP))
        }
        if (config.APS || config.PUMPCONTROL || buildHelper.isEngineeringMode())
            createViewsForPlugins(R.string.configbuilder_sensitivity, R.string.configbuilder_sensitivity_description, PluginType.SENSITIVITY, activePlugin.getSpecificPluginsVisibleInList(PluginType.SENSITIVITY))
        if (config.APS) {
            createViewsForPlugins(R.string.configbuilder_aps, R.string.configbuilder_aps_description, PluginType.APS, activePlugin.getSpecificPluginsVisibleInList(PluginType.APS))
            createViewsForPlugins(R.string.configbuilder_loop, R.string.configbuilder_loop_description, PluginType.LOOP, activePlugin.getSpecificPluginsVisibleInList(PluginType.LOOP))
            createViewsForPlugins(R.string.constraints, R.string.configbuilder_constraints_description, PluginType.CONSTRAINTS, activePlugin.getSpecificPluginsVisibleInList(PluginType.CONSTRAINTS))
        }
        createViewsForPlugins(R.string.configbuilder_general, R.string.configbuilder_general_description, PluginType.GENERAL, activePlugin.getSpecificPluginsVisibleInList(PluginType.GENERAL))
    }

    private fun createViewsForPlugins(@StringRes title: Int, @StringRes description: Int, pluginType: PluginType, plugins: List<PluginBase>) {
        if (plugins.isEmpty()) return
        @Suppress("InflateParams")
        val parent = layoutInflater.inflate(R.layout.configbuilder_single_category, null) as LinearLayout
        (parent.findViewById<View>(R.id.category_title) as TextView).text = rh.gs(title)
        (parent.findViewById<View>(R.id.category_description) as TextView).text = rh.gs(description)
        val pluginContainer = parent.findViewById<LinearLayout>(R.id.category_plugins)
        for (plugin in plugins) {
            val pluginViewHolder = PluginViewHolder(this, pluginType, plugin)
            pluginContainer.addView(pluginViewHolder.baseView)
            pluginViewHolders.add(pluginViewHolder)
        }
        binding.categories.addView(parent)
    }

    inner class PluginViewHolder internal constructor(private val fragment: ConfigBuilderFragment,
                                                      private val pluginType: PluginType,
                                                      private val plugin: PluginBase) {

        @Suppress("InflateParams")
        val baseView: LinearLayout = fragment.layoutInflater.inflate(R.layout.configbuilder_single_plugin, null) as LinearLayout
        private val enabledExclusive: RadioButton = baseView.findViewById(R.id.plugin_enabled_exclusive)
        private val enabledInclusive: CheckBox = baseView.findViewById(R.id.plugin_enabled_inclusive)
        private val pluginIcon: ImageView = baseView.findViewById(R.id.plugin_icon)
        private val pluginIcon2: ImageView = baseView.findViewById(R.id.plugin_icon2)
        private val pluginName: TextView = baseView.findViewById(R.id.plugin_name)
        private val pluginDescription: TextView = baseView.findViewById(R.id.plugin_description)
        private val pluginPreferences: ImageButton = baseView.findViewById(R.id.plugin_preferences)
        private val pluginVisibility: CheckBox = baseView.findViewById(R.id.plugin_visibility)

        init {

            pluginVisibility.setOnClickListener {
                plugin.setFragmentVisible(pluginType, pluginVisibility.isChecked)
                configBuilderPlugin.storeSettings("CheckedCheckboxVisible")
                rxBus.send(EventRebuildTabs())
                configBuilderPlugin.logPluginStatus()
            }

            enabledExclusive.setOnClickListener {
                configBuilderPlugin.switchAllowed(plugin, if (enabledExclusive.visibility == View.VISIBLE) enabledExclusive.isChecked else enabledInclusive.isChecked, fragment.activity, pluginType)
            }
            enabledInclusive.setOnClickListener {
                configBuilderPlugin.switchAllowed(plugin, if (enabledExclusive.visibility == View.VISIBLE) enabledExclusive.isChecked else enabledInclusive.isChecked, fragment.activity, pluginType)
            }

            pluginPreferences.setOnClickListener {
                fragment.activity?.let { activity ->
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, {
                        val i = Intent(ctx, PreferencesActivity::class.java)
                        i.putExtra("id", plugin.preferencesId)
                        fragment.startActivity(i)
                    }, null)
                }
            }
            update()
        }

        fun update() {
            enabledExclusive.visibility = areMultipleSelectionsAllowed(pluginType).not().toVisibility()
            enabledInclusive.visibility = areMultipleSelectionsAllowed(pluginType).toVisibility()
            enabledExclusive.isChecked = plugin.isEnabled(pluginType)
            enabledInclusive.isChecked = plugin.isEnabled(pluginType)
            enabledInclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            enabledExclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
            if (plugin.menuIcon != -1) {
                pluginIcon.visibility = View.VISIBLE
                pluginIcon.setImageDrawable(context?.let { ContextCompat.getDrawable(it, plugin.menuIcon) })
                if (plugin.menuIcon2 != -1) {
                    pluginIcon2.visibility = View.VISIBLE
                    pluginIcon2.setImageDrawable(context?.let { ContextCompat.getDrawable(it, plugin.menuIcon2) })
                } else {
                    pluginIcon2.visibility = View.GONE
                }
            } else {
                pluginIcon.visibility = View.GONE
            }
            pluginName.text = plugin.name
            if (plugin.description == null)
                pluginDescription.visibility = View.GONE
            else {
                pluginDescription.visibility = View.VISIBLE
                pluginDescription.text = plugin.description
            }
            pluginPreferences.visibility = if (plugin.preferencesId == -1 || !plugin.isEnabled(pluginType)) View.INVISIBLE else View.VISIBLE
            pluginVisibility.visibility = plugin.hasFragment().toVisibility()
            pluginVisibility.isEnabled = !(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwaysVisible) && plugin.isEnabled(pluginType)
            pluginVisibility.isChecked = plugin.isFragmentVisible()
        }

        private fun areMultipleSelectionsAllowed(type: PluginType): Boolean {
            return type == PluginType.GENERAL || type == PluginType.CONSTRAINTS || type == PluginType.LOOP
        }
    }
}
