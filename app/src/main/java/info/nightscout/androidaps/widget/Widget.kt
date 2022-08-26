package info.nightscout.androidaps.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.widget.RemoteViews
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.extensions.directionToIcon
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.extensions.valueToUnitsString
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.TrendCalculator
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

/**
 * Implementation of App Widget functionality.
 */
class Widget : AppWidgetProvider() {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var sp: SP
    @Inject lateinit var constraintChecker: ConstraintChecker

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val intentAction = "OpenApp"

    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        aapsLogger.debug(LTag.WIDGET, "updateAppWidget called")

        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val alpha = sp.getInt(WidgetConfigureActivity.PREF_PREFIX_KEY + appWidgetId, WidgetConfigureActivity.DEFAULT_OPACITY)

        // Create an Intent to launch MainActivity when clicked
        val intent = Intent(context, MainActivity::class.java).also { it.action = intentAction }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Widgets allow click handlers to only launch pending intents
        views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
        views.setInt(R.id.widget_layout, "setBackgroundColor", Color.argb(alpha, 0, 0, 0))

        handler.post {
            updateBg(views)
            updateTemporaryBasal(views)
            updateExtendedBolus(views)
            updateIobCob(views)
            updateTemporaryTarget(views)
            updateProfile(views)
            updateSensitivity(views)
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateBg(views: RemoteViews) {
        val units = profileFunction.getUnits()
        views.setTextViewText(R.id.bg, overviewData.lastBg?.valueToUnitsString(units) ?: rh.gs(R.string.notavailable))
        views.setTextColor(
            R.id.bg, when {
                overviewData.isLow  -> rh.gc(R.color.widget_low)
                overviewData.isHigh -> rh.gc(R.color.widget_high)
                else                -> rh.gc(R.color.widget_inrange)
            }
        )
        views.setImageViewResource(R.id.arrow, trendCalculator.getTrendArrow(overviewData.lastBg).directionToIcon())
        views.setInt(
            R.id.arrow, "setColorFilter", when {
                overviewData.isLow  -> rh.gc(R.color.widget_low)
                overviewData.isHigh -> rh.gc(R.color.widget_high)
                else                -> rh.gc(R.color.widget_inrange)
            }
        )

        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus != null) {
            views.setTextViewText(R.id.delta, Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units))
            views.setTextViewText(R.id.avg_delta, Profile.toSignedUnitsString(glucoseStatus.shortAvgDelta, glucoseStatus.shortAvgDelta * Constants.MGDL_TO_MMOLL, units))
            views.setTextViewText(R.id.long_avg_delta, Profile.toSignedUnitsString(glucoseStatus.longAvgDelta, glucoseStatus.longAvgDelta * Constants.MGDL_TO_MMOLL, units))
        } else {
            views.setTextViewText(R.id.delta, rh.gs(R.string.notavailable))
            views.setTextViewText(R.id.avg_delta, rh.gs(R.string.notavailable))
            views.setTextViewText(R.id.long_avg_delta, rh.gs(R.string.notavailable))
        }

        // strike through if BG is old
        if (!overviewData.isActualBg) views.setInt(R.id.bg, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
        else views.setInt(R.id.bg, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)

        views.setTextViewText(R.id.time_ago, dateUtil.minAgo(rh, overviewData.lastBg?.timestamp))
        views.setTextViewText(R.id.time_ago_short, "(" + dateUtil.minAgoShort(overviewData.lastBg?.timestamp) + ")")
    }

    private fun updateTemporaryBasal(views: RemoteViews) {
        views.setTextViewText(R.id.base_basal, overviewData.temporaryBasalText(iobCobCalculator))
        views.setTextColor(R.id.base_basal, iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { rh.gc(R.color.widget_basal) }
            ?: rh.gc(R.color.white))
        views.setImageViewResource(R.id.base_basal_icon, overviewData.temporaryBasalIcon(iobCobCalculator))
    }

    private fun updateExtendedBolus(views: RemoteViews) {
        val pump = activePlugin.activePump
        views.setTextViewText(R.id.extended_bolus, overviewData.extendedBolusText(iobCobCalculator))
        views.setViewVisibility(R.id.extended_layout, (iobCobCalculator.getExtendedBolus(dateUtil.now()) != null && !pump.isFakingTempsByExtendedBoluses).toVisibility())
    }

