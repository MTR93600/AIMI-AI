package info.nightscout.androidaps.di

import android.content.Context
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.implementations.ConfigImpl
import info.nightscout.androidaps.implementations.InstantiatorImpl
import info.nightscout.androidaps.implementations.UiInteractionImpl
import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.plugins.BgSyncImplementation
import info.nightscout.androidaps.workflow.CalculationWorkflowImpl
import info.nightscout.androidaps.workflow.WorkerClassesImpl
import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.workflow.WorkerClasses

@Suppress("unused")
@Module(
    includes = [
        AppModule.AppBindings::class
    ]
)
open class AppModule {

    @Provides
    fun providesPlugins(
        config: Config,
        @PluginsListModule.AllConfigs allConfigs: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>,
        @PluginsListModule.PumpDriver pumpDrivers: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.NotNSClient notNsClient: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.APS aps: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>,
        @PluginsListModule.Unfinished unfinished: Lazy<Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards PluginBase>>
    )
        : List<@JvmSuppressWildcards PluginBase> {
        val plugins = allConfigs.toMutableMap()
        if (config.PUMPDRIVERS) plugins += pumpDrivers.get()
        if (config.APS) plugins += aps.get()
        if (!config.NSCLIENT) plugins += notNsClient.get()
        if (config.isUnfinishedMode()) plugins += unfinished.get()
        return plugins.toList().sortedBy { it.first }.map { it.second }
    }

    @Module
    interface AppBindings {

        @Binds fun bindContext(mainApp: MainApp): Context
        @Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector
        @Binds fun bindConfigInterface(config: ConfigImpl): Config

        @Binds fun bindActivityNames(activityNames: UiInteractionImpl): UiInteraction
        @Binds fun bindWorkerClasses(workerClassesImpl: WorkerClassesImpl): WorkerClasses
        @Binds fun bindCalculationWorkflow(calculationWorkflow: CalculationWorkflowImpl): CalculationWorkflow
        @Binds fun bindInstantiator(instantiatorImpl: InstantiatorImpl): Instantiator

        /**
         * Medlink binding
         */
        @Binds fun bindBgSync(bgSyncImplementation: BgSyncImplementation): BgSync

    }
}

