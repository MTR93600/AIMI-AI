package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsBolusCarbsFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsCareportalFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsExtendedBolusesFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsProfileSwitchFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsTempTargetFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsTemporaryBasalsFragment
import info.nightscout.androidaps.activities.fragments.TreatmentsUserEntryFragment
import info.nightscout.androidaps.dialogs.ExtendedBolusDialog
import info.nightscout.androidaps.dialogs.FillDialog
import info.nightscout.androidaps.dialogs.InsulinDialog
import info.nightscout.androidaps.dialogs.LoopDialog
import info.nightscout.androidaps.dialogs.NtpProgressDialog
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.dialogs.TempBasalDialog
import info.nightscout.androidaps.dialogs.TempTargetDialog
import info.nightscout.androidaps.dialogs.TreatmentDialog
import info.nightscout.androidaps.dialogs.WizardDialog
import info.nightscout.androidaps.dialogs.WizardInfoDialog
import info.nightscout.androidaps.plugins.aps.OpenAPSFragment
import info.nightscout.androidaps.plugins.aps.loop.LoopFragment
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesFragment
import info.nightscout.androidaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import info.nightscout.androidaps.plugins.general.actions.ActionsFragment
import info.nightscout.androidaps.plugins.general.maintenance.MaintenanceFragment
import info.nightscout.androidaps.plugins.general.nsclient.NSClientFragment
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolFragment
import info.nightscout.androidaps.plugins.general.wear.WearFragment
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpFragment
import info.nightscout.androidaps.plugins.source.BGSourceFragment
import info.nightscout.androidaps.activities.fragments.*
import info.nightscout.androidaps.plugins.general.automation.dialogs.*
import info.nightscout.androidaps.plugins.aps.AIMI.AIMIFragment
import info.nightscout.androidaps.utils.protection.PasswordCheck
import info.nightscout.plugins.general.autotune.AutotuneFragment

@Module
@Suppress("unused")
abstract class FragmentsModule {

    @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @ContributesAndroidInjector abstract fun contributesActionsFragment(): ActionsFragment
    @ContributesAndroidInjector abstract fun contributesAutotuneFragment(): AutotuneFragment
    @ContributesAndroidInjector abstract fun contributesBGSourceFragment(): BGSourceFragment
    @ContributesAndroidInjector abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment
    @ContributesAndroidInjector abstract fun contributesObjectivesFragment(): ObjectivesFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
    @ContributesAndroidInjector abstract fun contributesAIMIFragment(): AIMIFragment
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun contributesLoopFragment(): LoopFragment
    @ContributesAndroidInjector abstract fun contributesMaintenanceFragment(): MaintenanceFragment
    @ContributesAndroidInjector abstract fun contributesNSClientFragment(): NSClientFragment
    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment

    @ContributesAndroidInjector abstract fun contributesTidepoolFragment(): TidepoolFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsBolusFragment(): TreatmentsBolusCarbsFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsTemporaryBasalsFragment(): TreatmentsTemporaryBasalsFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsTempTargetFragment(): TreatmentsTempTargetFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsExtendedBolusesFragment(): TreatmentsExtendedBolusesFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsCareportalFragment(): TreatmentsCareportalFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsProfileSwitchFragment(): TreatmentsProfileSwitchFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsUserEntryFragment(): TreatmentsUserEntryFragment

    @ContributesAndroidInjector abstract fun contributesVirtualPumpFragment(): VirtualPumpFragment

    @ContributesAndroidInjector abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog

    @ContributesAndroidInjector abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog
    @ContributesAndroidInjector abstract fun contributesFillDialog(): FillDialog
    @ContributesAndroidInjector abstract fun contributesInsulinDialog(): InsulinDialog
    @ContributesAndroidInjector abstract fun contributesLoopDialog(): LoopDialog
    @ContributesAndroidInjector abstract fun contributesObjectivesExamDialog(): ObjectivesExamDialog
    @ContributesAndroidInjector abstract fun contributesProfileSwitchDialog(): ProfileSwitchDialog
    @ContributesAndroidInjector abstract fun contributesTempBasalDialog(): TempBasalDialog
    @ContributesAndroidInjector abstract fun contributesTempTargetDialog(): TempTargetDialog
    @ContributesAndroidInjector abstract fun contributesTreatmentDialog(): TreatmentDialog
    @ContributesAndroidInjector abstract fun contributesWizardDialog(): WizardDialog
    @ContributesAndroidInjector abstract fun contributesWizardInfoDialog(): WizardInfoDialog
    @ContributesAndroidInjector abstract fun contributesNtpProgressDialog(): NtpProgressDialog
    @ContributesAndroidInjector abstract fun contributesPasswordCheck(): PasswordCheck
}