    private fun updateIobCob(views: RemoteViews) {
        views.setTextViewText(R.id.iob, overviewData.iobText(iobCobCalculator))
        // cob
        var cobText = overviewData.cobInfo(iobCobCalculator).displayText(rh, dateUtil, isDev = false) ?: rh.gs(R.string.value_unavailable_short)

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                //only display carbsreq when carbs have not been entered recently
                if (overviewData.lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += " | " + constraintsProcessed.carbsReq + " " + rh.gs(R.string.required)
                }
            }
        }
        views.setTextViewText(R.id.cob, cobText)
    }

    private fun updateTemporaryTarget(views: RemoteViews) {
        val units = profileFunction.getUnits()
        val tempTarget = overviewData.temporaryTarget
        if (tempTarget != null) {
            // this is crashing, use background as text for now
            //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextWarning))
            //views.setInt(R.id.temp_target, "setBackgroundColor", rh.gc(R.color.ribbonWarning))
            views.setTextColor(R.id.temp_target, rh.gc(R.color.widget_ribbonWarning))
            views.setTextViewText(R.id.temp_target, Profile.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units) + " " + dateUtil.untilString(tempTarget.end, rh))
        } else {
            // If the target is not the same as set in the profile then oref has overridden it
            profileFunction.getProfile()?.let { profile ->
                val targetUsed = loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0

                if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                    aapsLogger.debug("Adjusted target. Profile: ${profile.getTargetMgdl()} APS: $targetUsed")
                    views.setTextViewText(R.id.temp_target, Profile.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units))
                    // this is crashing, use background as text for now
                    //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextWarning))
                    //views.setInt(R.id.temp_target, "setBackgroundResource", rh.gc(R.color.tempTargetBackground))
                    views.setTextColor(R.id.temp_target, rh.gc(R.color.widget_ribbonWarning))
                } else {
                    // this is crashing, use background as text for now
                    //views.setTextColor(R.id.temp_target, rh.gc(R.color.ribbonTextDefault))
                    //views.setInt(R.id.temp_target, "setBackgroundColor", rh.gc(R.color.ribbonDefault))
                    views.setTextColor(R.id.temp_target, rh.gc(R.color.widget_ribbonTextDefault))
                    views.setTextViewText(R.id.temp_target, Profile.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units))
                }
            }
        }
    }

    fun updateProfile(views: RemoteViews) {
        val profileTextColor =
            profileFunction.getProfile()?.let {
                if (it is ProfileSealed.EPS) {
                    if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                        rh.gc(R.color.widget_ribbonWarning)
                    else rh.gc(R.color.widget_ribbonTextDefault)
                } else if (it is ProfileSealed.PS) {
                    rh.gc(R.color.widget_ribbonTextDefault)
                } else {
                    rh.gc(R.color.widget_ribbonTextDefault)
                }
            } ?: rh.gc(R.color.widget_ribbonCritical)

        views.setTextViewText(R.id.active_profile, profileFunction.getProfileNameWithRemainingTime())
        // this is crashing, use background as text for now
        //views.setInt(R.id.active_profile, "setBackgroundColor", profileBackgroundColor)
        //views.setTextColor(R.id.active_profile, profileTextColor)
        views.setTextColor(R.id.active_profile, profileTextColor)
    }

    private fun updateSensitivity(views: RemoteViews) {
        if (sp.getBoolean(R.string.key_openapsama_useautosens, false) && constraintChecker.isAutosensModeEnabled().value())
            views.setImageViewResource(R.id.sensitivity_icon, R.drawable.ic_swap_vert_black_48dp_green)
        else
            views.setImageViewResource(R.id.sensitivity_icon, R.drawable.ic_x_swap_vert)
        views.setTextViewText(R.id.sensitivity, overviewData.lastAutosensData(iobCobCalculator)?.let { autosensData ->
            String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100)
        } ?: "")

        // Show variable sensitivity
        val request = loop.lastRun?.request
        if (request is DetermineBasalResultSMB) {
            val isfMgdl = profileFunction.getProfile()?.getIsfMgdl()
            val variableSens = request.variableSens
            if (variableSens != isfMgdl && variableSens != null && isfMgdl != null) {
                views.setTextViewText(
                    R.id.variable_sensitivity,
                    String.format(
                        Locale.getDefault(), "%1$.1f→%2$.1f",
                        Profile.toUnits(isfMgdl, isfMgdl * Constants.MGDL_TO_MMOLL, profileFunction.getUnits()),
                        Profile.toUnits(variableSens, variableSens * Constants.MGDL_TO_MMOLL, profileFunction.getUnits())
                    )
                )
                views.setViewVisibility(R.id.variable_sensitivity, View.VISIBLE)
            } else views.setViewVisibility(R.id.variable_sensitivity, View.GONE)
        } else views.setViewVisibility(R.id.variable_sensitivity, View.GONE)
    }
}

internal fun updateWidget(context: Context) {
    context.sendBroadcast(Intent().also {
        it.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, Widget::class.java)))
        it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    })
}