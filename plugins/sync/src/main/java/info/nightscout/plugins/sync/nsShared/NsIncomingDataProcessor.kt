package info.nightscout.plugins.sync.nsShared

import info.nightscout.annotations.OpenForTesting
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.nsclient.NSSgv
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profile.ProfileSource
import info.nightscout.interfaces.source.NSClientSource
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsclient.extensions.fromJson
import info.nightscout.plugins.sync.nsclientV3.extensions.toBolus
import info.nightscout.plugins.sync.nsclientV3.extensions.toBolusCalculatorResult
import info.nightscout.plugins.sync.nsclientV3.extensions.toCarbs
import info.nightscout.plugins.sync.nsclientV3.extensions.toEffectiveProfileSwitch
import info.nightscout.plugins.sync.nsclientV3.extensions.toExtendedBolus
import info.nightscout.plugins.sync.nsclientV3.extensions.toFood
import info.nightscout.plugins.sync.nsclientV3.extensions.toOfflineEvent
import info.nightscout.plugins.sync.nsclientV3.extensions.toProfileSwitch
import info.nightscout.plugins.sync.nsclientV3.extensions.toTemporaryBasal
import info.nightscout.plugins.sync.nsclientV3.extensions.toTemporaryTarget
import info.nightscout.plugins.sync.nsclientV3.extensions.toTherapyEvent
import info.nightscout.plugins.sync.nsclientV3.extensions.toTransactionGlucoseValue
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.food.NSFood
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSOfflineEvent
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class NsIncomingDataProcessor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val nsClientSource: NSClientSource,
    private val sp: SP,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val storeDataForDb: StoreDataForDb,
    private val config: Config,
    private val instantiator: Instantiator,
    private val profileSource: ProfileSource
) {

    private fun toGv(jsonObject: JSONObject): TransactionGlucoseValue? {
        val sgv = NSSgv(jsonObject)
        return TransactionGlucoseValue(
            timestamp = sgv.mills ?: return null,
            value = sgv.mgdl?.toDouble() ?: return null,
            noise = null,
            raw = sgv.filtered?.toDouble(),
            trendArrow = GlucoseValue.TrendArrow.fromString(sgv.direction),
            nightscoutId = sgv.id,
            sourceSensor = GlucoseValue.SourceSensor.fromString(sgv.device)
        )
    }

    @Suppress("SpellCheckingInspection")
    fun processSgvs(sgvs: Any) {

        if (!nsClientSource.isEnabled() && !sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_cgm, false)) return

        var latestDateInReceivedData: Long = 0
        aapsLogger.debug(LTag.NSCLIENT, "Received NS Data: $sgvs")
        val glucoseValues = mutableListOf<TransactionGlucoseValue>()

        if (sgvs is JSONArray) { // V1 client
            for (i in 0 until sgvs.length()) {
                val sgv = toGv(sgvs.getJSONObject(i)) ?: continue
                if (sgv.timestamp < dateUtil.now() && sgv.timestamp > latestDateInReceivedData) latestDateInReceivedData = sgv.timestamp
                glucoseValues += sgv
            }
        } else if (sgvs is List<*>) { // V3 client

            for (i in 0 until sgvs.size) {
                val sgv = (sgvs[i] as NSSgvV3).toTransactionGlucoseValue()
                if (sgv.timestamp < dateUtil.now() && sgv.timestamp > latestDateInReceivedData) latestDateInReceivedData = sgv.timestamp
                glucoseValues += sgv
            }
        }
        activePlugin.activeNsClient?.updateLatestBgReceivedIfNewer(latestDateInReceivedData)
        // Was that sgv more less 5 mins ago ?
        if (T.msecs(dateUtil.now() - latestDateInReceivedData).mins() < 5L) {
            rxBus.send(EventDismissNotification(Notification.NS_ALARM))
            rxBus.send(EventDismissNotification(Notification.NS_URGENT_ALARM))
        }
        storeDataForDb.glucoseValues.addAll(glucoseValues)
    }

    fun processTreatments(treatments: List<NSTreatment>) {
        try {
            var latestDateInReceivedData: Long = 0
            for (treatment in treatments) {
                aapsLogger.debug(LTag.DATABASE, "Received NS treatment: $treatment")
                val date = treatment.date ?: continue
                if (date > latestDateInReceivedData) latestDateInReceivedData = date

                when (treatment) {
                    is NSBolus                  ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_insulin, false) || config.NSCLIENT)
                            storeDataForDb.boluses.add(treatment.toBolus())

                    is NSCarbs                  ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_carbs, false) || config.NSCLIENT)
                            storeDataForDb.carbs.add(treatment.toCarbs())

                    is NSTemporaryTarget        ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_temp_target, false) || config.NSCLIENT) {
                            if (treatment.duration > 0L) {
                                // not ending event
                                if (treatment.targetBottomAsMgdl() < Constants.MIN_TT_MGDL
                                    || treatment.targetBottomAsMgdl() > Constants.MAX_TT_MGDL
                                    || treatment.targetTopAsMgdl() < Constants.MIN_TT_MGDL
                                    || treatment.targetTopAsMgdl() > Constants.MAX_TT_MGDL
                                    || treatment.targetBottomAsMgdl() > treatment.targetTopAsMgdl()
                                ) {
                                    aapsLogger.debug(LTag.DATABASE, "Ignored TemporaryTarget $treatment")
                                    continue
                                }
                            }
                            storeDataForDb.temporaryTargets.add(treatment.toTemporaryTarget())
                        }

                    is NSTemporaryBasal         ->
                        if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                            storeDataForDb.temporaryBasals.add(treatment.toTemporaryBasal())

                    is NSEffectiveProfileSwitch ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                            treatment.toEffectiveProfileSwitch(dateUtil)?.let { effectiveProfileSwitch ->
                                storeDataForDb.effectiveProfileSwitches.add(effectiveProfileSwitch)
                            }
                        }

                    is NSProfileSwitch          ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                            treatment.toProfileSwitch(activePlugin, dateUtil)?.let { profileSwitch ->
                                storeDataForDb.profileSwitches.add(profileSwitch)
                            }
                        }

                    is NSBolusWizard            ->
                        treatment.toBolusCalculatorResult()?.let { bolusCalculatorResult ->
                            storeDataForDb.bolusCalculatorResults.add(bolusCalculatorResult)
                        }

                    is NSTherapyEvent           ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
                            treatment.toTherapyEvent().let { therapyEvent ->
                                storeDataForDb.therapyEvents.add(therapyEvent)
                            }

                    is NSOfflineEvent           ->
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_offline_event, false) && config.isEngineeringMode() || config.NSCLIENT)
                            treatment.toOfflineEvent().let { offlineEvent ->
                                storeDataForDb.offlineEvents.add(offlineEvent)
                            }

                    is NSExtendedBolus          ->
                        if (config.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT)
                            treatment.toExtendedBolus().let { extendedBolus ->
                                storeDataForDb.extendedBoluses.add(extendedBolus)
                            }
                }
            }
            activePlugin.activeNsClient?.updateLatestTreatmentReceivedIfNewer(latestDateInReceivedData)
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
        }
    }

    fun processFood(data: Any) {
        aapsLogger.debug(LTag.DATABASE, "Received Food Data: $data")

        try {
            val foods = mutableListOf<Food>()
            if (data is JSONArray) {
                for (index in 0 until data.length()) {
                    val jsonFood: JSONObject = data.getJSONObject(index)

                    if (JsonHelper.safeGetString(jsonFood, "type") != "food") continue

                    when (JsonHelper.safeGetString(jsonFood, "action")) {
                        "remove" -> {
                            val delFood = Food(
                                name = "",
                                portion = 0.0,
                                carbs = 0,
                                isValid = false
                            ).also { it.interfaceIDs.nightscoutId = JsonHelper.safeGetString(jsonFood, "_id") }
                            foods += delFood
                        }

                        else     -> {
                            val food = Food.fromJson(jsonFood)
                            if (food != null) foods += food
                            else aapsLogger.error(LTag.DATABASE, "Error parsing food", jsonFood.toString())
                        }
                    }
                }
            } else if (data is List<*>) {
                for (i in 0 until data.size)
                    foods += (data[i] as NSFood).toFood()
            }
            storeDataForDb.foods.addAll(foods)
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            rxBus.send(EventNSClientNewLog("◄ ERROR", error.localizedMessage))
        }
    }

    fun processProfile(profileJson: JSONObject) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_profile_store, true) || config.NSCLIENT) {
            val store = instantiator.provideProfileStore(profileJson)
            val createdAt = store.getStartDate()
            val lastLocalChange = sp.getLong(info.nightscout.core.utils.R.string.key_local_profile_last_change, 0)
            aapsLogger.debug(LTag.PROFILE, "Received profileStore: createdAt: $createdAt Local last modification: $lastLocalChange")
            if (createdAt > lastLocalChange || createdAt % 1000 == 0L) { // whole second means edited in NS
                profileSource.loadFromStore(store)
                activePlugin.activeNsClient?.dataSyncSelector?.profileReceived(store.getStartDate())
                aapsLogger.debug(LTag.PROFILE, "Received profileStore: $profileJson")
            }
        }
    }
}