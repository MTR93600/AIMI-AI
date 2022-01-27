package info.nightscout.androidaps.plugins.general.nsclient

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.extensions.toJson
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncSelectorImplementation @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val nsClientPlugin: NSClientPlugin,
    private val activePlugin: ActivePlugin,
    private val appRepository: AppRepository,
    private val localProfilePlugin: LocalProfilePlugin
) : DataSyncSelector {

    class QueueCounter(
        var bolusesRemaining: Long = -1L,
        var carbsRemaining: Long = -1L,
        var bcrRemaining: Long = -1L,
        var ttsRemaining: Long = -1L,
        var foodsRemaining: Long = -1L,
        var gvsRemaining: Long = -1L,
        var tesRemaining: Long = -1L,
        var dssRemaining: Long = -1L,
        var tbrsRemaining: Long = -1L,
        var ebsRemaining: Long = -1L,
        var pssRemaining: Long = -1L,
        var epssRemaining: Long = -1L,
        var oesRemaining: Long = -1L
    ) {

        fun size(): Long =
            bolusesRemaining +
                carbsRemaining +
                bcrRemaining +
                ttsRemaining +
                foodsRemaining +
                gvsRemaining +
                tesRemaining +
                dssRemaining +
                tbrsRemaining +
                ebsRemaining +
                pssRemaining +
                epssRemaining +
                oesRemaining
    }

    private val queueCounter = QueueCounter()

    override fun queueSize(): Long = queueCounter.size()

    override fun doUpload() {
        if (sp.getBoolean(R.string.key_ns_upload, true)) {
            processChangedBolusesCompat()
            processChangedCarbsCompat()
            processChangedBolusCalculatorResultsCompat()
            processChangedTemporaryBasalsCompat()
            processChangedExtendedBolusesCompat()
            processChangedProfileSwitchesCompat()
            processChangedEffectiveProfileSwitchesCompat()
            processChangedGlucoseValuesCompat()
            processChangedTempTargetsCompat()
            processChangedFoodsCompat()
            processChangedTherapyEventsCompat()
            processChangedDeviceStatusesCompat()
            processChangedOfflineEventsCompat()
            processChangedProfileStore()
        }
    }

    override fun resetToNextFullSync() {
        appRepository.getLastGlucoseValueIdWrapped().blockingGet().run {
            val currentLast = if (this is ValueWrapper.Existing) this.value else 0L
            sp.putLong(R.string.key_ns_glucose_value_new_data_id, currentLast)
        }
        sp.remove(R.string.key_ns_glucose_value_last_synced_id)

        appRepository.getLastTemporaryBasalIdWrapped().blockingGet().run {
            val currentLast = if (this is ValueWrapper.Existing) this.value else 0L
            sp.putLong(R.string.key_ns_temporary_basal_new_data_id, currentLast)
        }
        sp.remove(R.string.key_ns_temporary_basal_last_synced_id)

        appRepository.getLastTempTargetIdWrapped().blockingGet().run {
            val currentLast = if (this is ValueWrapper.Existing) this.value else 0L
            sp.putLong(R.string.key_ns_temporary_target_new_data_id, currentLast)
        }
        sp.remove(R.string.key_ns_temporary_target_last_synced_id)

        appRepository.getLastExtendedBolusIdWrapped().blockingGet().run {
            val currentLast = if (this is ValueWrapper.Existing) this.value else 0L
            sp.putLong(R.string.key_ns_extended_bolus_new_data_id, currentLast)
        }
        sp.remove(R.string.key_ns_extended_bolus_last_synced_id)

        sp.remove(R.string.key_ns_food_last_synced_id)
        sp.remove(R.string.key_ns_bolus_last_synced_id)
        sp.remove(R.string.key_ns_carbs_last_synced_id)
        sp.remove(R.string.key_ns_bolus_calculator_result_last_synced_id)
        sp.remove(R.string.key_ns_therapy_event_last_synced_id)
        sp.remove(R.string.key_ns_profile_switch_last_synced_id)
        sp.remove(R.string.key_ns_effective_profile_switch_last_synced_id)
        sp.remove(R.string.key_ns_offline_event_last_synced_id)
        sp.remove(R.string.key_ns_profile_store_last_synced_timestamp)

        val lastDeviceStatusDbIdWrapped = appRepository.getLastDeviceStatusIdWrapped().blockingGet()
        if (lastDeviceStatusDbIdWrapped is ValueWrapper.Existing) sp.putLong(R.string.key_ns_device_status_last_synced_id, lastDeviceStatusDbIdWrapped.value)
        else sp.remove(R.string.key_ns_device_status_last_synced_id)
    }

    override fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting Bolus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_bolus_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedBoluses(): List<Bolus> {
        val startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
        return appRepository.getModifiedBolusesDataFromId(startId)
            .blockingGet()
            .filter { it.type != Bolus.Type.PRIMING }
            .also {
                aapsLogger.debug(LTag.NSCLIENT, "Loading Bolus data for sync from $startId. Records ${it.size}")
            }
    }

    //private var lastBolusId = -1L
    //private var lastBolusTime = -1L
    override fun processChangedBolusesCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastBolusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_bolus_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastBolusId && dateUtil.now() - lastBolusTime < 5000) return false
        //lastBolusId = startId
        //lastBolusTime = dateUtil.now()
        queueCounter.bolusesRemaining = lastDbId - startId
        appRepository.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Bolus data Start: $startId ID: ${bolus.first.id} HistoryID: ${bolus.second.id} ")
            when {
                // only NsId changed, no need to upload
                bolus.first.onlyNsIdAdded(bolus.second)       -> {
                    confirmLastBolusIdIfGreater(bolus.second.id)
                    //lastBolusId = -1
                    processChangedBolusesCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Only NS id changed ID: ${bolus.first.id} HistoryID: ${bolus.second.id} ")
                    return false
                }
                // without nsId = create new
                bolus.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", bolus.first.toJson(true, dateUtil), DataSyncSelector.PairBolus(bolus.first, bolus.second.id), "$startId/$lastDbId")
                // with nsId = update
                bolus.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        bolus.first.interfaceIDs.nightscoutId,
                        bolus.first.toJson(false, dateUtil),
                        DataSyncSelector.PairBolus(bolus.first, bolus.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting Carbs data sync from $lastSynced")
            sp.putLong(R.string.key_ns_carbs_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedCarbs(): List<Carbs> {
        val startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
        return appRepository.getModifiedCarbsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading Carbs data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastCarbsId = -1L
    //private var lastCarbsTime = -1L
    override fun processChangedCarbsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastCarbsIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_carbs_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastCarbsId && dateUtil.now() - lastCarbsTime < 5000) return false
        //lastCarbsId = startId
        //lastCarbsTime = dateUtil.now()
        queueCounter.carbsRemaining = lastDbId - startId
        appRepository.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Carbs data Start: $startId ID: ${carb.first.id} HistoryID: ${carb.second.id} ")
            when {
                // only NsId changed, no need to upload
                carb.first.onlyNsIdAdded(carb.second)        -> {
                    confirmLastCarbsIdIfGreater(carb.second.id)
                    //lastCarbsId = -1
                    processChangedCarbsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Only NS id changed ID: ${carb.first.id} HistoryID: ${carb.second.id} ")
                    return false
                }
                // without nsId = create new
                carb.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", carb.first.toJson(true, dateUtil), DataSyncSelector.PairCarbs(carb.first, carb.second.id), "$startId/$lastDbId")
                // with nsId = update
                carb.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        carb.first.interfaceIDs.nightscoutId,
                        carb.first.toJson(false, dateUtil),
                        DataSyncSelector.PairCarbs(carb.first, carb.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting BolusCalculatorResult data sync from $lastSynced")
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedBolusCalculatorResults(): List<BolusCalculatorResult> {
        val startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
        return appRepository.getModifiedBolusCalculatorResultsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading BolusCalculatorResult data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastBcrId = -1L
    //private var lastBcrTime = -1L
    override fun processChangedBolusCalculatorResultsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastBolusCalculatorResultIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastBcrId && dateUtil.now() - lastBcrTime < 5000) return false
        //lastBcrId = startId
        //lastBcrTime = dateUtil.now()
        queueCounter.bcrRemaining = lastDbId - startId
        appRepository.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
            aapsLogger.info(LTag.NSCLIENT, "Loading BolusCalculatorResult data Start: $startId ID: ${bolusCalculatorResult.first.id} HistoryID: ${bolusCalculatorResult.second.id} ")
            when {
                // only NsId changed, no need to upload
                bolusCalculatorResult.first.onlyNsIdAdded(bolusCalculatorResult.second) -> {
                    confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
                    //lastBcrId = -1
                    processChangedBolusCalculatorResultsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Only NS id changed ID: ${bolusCalculatorResult.first.id} HistoryID: ${bolusCalculatorResult.second.id} ")
                    return false
                }
                // without nsId = create new
                bolusCalculatorResult.first.interfaceIDs.nightscoutId == null           ->
                    nsClientPlugin.nsClientService?.dbAdd(
                        "treatments",
                        bolusCalculatorResult.first.toJson(true, dateUtil),
                        DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id),
                        "$startId/$lastDbId"
                    )
                // with nsId = update
                bolusCalculatorResult.first.interfaceIDs.nightscoutId != null           ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments", bolusCalculatorResult.first.interfaceIDs.nightscoutId, bolusCalculatorResult.first.toJson(false, dateUtil),
                        DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id), "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting TemporaryTarget data sync from $lastSynced")
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedTempTargets(): List<TemporaryTarget> {
        val startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
        return appRepository.getModifiedTemporaryTargetsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TemporaryTarget data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastTtId = -1L
    //private var lastTtTime = -1L
    override fun processChangedTempTargetsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastTempTargetIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastTtId && dateUtil.now() - lastTtTime < 5000) return false
        //lastTtId = startId
        //lastTtTime = dateUtil.now()
        queueCounter.ttsRemaining = lastDbId - startId
        appRepository.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryTarget data Start: $startId ID: ${tt.first.id} HistoryID: ${tt.second.id} ")
            when {
                // record is not valid record and we are within first sync, no need to upload
                tt.first.id != tt.second.id && tt.second.id <= sp.getLong(R.string.key_ns_temporary_target_new_data_id, 0) -> {
                    confirmLastTempTargetsIdIfGreater(tt.second.id)
                    //lastTbrId = -1
                    processChangedTempTargetsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Change within first sync ID: ${tt.first.id} HistoryID: ${tt.second.id} ")
                    return false
                }
                // only NsId changed, no need to upload
                tt.first.onlyNsIdAdded(tt.second)          -> {
                    confirmLastTempTargetsIdIfGreater(tt.second.id)
                    //lastTtId = -1
                    processChangedTempTargetsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Only NS id changed ID: ${tt.first.id} HistoryID: ${tt.second.id} ")
                    return false
                }
                // without nsId = create new
                tt.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd(
                        "treatments",
                        tt.first.toJson(true, profileFunction.getUnits(), dateUtil),
                        DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id),
                        "$startId/$lastDbId"
                    )
                // existing with nsId = update
                tt.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        tt.first.interfaceIDs.nightscoutId,
                        tt.first.toJson(false, profileFunction.getUnits(), dateUtil),
                        DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_food_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting Food data sync from $lastSynced")
            sp.putLong(R.string.key_ns_food_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedFoods(): List<Food> {
        val startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
        return appRepository.getModifiedFoodDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading Food data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastFoodId = -1L
    //private var lastFoodTime = -1L
    override fun processChangedFoodsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastFoodIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_food_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastFoodId && dateUtil.now() - lastFoodTime < 5000) return false
        //lastFoodId = startId
        //lastFoodTime = dateUtil.now()
        queueCounter.foodsRemaining = lastDbId - startId
        appRepository.getNextSyncElementFood(startId).blockingGet()?.let { food ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Food data Start: $startId ID: ${food.first.id} HistoryID: ${food.second} ")
            when {
                // only NsId changed, no need to upload
                food.first.onlyNsIdAdded(food.second)        -> {
                    confirmLastFoodIdIfGreater(food.second.id)
                    //lastFoodId = -1
                    processChangedFoodsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Only NS id changed ID: ${food.first.id} HistoryID: ${food.second.id} ")
                    return false
                }
                // without nsId = create new
                food.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("food", food.first.toJson(true), DataSyncSelector.PairFood(food.first, food.second.id), "$startId/$lastDbId")
                // with nsId = update
                food.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "food",
                        food.first.interfaceIDs.nightscoutId,
                        food.first.toJson(false),
                        DataSyncSelector.PairFood(food.first, food.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting GlucoseValue data sync from $lastSynced")
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedGlucoseValues(): List<GlucoseValue> {
        val startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
        return appRepository.getModifiedBgReadingsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading GlucoseValue data for sync from $startId . Records ${it.size}")
        }
    }

    //private var lastGvId = -1L
    //private var lastGvTime = -1L
    override tailrec fun processChangedGlucoseValuesCompat() {
        val lastDbIdWrapped = appRepository.getLastGlucoseValueIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastGvId && dateUtil.now() - lastGvTime < 5000) return false
        //lastGvId = startId
        //lastGvTime = dateUtil.now()
        queueCounter.gvsRemaining = lastDbId - startId
        var tailCall = false
        appRepository.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
            aapsLogger.info(LTag.NSCLIENT, "Loading GlucoseValue data ID: ${gv.first.id} HistoryID: ${gv.second.id} ")
            if (activePlugin.activeBgSource.shouldUploadToNs(gv.first)) {
                when {
                    // record is not valid record and we are within first sync, no need to upload
                    gv.first.id != gv.second.id && gv.second.id <= sp.getLong(R.string.key_ns_glucose_value_new_data_id, 0) -> {
                        confirmLastGlucoseValueIdIfGreater(gv.second.id)
                        //lastGvId = -1
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Change within first sync ID: ${gv.first.id} HistoryID: ${gv.second.id} ")
                        tailCall = true
                    }
                    // only NsId changed, no need to upload
                    gv.first.onlyNsIdAdded(gv.second)          -> {
                        confirmLastGlucoseValueIdIfGreater(gv.second.id)
                        //lastGvId = -1
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Only NS id changed ID: ${gv.first.id} HistoryID: ${gv.second.id} ")
                        tailCall = true
                    }
                    // without nsId = create new
                    gv.first.interfaceIDs.nightscoutId == null ->
                        nsClientPlugin.nsClientService?.dbAdd("entries", gv.first.toJson(true, dateUtil), DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id), "$startId/$lastDbId")
                    // with nsId = update
                    else ->  //  gv.first.interfaceIDs.nightscoutId != null
                        nsClientPlugin.nsClientService?.dbUpdate(
                            "entries",
                            gv.first.interfaceIDs.nightscoutId,
                            gv.first.toJson(false, dateUtil),
                            DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id),
                            "$startId/$lastDbId"
                        )
                }
            } else {
                confirmLastGlucoseValueIdIfGreater(gv.second.id)
                //lastGvId = -1
                tailCall = true
            }
        }
        if (tailCall) {
            processChangedGlucoseValuesCompat()
        }
    }

    override fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting TherapyEvents data sync from $lastSynced")
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedTherapyEvents(): List<TherapyEvent> {
        val startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
        return appRepository.getModifiedTherapyEventDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TherapyEvents data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastTeId = -1L
    //private var lastTeTime = -1L
    override fun processChangedTherapyEventsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastTherapyEventIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastTeId && dateUtil.now() - lastTeTime < 5000) return false
        //lastTeId = startId
        //lastTeTime = dateUtil.now()
        queueCounter.tesRemaining = lastDbId - startId
        appRepository.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TherapyEvents data Start: $startId ID: ${te.first.id} HistoryID: ${te.second} ")
            when {
                // only NsId changed, no need to upload
                te.first.onlyNsIdAdded(te.second)          -> {
                    confirmLastTherapyEventIdIfGreater(te.second.id)
                    //lastTeId = -1
                    processChangedTherapyEventsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvents. Only NS id changed ID: ${te.first.id} HistoryID: ${te.second.id} ")
                    return false
                }
                // without nsId = create new
                te.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", te.first.toJson(true, dateUtil), DataSyncSelector.PairTherapyEvent(te.first, te.second.id), "$startId/$lastDbId")
                // nsId = update
                te.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        te.first.interfaceIDs.nightscoutId,
                        te.first.toJson(false, dateUtil),
                        DataSyncSelector.PairTherapyEvent(te.first, te.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting DeviceStatus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_device_status_last_synced_id, lastSynced)
        }
    }

    override fun changedDeviceStatuses(): List<DeviceStatus> {
        val startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        return appRepository.getModifiedDeviceStatusDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading DeviceStatus data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastDsId = -1L
    //private var lastDsTime = -1L
    override fun processChangedDeviceStatusesCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastDeviceStatusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_device_status_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastDsId && dateUtil.now() - lastDsTime < 5000) return false
        //lastDsId = startId
        //lastDsTime = dateUtil.now()
        queueCounter.dssRemaining = lastDbId - startId
        appRepository.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
            aapsLogger.info(LTag.NSCLIENT, "Loading DeviceStatus data Start: $startId ID: ${deviceStatus.id}")
            when {
                // without nsId = create new
                deviceStatus.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("devicestatus", deviceStatus.toJson(dateUtil), deviceStatus, "$startId/$lastDbId")
                // with nsId = ignore
                deviceStatus.interfaceIDs.nightscoutId != null -> Any()
            }
            return true
        }
        return false
    }

    override fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting TemporaryBasal data sync from $lastSynced")
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedTemporaryBasals(): List<TemporaryBasal> {
        val startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
        return appRepository.getModifiedTemporaryBasalDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TemporaryBasal data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastTbrId = -1L
    //private var lastTbrTime = -1L
    override fun processChangedTemporaryBasalsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastTemporaryBasalIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastTbrId && dateUtil.now() - lastTbrTime < 5000) return false
        //lastTbrId = startId
        //lastTbrTime = dateUtil.now()
        queueCounter.tbrsRemaining = lastDbId - startId
        appRepository.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryBasal data Start: $startId ID: ${tb.first.id} HistoryID: ${tb.second} ")
            val profile = profileFunction.getProfile(tb.first.timestamp)
            if (profile != null) {
                when {
                    // record is not valid record and we are within first sync, no need to upload
                    tb.first.id != tb.second.id && tb.second.id <= sp.getLong(R.string.key_ns_temporary_basal_new_data_id, 0) -> {
                        confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                        //lastTbrId = -1
                        processChangedTemporaryBasalsCompat()
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Change within first sync ID: ${tb.first.id} HistoryID: ${tb.second.id} ")
                        return false
                    }
                    // only NsId changed, no need to upload
                    tb.first.onlyNsIdAdded(tb.second)          -> {
                        confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                        //lastTbrId = -1
                        processChangedTemporaryBasalsCompat()
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Only NS id changed ID: ${tb.first.id} HistoryID: ${tb.second.id} ")
                        return false
                    }
                    // without nsId = create new
                    tb.first.interfaceIDs.nightscoutId == null ->
                        nsClientPlugin.nsClientService?.dbAdd(
                            "treatments",
                            tb.first.toJson(true, profile, dateUtil),
                            DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id),
                            "$startId/$lastDbId"
                        )
                    // with nsId = update
                    tb.first.interfaceIDs.nightscoutId != null ->
                        nsClientPlugin.nsClientService?.dbUpdate(
                            "treatments",
                            tb.first.interfaceIDs.nightscoutId,
                            tb.first.toJson(false, profile, dateUtil),
                            DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id),
                            "$startId/$lastDbId"
                        )
                }
                return true
            } else {
                confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                //lastTbrId = -1
                processChangedTemporaryBasalsCompat()
            }
        }
        return false
    }

    override fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting ExtendedBolus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedExtendedBoluses(): List<ExtendedBolus> {
        val startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
        return appRepository.getModifiedExtendedBolusDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading ExtendedBolus data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastEbId = -1L
    //private var lastEbTime = -1L
    override fun processChangedExtendedBolusesCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastExtendedBolusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastEbId && dateUtil.now() - lastEbTime < 5000) return false
        //lastEbId = startId
        //lastEbTime = dateUtil.now()
        queueCounter.ebsRemaining = lastDbId - startId
        appRepository.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading ExtendedBolus data Start: $startId ID: ${eb.first.id} HistoryID: ${eb.second} ")
            val profile = profileFunction.getProfile(eb.first.timestamp)
            if (profile != null) {
                when {
                    // record is not valid record and we are within first sync, no need to upload
                    eb.first.id != eb.second.id && eb.second.id <= sp.getLong(R.string.key_ns_extended_bolus_new_data_id, 0) -> {
                        confirmLastExtendedBolusIdIfGreater(eb.second.id)
                        //lastTbrId = -1
                        processChangedExtendedBolusesCompat()
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Change within first sync ID: ${eb.first.id} HistoryID: ${eb.second.id} ")
                        return false
                    }
                    // only NsId changed, no need to upload
                    eb.first.onlyNsIdAdded(eb.second)          -> {
                        confirmLastExtendedBolusIdIfGreater(eb.second.id)
                        //lastEbId = -1
                        processChangedExtendedBolusesCompat()
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Only NS id changed ID: ${eb.first.id} HistoryID: ${eb.second.id} ")
                        return false
                    }
                    // without nsId = create new
                    eb.first.interfaceIDs.nightscoutId == null ->
                        nsClientPlugin.nsClientService?.dbAdd(
                            "treatments",
                            eb.first.toJson(true, profile, dateUtil),
                            DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id),
                            "$startId/$lastDbId"
                        )
                    // with nsId = update
                    eb.first.interfaceIDs.nightscoutId != null ->
                        nsClientPlugin.nsClientService?.dbUpdate(
                            "treatments",
                            eb.first.interfaceIDs.nightscoutId,
                            eb.first.toJson(false, profile, dateUtil),
                            DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id),
                            "$startId/$lastDbId"
                        )
                }
                return true
            } else {
                confirmLastExtendedBolusIdIfGreater(eb.second.id)
                //lastEbId = -1
                processChangedExtendedBolusesCompat()
            }
        }
        return false
    }

    override fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting ProfileSwitch data sync from $lastSynced")
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, lastSynced)
        }
    }

    override fun changedProfileSwitch(): List<ProfileSwitch> {
        val startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        return appRepository.getModifiedProfileSwitchDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading ProfileSwitch data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastPsId = -1L
    //private var lastPsTime = -1L
    override fun processChangedProfileSwitchesCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastProfileSwitchIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastPsId && dateUtil.now() - lastPsTime < 5000) return false
        //lastPsId = startId
        //lastPsTime = dateUtil.now()
        queueCounter.pssRemaining = lastDbId - startId
        appRepository.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
            aapsLogger.info(LTag.NSCLIENT, "Loading ProfileSwitch data Start: $startId ID: ${ps.first.id} HistoryID: ${ps.second} ")
            when {
                // only NsId changed, no need to upload
                ps.first.onlyNsIdAdded(ps.second)          -> {
                    confirmLastProfileSwitchIdIfGreater(ps.second.id)
                    //lastPsId = -1
                    processChangedProfileSwitchesCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Only NS id changed ID: ${ps.first.id} HistoryID: ${ps.second.id} ")
                    return false
                }
                // without nsId = create new
                ps.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", ps.first.toJson(true, dateUtil), DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId")
                // with nsId = update
                ps.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        ps.first.interfaceIDs.nightscoutId,
                        ps.first.toJson(false, dateUtil),
                        DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting EffectiveProfileSwitch data sync from $lastSynced")
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, lastSynced)
        }
    }

    override fun changedEffectiveProfileSwitch(): List<EffectiveProfileSwitch> {
        val startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
        return appRepository.getModifiedEffectiveProfileSwitchDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastEpsId = -1L
    //private var lastEpsTime = -1L
    override fun processChangedEffectiveProfileSwitchesCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastEffectiveProfileSwitchIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastEpsId && dateUtil.now() - lastEpsTime < 5000) return false
        //lastEpsId = startId
        //lastEpsTime = dateUtil.now()
        queueCounter.epssRemaining = lastDbId - startId
        appRepository.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
            aapsLogger.info(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data Start: $startId ID: ${ps.first.id} HistoryID: ${ps.second} ")
            when {
                // only NsId changed, no need to upload
                ps.first.onlyNsIdAdded(ps.second)          -> {
                    confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
                    //lastEpsId = -1
                    processChangedEffectiveProfileSwitchesCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Only NS id changed ID: ${ps.first.id} HistoryID: ${ps.second.id} ")
                    return false
                }
                // without nsId = create new
                ps.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", ps.first.toJson(true, dateUtil), DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId")
                // with nsId = update
                ps.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        ps.first.interfaceIDs.nightscoutId,
                        ps.first.toJson(false, dateUtil),
                        DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastOfflineEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting OfflineEvent data sync from $lastSynced")
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedOfflineEvents(): List<OfflineEvent> {
        val startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
        return appRepository.getModifiedOfflineEventsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading OfflineEvent data for sync from $startId. Records ${it.size}")
        }
    }

    //private var lastOeId = -1L
    //private var lastOeTime = -1L
    override fun processChangedOfflineEventsCompat(): Boolean {
        val lastDbIdWrapped = appRepository.getLastOfflineEventIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, 0)
            startId = 0
        }
        //if (startId == lastOeId && dateUtil.now() - lastOeTime < 5000) return false
        //lastOeId = startId
        //lastOeTime = dateUtil.now()
        queueCounter.oesRemaining = lastDbId - startId
        appRepository.getNextSyncElementOfflineEvent(startId).blockingGet()?.let { oe ->
            aapsLogger.info(LTag.NSCLIENT, "Loading OfflineEvent data Start: $startId ID: ${oe.first.id} HistoryID: ${oe.second} ")
            when {
                // only NsId changed, no need to upload
                oe.first.onlyNsIdAdded(oe.second)          -> {
                    confirmLastOfflineEventIdIfGreater(oe.second.id)
                    //lastOeId = -1
                    processChangedOfflineEventsCompat()
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Only NS id changed ID: ${oe.first.id} HistoryID: ${oe.second.id} ")
                    return false
                }
                // without nsId = create new
                oe.first.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", oe.first.toJson(true, dateUtil), DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id), "$startId/$lastDbId")
                // existing with nsId = update
                oe.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbUpdate(
                        "treatments",
                        oe.first.interfaceIDs.nightscoutId,
                        oe.first.toJson(false, dateUtil),
                        DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return true
        }
        return false
    }

    override fun confirmLastProfileStore(lastSynced: Long) {
        sp.putLong(R.string.key_ns_profile_store_last_synced_timestamp, lastSynced)
    }

    override fun processChangedProfileStore() {
        val lastSync = sp.getLong(R.string.key_ns_profile_store_last_synced_timestamp, 0)
        val lastChange = sp.getLong(R.string.key_local_profile_last_change, 0)
        if (lastChange == 0L) return
        if (lastChange > lastSync) {
            val profileJson = localProfilePlugin.profile?.data ?: return
            nsClientPlugin.nsClientService?.dbAdd("profile", profileJson, DataSyncSelector.PairProfileStore(profileJson, dateUtil.now()), "")
        }
    }
}
