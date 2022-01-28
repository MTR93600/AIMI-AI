package info.nightscout.androidaps.plugin.general.openhumans

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugin.general.openhumans.delegates.OHAppIDDelegate
import info.nightscout.androidaps.plugin.general.openhumans.delegates.OHCounterDelegate
import info.nightscout.androidaps.plugin.general.openhumans.delegates.OHStateDelegate
import info.nightscout.androidaps.plugin.general.openhumans.ui.OHFragment
import info.nightscout.androidaps.plugin.general.openhumans.ui.OHLoginActivity
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenHumansUploader @Inject internal constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val context: Context,
    private val repository: AppRepository,
    private val openHumansAPI: OpenHumansAPI,
    stateDelegate: OHStateDelegate,
    counterDelegate: OHCounterDelegate,
    appIdDelegate: OHAppIDDelegate,
    private val rxBus: RxBus
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginIcon(R.drawable.open_humans_white)
        .pluginName(R.string.open_humans)
        .shortName(R.string.open_humans_short)
        .description(R.string.open_humans_description)
        .preferencesId(R.xml.pref_openhumans)
        .fragmentClass(OHFragment::class.qualifiedName),
    aapsLogger, rh, injector
) {

    private var openHumansState by stateDelegate
    private var uploadCounter by counterDelegate
    private val appId by appIdDelegate

    private val preferenceChangeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        setupNotificationChannels()
        if (openHumansState != null) scheduleWorker(false)
        preferenceChangeDisposable += rxBus.toObservable(EventPreferenceChange::class.java).subscribe {
            onSharedPreferenceChanged(it)
        }
    }

    override fun onStop() {
        super.onStop()
        cancelWorker()
        preferenceChangeDisposable.clear()
    }

    private fun onSharedPreferenceChanged(event: EventPreferenceChange) {
        if (event.changedKey in arrayOf("key_oh_charging_only", "key_oh_wifi_only")  && openHumansState != null) scheduleWorker(true)
    }

    suspend fun login(bearerToken: String) = withContext(Dispatchers.IO) {
        try {
            val oAuthTokens = openHumansAPI.exchangeBearerToken(bearerToken)
            val projectMemberId = openHumansAPI.getProjectMemberId(oAuthTokens.accessToken)
            withContext(Dispatchers.Main) {
                openHumansState = OpenHumansState(
                    accessToken = oAuthTokens.accessToken,
                    refreshToken = oAuthTokens.refreshToken,
                    expiresAt = oAuthTokens.expiresAt,
                    projectMemberId = projectMemberId,
                    uploadOffset = 0L
                )
            }
            scheduleWorker(false)
        } catch (e: Exception) {
            aapsLogger.error("Error while logging in to Open Humans", e)
            throw e
        }
    }

    private fun String.sha256(): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(toByteArray())
        return messageDigest.digest().toHexString()
    }

    private fun <T : TraceableDBEntry> ZipOutputStream.writeDBEntryFile(name: String, list: List<T>, block: JSONObject.(entry: T) -> Unit) = writeJSONArrayFile(name, list) {
        put("structureVersion", 2)
        put("id", it.id)
        put("version", it.version)
        put("dateCreated", it.dateCreated)
        put("isValid", it)
        put("referenceId", it.referenceId)
        put("pumpType", it.interfaceIDs.pumpType)
        put("pumpSerialHash", it.interfaceIDs.pumpSerial?.sha256())
        put("pumpId", it.interfaceIDs.pumpId)
        put("startId", it.interfaceIDs.startId)
        put("endId", it.interfaceIDs.endId)
        block(it)
    }

    private fun <T> ZipOutputStream.writeJSONArrayFile(name: String, list: List<T>, block: JSONObject.(entry: T) -> Unit) {
        val jsonArray = JSONArray()
        list.forEach { entry ->
            val jsonObject = JSONObject()
            jsonObject.block(entry)
            jsonArray.put(jsonObject)
        }
        writeFile(name, jsonArray.toString().toByteArray())
    }

    private fun ZipOutputStream.writeFile(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun List<Block>.serialize(): JSONArray {
        val jsonArray = JSONArray()
        forEach {
            val jsonObject = JSONObject()
            jsonObject.put("duration", it.duration)
            jsonObject.put("amount", it.amount)
            jsonArray.put(jsonObject)
        }
        return jsonArray
    }

    internal suspend fun uploadData() {
        try {
            withContext(Dispatchers.Default) {
                val timestamp = System.currentTimeMillis()
                val offset = openHumansState!!.uploadOffset
                var page = 0
                while (uploadDataPaged(offset, timestamp, page++));
                withContext(Dispatchers.Main) {
                    openHumansState = openHumansState!!.copy(uploadOffset = timestamp)
                }
            }
        } catch (e: OpenHumansAPI.OHHttpException) {
            if (e.code == 401 && e.detail == "Invalid token.") {
                withContext(NonCancellable) {
                    handleSignOut()
                }
            }
        }
    }

    private suspend fun uploadDataPaged(since: Long, until: Long, page: Int): Boolean {
        val data = repository
            .collectNewEntriesSince(since, until, 1000, 1000 * page)
            .let { data -> data.copy(preferencesChanges = data.preferencesChanges.filter { it.key.isAllowedKey() }) }
        val hasData = with(data) {
            apsResults.isNotEmpty() ||
                apsResultLinks.isNotEmpty() ||
                bolusCalculatorResults.isNotEmpty() ||
                boluses.isNotEmpty() ||
                carbs.isNotEmpty() ||
                effectiveProfileSwitches.isNotEmpty() ||
                extendedBoluses.isNotEmpty() ||
                glucoseValues.isNotEmpty() ||
                multiwaveBolusLinks.isNotEmpty() ||
                offlineEvents.isNotEmpty() ||
                preferencesChanges.isNotEmpty() ||
                profileSwitches.isNotEmpty() ||
                temporaryBasals.isNotEmpty() ||
                temporaryTarget.isNotEmpty() ||
                therapyEvents.isNotEmpty() ||
                totalDailyDoses.isNotEmpty() ||
                versionChanges.isNotEmpty()
        }
        if (!hasData) return false

        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        val tags = mutableListOf<String>()

        val applicationInfo = JSONObject()
        //TODO: Move build configuration to core module
        /*applicationInfo.put("versionName", BuildConfig.VERSION_NAME)
        applicationInfo.put("versionCode", BuildConfig.VERSION_CODE)
        val hasGitInfo = !BuildConfig.HEAD.endsWith("NoGitSystemAvailable", true)
        val customRemote = !BuildConfig.REMOTE.equals("https://github.com/nightscout/AndroidAPS.git", true)
        applicationInfo.put("hasGitInfo", hasGitInfo)
        applicationInfo.put("customRemote", customRemote)*/
        applicationInfo.put("applicationId", appId.toString())
        zos.writeFile("ApplicationInfo.json", applicationInfo.toString().toByteArray())
        tags.add("ApplicationInfo")

        val deviceInfo = JSONObject()
        deviceInfo.put("brand", android.os.Build.BRAND)
        deviceInfo.put("device", android.os.Build.DEVICE)
        deviceInfo.put("manufacturer", android.os.Build.MANUFACTURER)
        deviceInfo.put("model", android.os.Build.MODEL)
        deviceInfo.put("product", android.os.Build.PRODUCT)
        zos.writeFile("DeviceInfo.json", deviceInfo.toString().toByteArray())
        tags.add("DeviceInfo")

        val displayMetrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
            context.display?.getRealMetrics(displayMetrics)
        else
            @Suppress("DEPRECATION") (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(displayMetrics)

        val displayInfo = JSONObject()
        displayInfo.put("height", displayMetrics.heightPixels)
        displayInfo.put("width", displayMetrics.widthPixels)
        displayInfo.put("density", displayMetrics.density)
        displayInfo.put("scaledDensity", displayMetrics.scaledDensity)
        displayInfo.put("xdpi", displayMetrics.xdpi)
        displayInfo.put("ydpi", displayMetrics.ydpi)
        zos.writeFile("DisplayInfo.json", displayInfo.toString().toByteArray())
        tags.add("DisplayInfo")

        val uploadNumber = this.uploadCounter++
        val uploadDate = Date()
        val uploadInfo = JSONObject()
        uploadInfo.put("fileVersion", 2)
        uploadInfo.put("counter", uploadNumber)
        uploadInfo.put("timestamp", until)
        uploadInfo.put("utcOffset", TimeZone.getDefault().getOffset(uploadDate.time))
        zos.writeFile("UploadInfo.json", uploadInfo.toString().toByteArray())
        tags.add("UploadInfo")

        if (data.apsResults.isNotEmpty()) {
            zos.writeDBEntryFile("APSResults.json", data.apsResults) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("algorithm", it.algorithm.toString())
                put("glucoseStatus", JSONObject(it.glucoseStatusJson))
                put("currentTemp", JSONObject(it.currentTempJson))
                put("iobData", JSONObject(it.iobDataJson))
                put("profile", JSONObject(it.profileJson))
                put("autosensData", JSONObject(it.autosensDataJson ?: ""))
                put("mealData", JSONObject(it.mealDataJson))
                put("isMicroBolusAllowed", it.isMicroBolusAllowed)
                put("result", JSONObject(it.resultJson))
            }
            tags.add("APSResults")
        }

        if (data.apsResultLinks.isNotEmpty()) {
            zos.writeDBEntryFile("APSResultLinks.json", data.apsResultLinks) {
                put("apsResultId", it.apsResultId)
                put("smbId", it.smbId)
                put("tbrId", it.tbrId)
            }
            tags.add("APSResultLinks")
        }

        if (data.bolusCalculatorResults.isNotEmpty()) {
            zos.writeDBEntryFile("BolusCalculatorResults.json", data.bolusCalculatorResults) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("targetBGLow", it.targetBGLow)
                put("targetBGHigh", it.targetBGHigh)
                put("isf", it.isf)
                put("ic", it.ic)
                put("bolusIOB", it.bolusIOB)
                put("wasBolusIOBUsed", it.wasBolusIOBUsed)
                put("basalIOB", it.basalIOB)
                put("wasBasalIOBUsed", it.wasBasalIOBUsed)
                put("glucoseValue", it.glucoseValue)
                put("wasGlucoseUsed", it.wasGlucoseUsed)
                put("glucoseDifference", it.glucoseDifference)
                put("glucoseInsulin", it.glucoseInsulin)
                put("glucoseTrend", it.glucoseTrend)
                put("wasTrendUsed", it.wasTrendUsed)
                put("trendInsulin", it.trendInsulin)
                put("cob", it.cob)
                put("wasCOBUsed", it.wasCOBUsed)
                put("cobInsulin", it.cobInsulin)
                put("carbs", it.carbs)
                put("wereCarbsUsed", it.wereCarbsUsed)
                put("carbsInsulin", it.carbsInsulin)
                put("otherCorrection", it.otherCorrection)
                put("wasSuperbolusUsed", it.wasSuperbolusUsed)
                put("superbolusInsulin", it.superbolusInsulin)
                put("wasTempTargetUsed", it.wasTempTargetUsed)
                put("totalInsulin", it.totalInsulin)
                put("percentageCorrection", it.percentageCorrection)
            }
            tags.add("BolusCalculatorResults")
        }

        if (data.boluses.isNotEmpty()) {
            zos.writeDBEntryFile("Boluses.json", data.boluses) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("amount", it.amount)
                put("type", it.type.toString())
                put("isBasalInsulin", it.isBasalInsulin)
                put("insulinEndTime", it.insulinConfiguration?.insulinEndTime)
                put("peak", it.insulinConfiguration?.peak)
            }
            tags.add("Boluses")
        }

        if (data.carbs.isNotEmpty()) {
            zos.writeDBEntryFile("Carbs.json", data.carbs) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("duration", it.duration)
                put("amount", it.amount)
            }
            tags.add("Carbs")
        }

        if (data.effectiveProfileSwitches.isNotEmpty()) {
            zos.writeDBEntryFile("EffectiveProfileSwitches.json", data.effectiveProfileSwitches) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("basalBlocks", it.basalBlocks.serialize())
                put("isfBlocks", it.isfBlocks.serialize())
                put("icBlocks", it.icBlocks.serialize())
                put("icBlocks", it.icBlocks.serialize())
                val targetBlocks = JSONArray()
                it.targetBlocks.forEach { block ->
                    val jsonObject = JSONObject()
                    jsonObject.put("duration", block.duration)
                    jsonObject.put("lowTarget", block.lowTarget)
                    jsonObject.put("highTarget", block.highTarget)
                    targetBlocks.put(jsonObject)
                }
                put("targetBlocks", it.targetBlocks)
                put("glucoseUnit", it.glucoseUnit.toString())
                put("originalTimeshift", it.originalTimeshift)
                put("originalPercentage", it.originalPercentage)
                put("originalDuration", it.originalDuration)
                put("originalEnd", it.originalEnd)
                put("insulinEndTime", it.insulinConfiguration.insulinEndTime)
                put("insulinEndTime", it.insulinConfiguration.peak)
            }
            tags.add("EffectiveProfileSwitches")
        }

        if (data.extendedBoluses.isNotEmpty()) {
            zos.writeDBEntryFile("ExtendedBoluses.json", data.extendedBoluses) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("duration", it.duration)
                put("amount", it.amount)
                put("isEmulatingTempBasal", it.isEmulatingTempBasal)
            }
            tags.add("ExtendedBoluses")
        }

        if (data.glucoseValues.isNotEmpty()) {
            zos.writeDBEntryFile("GlucoseValues.json", data.glucoseValues) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("raw", it.raw)
                put("value", it.value)
                put("trendArrow", it.trendArrow.toString())
                put("noise", it.noise)
                put("sourceSensor", it.sourceSensor.toString())
            }
            tags.add("GlucoseValues")
        }

        if (data.multiwaveBolusLinks.isNotEmpty()) {
            zos.writeDBEntryFile("MultiwaveBolusLinks.json", data.multiwaveBolusLinks) {
                put("bolusId", it.bolusId)
                put("extendedBolusId", it.extendedBolusId)
            }
            tags.add("MultiwaveBolusLinks")
        }

        if (data.offlineEvents.isNotEmpty()) {
            zos.writeDBEntryFile("OfflineEvents.json", data.offlineEvents) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("reason", it.reason.toString())
                put("duration", it.duration)
            }
            tags.add("OfflineEvents")
        }

        if (data.preferencesChanges.isNotEmpty()) {
            zos.writeJSONArrayFile("PreferenceChanges.json", data.preferencesChanges) {
                put("structureVersion", 2)
                put("id", it.id)
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("structureVersion", 2)
                put("key", it.key)
                put("value", it.value)
            }
            tags.add("PreferenceChanges")
        }

        if (data.profileSwitches.isNotEmpty()) {
            zos.writeDBEntryFile("ProfileSwitches.json", data.profileSwitches) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("basalBlocks", it.basalBlocks.serialize())
                put("isfBlocks", it.basalBlocks.serialize())
                put("icBlocks", it.icBlocks.serialize())
                put("basalBlocks", it.basalBlocks.serialize())
                val targetBlocks = JSONArray()
                it.targetBlocks.forEach { block ->
                    val jsonObject = JSONObject()
                    jsonObject.put("duration", block.duration)
                    jsonObject.put("lowTarget", block.lowTarget)
                    jsonObject.put("highTarget", block.highTarget)
                    targetBlocks.put(jsonObject)
                }
                put("glucoseUnit", it.glucoseUnit.toString())
                put("timeshift", it.timeshift)
                put("percentage", it.percentage)
                put("duration", it.duration)
                put("insulinEndTime", it.insulinConfiguration.insulinEndTime)
                put("peak", it.insulinConfiguration.peak)
            }
            tags.add("ProfileSwitches")
        }

        if (data.temporaryBasals.isNotEmpty()) {
            zos.writeDBEntryFile("TemporaryBasals.json", data.temporaryBasals) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("type", it.type.toString())
                put("isAbsolute", it.isAbsolute)
                put("rate", it.rate)
                put("duration", it.duration)
            }
            tags.add("TemporaryBasals")
        }

        if (data.temporaryTarget.isNotEmpty()) {
            zos.writeDBEntryFile("TemporaryTargets.json", data.temporaryTarget) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("reason", it.reason.toString())
                put("highTarget", it.highTarget)
                put("lowTarget", it.lowTarget)
                put("duration", it.duration)
            }
            tags.add("TemporaryTargets")
        }

        if (data.therapyEvents.isNotEmpty()) {
            zos.writeDBEntryFile("TherapyEvents.json", data.therapyEvents) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("type", it.type.toString())
                put("glucose", it.glucose)
                put("glucoseType", it.glucoseType?.toString())
                put("glucoseUnit", it.glucoseUnit.toString())
            }
            tags.add("TherapyEvents")
        }

        if (data.totalDailyDoses.isNotEmpty()) {
            zos.writeDBEntryFile("TotalDailyDoses.json", data.totalDailyDoses) {
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("basalAmount", it.basalAmount)
                put("bolusAmount", it.bolusAmount)
                put("totalAmount", it.totalAmount)
                put("carbs", it.carbs)
            }
            tags.add("TotalDailyDoses")
        }

        if (data.versionChanges.isNotEmpty()) {
            zos.writeJSONArrayFile("VersionChanges.json", data.versionChanges) {
                put("structureVersion", 2)
                put("id", it.id)
                put("timestamp", it.timestamp)
                put("utcOffset", it.utcOffset)
                put("versionCode", it.versionCode)
                put("versionName", it.versionName)
                val customGitRemote = it.gitRemote != "https://github.com/nightscout/AndroidAPS.git"
                put("customGitRemote", customGitRemote)
                put("commitHash", if (customGitRemote) null else it.commitHash)
            }
            tags.add("VersionChanges")
        }

        zos.close()
        val bytes = baos.toByteArray()

        val fileName = "upload-num$uploadNumber-ver2-date${FILE_NAME_DATE_FORMAT.format(uploadDate)}-appid${appId.toString().replace("-", "")}.zip"

        val metaData = OpenHumansAPI.FileMetadata(
            tags = tags,
            description = "AndroidAPS Database Upload",
            md5 = MessageDigest.getInstance("MD5").digest(bytes).toHexString(),
            creationDate = uploadDate.time
        )

        refreshAccessTokenIfNeeded()

        val preparedUpload = openHumansAPI.prepareFileUpload(openHumansState!!.accessToken, fileName, metaData)
        openHumansAPI.uploadFile(preparedUpload.uploadURL, bytes)
        openHumansAPI.completeFileUpload(openHumansState!!.accessToken, preparedUpload.fileId)

        return true
    }

    private fun cancelWorker() = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)

    private fun scheduleWorker(replace: Boolean, delay: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (sp.getBoolean("key_oh_wifi_only", true)) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(sp.getBoolean("key_oh_charging_only", false))
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<OpenHumansWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.MINUTES)
            .setInitialDelay(if (delay) 1 else 0, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    internal fun uploadNow() {
        val workRequest = OneTimeWorkRequestBuilder<OpenHumansWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, workRequest)
        scheduleWorker(replace = true, delay = true)
    }

    private fun ByteArray.toHexString(): String {
        val stringBuilder = StringBuilder()
        map { it.toInt() }.forEach {
            stringBuilder.append(HEX_DIGITS[(it shr 4) and 0x0F])
            stringBuilder.append(HEX_DIGITS[it and 0x0F])
        }
        return stringBuilder.toString()
    }

    private suspend fun refreshAccessTokenIfNeeded() {
        val state = openHumansState!!
        if (state.expiresAt <= System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) {
            val newTokens = openHumansAPI.refreshAccessToken(state.refreshToken)
            withContext(Dispatchers.Main) {
                openHumansState = state.copy(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken,
                    expiresAt = newTokens.expiresAt
                )
            }
        }
    }

    fun logout() {
        cancelWorker()
        openHumansState = null
    }

    private fun setupNotificationChannels() {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        notificationManagerCompat.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_WORKER,
                rh.gs(R.string.open_humans_uploading),
                NotificationManager.IMPORTANCE_MIN
            )
        )
        notificationManagerCompat.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_MESSAGES,
                rh.gs(R.string.open_humans_notifications),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private suspend fun handleSignOut() {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_MESSAGES)
            .setContentTitle(rh.gs(R.string.you_have_been_signed_out_of_open_humans))
            .setContentText(rh.gs(R.string.click_here_to_sign_in_again_if_this_wasnt_on_purpose))
            .setStyle(NotificationCompat.BigTextStyle())
            .setSmallIcon(R.drawable.open_humans_notification)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                context,
                0,
                Intent(context, OHLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                0
            ))
            .build()
        NotificationManagerCompat.from(context).notify(SIGNED_OUT_NOTIFICATION_ID, notification)
        withContext(Dispatchers.Main) {
            logout()
        }
    }

    internal fun createForegroundInfo(id: UUID): ForegroundInfo {
        val cancel = context.getString(R.string.cancel)

        val intent = WorkManager.getInstance(context)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_WORKER)
            .setContentTitle(context.getString(R.string.open_humans_uploading))
            .setContentText(context.getString(R.string.uploading_to_open_humans))
            .setSmallIcon(R.drawable.open_humans_notification)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return ForegroundInfo(UPLOAD_NOTIFICATION_ID, notification)
    }

    private companion object {
        val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
        @Suppress("PrivatePropertyName")
        private val FILE_NAME_DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        const val WORK_NAME_PERIODIC = "Open Humans Periodic"
        const val WORK_NAME_MANUAL = "Open Humans Manual"
        const val NOTIFICATION_CHANNEL_WORKER = "OpenHumansWorker"
        const val NOTIFICATION_CHANNEL_MESSAGES = "OpenHumansMessages"
        const val SIGNED_OUT_NOTIFICATION_ID = 3125
        const val UPLOAD_NOTIFICATION_ID = 3126
    }

}