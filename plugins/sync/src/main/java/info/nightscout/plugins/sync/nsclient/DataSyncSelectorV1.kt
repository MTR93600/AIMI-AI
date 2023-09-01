package info.nightscout.plugins.sync.nsclient

import info.nightscout.core.utils.waitMillis
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.source.NSClientSource
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiQueue
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncSelectorV1 @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val appRepository: AppRepository,
    private val rxBus: RxBus
) : DataSyncSelector {

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
    private val isPaused get() = sp.getBoolean(R.string.key_ns_paused, false)

    override fun queueSize(): Long = queueCounter.size()

    private var running = false
    private val sync = Any()

    private val bgUploadEnabled get() = sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false) && activePlugin.activeBgSource !is NSClientSource

    override suspend fun doUpload() {
        synchronized(sync) {
            if (running) {
                rxBus.send(EventNSClientNewLog("● RUN", "Already running"))
                return
            }
            running = true
        }
        rxBus.send(EventNSClientUpdateGuiStatus())
        if (sp.getBoolean(R.string.key_ns_upload, true) && !isPaused) {
            queueCounter.bolusesRemaining = (appRepository.getLastBolusId() ?: 0L) - sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
            queueCounter.carbsRemaining = (appRepository.getLastCarbsId() ?: 0L) - sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
            queueCounter.bcrRemaining = (appRepository.getLastBolusCalculatorResultId() ?: 0L) - sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            queueCounter.ttsRemaining = (appRepository.getLastTempTargetId() ?: 0L) - sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            queueCounter.foodsRemaining = (appRepository.getLastFoodId() ?: 0L) - sp.getLong(R.string.key_ns_food_last_synced_id, 0)
            queueCounter.gvsRemaining = (appRepository.getLastGlucoseValueId() ?: 0L) - sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            queueCounter.tesRemaining = (appRepository.getLastTherapyEventId() ?: 0L) - sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            queueCounter.dssRemaining = (appRepository.getLastDeviceStatusId() ?: 0L) - sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
            queueCounter.tbrsRemaining = (appRepository.getLastTemporaryBasalId() ?: 0L) - sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            queueCounter.ebsRemaining = (appRepository.getLastExtendedBolusId() ?: 0L) - sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            queueCounter.pssRemaining = (appRepository.getLastProfileSwitchId() ?: 0L) - sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            queueCounter.epssRemaining = (appRepository.getLastEffectiveProfileSwitchId() ?: 0L) - sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            queueCounter.oesRemaining = (appRepository.getLastOfflineEventId() ?: 0L) - sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
            rxBus.send(EventNSClientUpdateGuiQueue())
            val boluses = scope.async { processChangedBoluses() }
            val carbs = scope.async { processChangedCarbs() }
            val bcs = scope.async { processChangedBolusCalculatorResults() }
            val tbs = scope.async { processChangedTemporaryBasals() }
            val ebs = scope.async { processChangedExtendedBoluses() }
            val pss = scope.async { processChangedProfileSwitches() }
            val epss = scope.async { processChangedEffectiveProfileSwitches() }
            val gvs = scope.async { processChangedGlucoseValues() }
            val tts = scope.async { processChangedTempTargets() }
            val foods = scope.async { processChangedFoods() }
            val tes = scope.async { processChangedTherapyEvents() }
            val dss = scope.async { processChangedDeviceStatuses() }
            val oes = scope.async { processChangedOfflineEvents() }
            val ps = scope.async { processChangedProfileStore() }

            boluses.await()
            carbs.await()
            bcs.await()
            tbs.await()
            ebs.await()
            pss.await()
            epss.await()
            gvs.await()
            tts.await()
            foods.await()
            tes.await()
            dss.await()
            oes.await()
            ps.await()
        }
        rxBus.send(EventNSClientUpdateGuiStatus())
        running = false
    }

    override fun resetToNextFullSync() {
        sp.remove(R.string.key_ns_glucose_value_last_synced_id)
        sp.remove(R.string.key_ns_temporary_basal_last_synced_id)
        sp.remove(R.string.key_ns_temporary_target_last_synced_id)
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

        val lastDeviceStatusDbId = appRepository.getLastDeviceStatusId()
        if (lastDeviceStatusDbId != null) sp.putLong(R.string.key_ns_device_status_last_synced_id, lastDeviceStatusDbId)
        else sp.remove(R.string.key_ns_device_status_last_synced_id)
    }

    private fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_bolus_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedBoluses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastBolusId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_bolus_last_synced_id, 0)
                startId = 0
            }
            queueCounter.bolusesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
                aapsLogger.info(LTag.NSCLIENT, "Loading Bolus data Start: $startId ${bolus.first} forID: ${bolus.second.id} ")
                val dataPair = DataSyncSelector.PairBolus(bolus.first, bolus.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    bolus.first.id == bolus.second.id && bolus.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Loaded from NS: ${bolus.second.id} ")
                    // only NsId changed, no need to upload
                    bolus.first.onlyNsIdAdded(bolus.second)                                            ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Only NS id changed: ${bolus.second.id} ")
                    // without nsId = create new
                    bolus.first.interfaceIDs.nightscoutId == null                                      -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, " $startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update if it's modified record
                    bolus.first.interfaceIDs.nightscoutId != null && bolus.first.id != bolus.second.id -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastBolusIdIfGreater(bolus.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_carbs_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedCarbs() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastCarbsId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_carbs_last_synced_id, 0)
                startId = 0
            }
            queueCounter.carbsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
                aapsLogger.info(LTag.NSCLIENT, "Loading Carbs data Start: $startId ${carb.first} forID: ${carb.second.id} ")
                val dataPair = DataSyncSelector.PairCarbs(carb.first, carb.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    carb.first.id == carb.second.id && carb.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Loaded from NS: ${carb.second.id} ")
                    // only NsId changed, no need to upload
                    carb.first.onlyNsIdAdded(carb.second)                                           ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Only NS id changed ID: ${carb.second.id} ")
                    // without nsId = create new
                    carb.first.interfaceIDs.nightscoutId == null                                    -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update if it's modified record
                    carb.first.interfaceIDs.nightscoutId != null && carb.first.id != carb.second.id -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastCarbsIdIfGreater(carb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedBolusCalculatorResults() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastBolusCalculatorResultId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
                startId = 0
            }
            queueCounter.bcrRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
                aapsLogger.info(LTag.NSCLIENT, "Loading BolusCalculatorResult data Start: $startId ${bolusCalculatorResult.first} forID: ${bolusCalculatorResult.second.id} ")
                val dataPair = DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    bolusCalculatorResult.first.id == bolusCalculatorResult.second.id && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Loaded from NS: ${bolusCalculatorResult.second.id} ")
                    // only NsId changed, no need to upload
                    bolusCalculatorResult.first.onlyNsIdAdded(bolusCalculatorResult.second)                                                            ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Only NS id changed ID: ${bolusCalculatorResult.second.id} ")
                    // without nsId = create new
                    bolusCalculatorResult.first.interfaceIDs.nightscoutId == null                                                                      -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update if it's modified record
                    bolusCalculatorResult.first.interfaceIDs.nightscoutId != null && bolusCalculatorResult.first.id != bolusCalculatorResult.second.id -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedTempTargets() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastTempTargetId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_temporary_target_last_synced_id, 0)
                startId = 0
            }
            queueCounter.ttsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
                aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryTarget data Start: $startId ${tt.first} forID: ${tt.second.id} ")
                val dataPair = DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    tt.first.id == tt.second.id && tt.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Loaded from NS: ${tt.second.id} ")
                    // only NsId changed, no need to upload
                    tt.first.onlyNsIdAdded(tt.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Only NS id changed ID: ${tt.second.id} ")
                    // without nsId = create new
                    tt.first.interfaceIDs.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // existing with nsId = update
                    tt.first.interfaceIDs.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastTempTargetsIdIfGreater(tt.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_food_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_food_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedFoods() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastFoodId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_food_last_synced_id, 0)
                startId = 0
            }
            queueCounter.foodsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementFood(startId).blockingGet()?.let { food ->
                aapsLogger.info(LTag.NSCLIENT, "Loading Food data Start: $startId ${food.first} forID: ${food.second.id} ")
                val dataPair = DataSyncSelector.PairFood(food.first, food.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    food.first.id == food.second.id && food.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Loaded from NS: ${food.second.id} ")
                    // only NsId changed, no need to upload
                    food.first.onlyNsIdAdded(food.second)                                           ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Only NS id changed ID: ${food.second.id} ")
                    // without nsId = create new
                    food.first.interfaceIDs.nightscoutId == null                                    -> {
                        activePlugin.activeNsClient?.nsAdd("food", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update
                    food.first.interfaceIDs.nightscoutId != null                                    -> {
                        activePlugin.activeNsClient?.nsUpdate("food", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastFoodIdIfGreater(food.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedGlucoseValues() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastGlucoseValueId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_glucose_value_last_synced_id, 0)
                startId = 0
            }
            queueCounter.gvsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
                aapsLogger.info(LTag.NSCLIENT, "Loading GlucoseValue data Start: $startId ${gv.first} forID: ${gv.second.id} ")
                val dataPair = DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id)
                if (bgUploadEnabled) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        gv.first.id == gv.second.id && gv.first.interfaceIDs.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Loaded from NS: ${gv.second.id} ")
                        // only NsId changed, no need to upload
                        gv.first.onlyNsIdAdded(gv.second)                                         ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Only NS id changed ID: ${gv.second.id} ")
                        // without nsId = create new
                        gv.first.interfaceIDs.nightscoutId == null                                -> {
                            activePlugin.activeNsClient?.nsAdd("entries", dataPair, "$startId/$lastDbId")
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                        // with nsId = update
                        else                                                                      -> {//  gv.first.interfaceIDs.nightscoutId != null
                            activePlugin.activeNsClient?.nsUpdate("entries", dataPair, "$startId/$lastDbId")
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                    }
                }
                if (cont) confirmLastGlucoseValueIdIfGreater(gv.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedTherapyEvents() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastTherapyEventId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_therapy_event_last_synced_id, 0)
                startId = 0
            }
            queueCounter.tesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
                aapsLogger.info(LTag.NSCLIENT, "Loading TherapyEvents data Start: $startId ${te.first} forID: ${te.second.id} ")
                val dataPair = DataSyncSelector.PairTherapyEvent(te.first, te.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    te.first.id == te.second.id && te.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Loaded from NS: ${te.second.id} ")
                    // only NsId changed, no need to upload
                    te.first.onlyNsIdAdded(te.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Only NS id changed ID: ${te.second.id} ")
                    // without nsId = create new
                    te.first.interfaceIDs.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // nsId = update
                    te.first.interfaceIDs.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastTherapyEventIdIfGreater(te.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_device_status_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedDeviceStatuses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastDeviceStatusId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_device_status_last_synced_id, 0)
                startId = 0
            }
            queueCounter.dssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
                aapsLogger.info(LTag.NSCLIENT, "Loading DeviceStatus data Start: $startId $deviceStatus")
                val dataPair = DataSyncSelector.PairDeviceStatus(deviceStatus, lastDbId)
                activePlugin.activeNsClient?.nsAdd("devicestatus", dataPair, "$startId/$lastDbId")
                synchronized(dataPair) { dataPair.waitMillis(60000) }
                cont = dataPair.confirmed
                if (cont) confirmLastDeviceStatusIdIfGreater(deviceStatus.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedTemporaryBasals() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastTemporaryBasalId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
                startId = 0
            }
            queueCounter.tbrsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
                aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryBasal data Start: $startId ${tb.first} forID: ${tb.second.id} ")
                val dataPair = DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id)
                val profile = profileFunction.getProfile(tb.first.timestamp)
                if (profile != null) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        tb.first.id == tb.second.id && tb.first.interfaceIDs.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Loaded from NS: ${tb.second.id} ")
                        // only NsId changed, no need to upload
                        tb.first.onlyNsIdAdded(tb.second)                                         ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Only NS id changed ID: ${tb.second.id} ")
                        // without nsId = create new
                        tb.first.interfaceIDs.nightscoutId == null                                -> {
                            activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                        // with nsId = update
                        tb.first.interfaceIDs.nightscoutId != null                                -> {
                            activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                    }
                } else aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. No profile: ${tb.second.id} ")
                if (cont) confirmLastTemporaryBasalIdIfGreater(tb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedExtendedBoluses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastExtendedBolusId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
                startId = 0
            }
            queueCounter.ebsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
                aapsLogger.info(LTag.NSCLIENT, "Loading ExtendedBolus data Start: $startId ${eb.first} forID: ${eb.second.id} ")
                val dataPair = DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id)
                val profile = profileFunction.getProfile(eb.first.timestamp)
                if (profile != null) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        eb.first.id == eb.second.id && eb.first.interfaceIDs.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Loaded from NS: ${eb.second.id} ")
                        // only NsId changed, no need to upload
                        eb.first.onlyNsIdAdded(eb.second)                                         ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Only NS id changed ID: ${eb.second.id} ")
                        // without nsId = create new
                        eb.first.interfaceIDs.nightscoutId == null                                -> {
                            activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                        // with nsId = update
                        eb.first.interfaceIDs.nightscoutId != null                                -> {
                            activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                    }
                } else aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. No profile: ${eb.second.id} ")
                if (cont) confirmLastExtendedBolusIdIfGreater(eb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedProfileSwitches() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastProfileSwitchId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_profile_switch_last_synced_id, 0)
                startId = 0
            }
            queueCounter.pssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
                aapsLogger.info(LTag.NSCLIENT, "Loading ProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
                val dataPair = DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    // only NsId changed, no need to upload
                    ps.first.onlyNsIdAdded(ps.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    // without nsId = create new
                    ps.first.interfaceIDs.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update
                    ps.first.interfaceIDs.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastProfileSwitchIdIfGreater(ps.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedEffectiveProfileSwitches() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastEffectiveProfileSwitchId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
                startId = 0
            }
            queueCounter.epssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
                aapsLogger.info(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
                val dataPair = DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    // only NsId changed, no need to upload
                    ps.first.onlyNsIdAdded(ps.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    // without nsId = create new
                    ps.first.interfaceIDs.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update
                    ps.first.interfaceIDs.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastOfflineEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedOfflineEvents() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastOfflineEventId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
            if (startId > lastDbId) {
                sp.putLong(R.string.key_ns_offline_event_last_synced_id, 0)
                startId = 0
            }
            queueCounter.oesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementOfflineEvent(startId).blockingGet()?.let { oe ->
                aapsLogger.info(LTag.NSCLIENT, "Loading OfflineEvent data Start: $startId ${oe.first} forID: ${oe.second.id} ")
                val dataPair = DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    oe.first.id == oe.second.id && oe.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Loaded from NS: ${oe.second.id} ")
                    // only NsId changed, no need to upload
                    oe.first.onlyNsIdAdded(oe.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Only NS id changed ID: ${oe.second.id} ")
                    // without nsId = create new
                    oe.first.interfaceIDs.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // existing with nsId = update
                    oe.first.interfaceIDs.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastOfflineEventIdIfGreater(oe.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastProfileStore(lastSynced: Long) {
        sp.putLong(R.string.key_ns_profile_store_last_synced_timestamp, lastSynced)
    }

    private suspend fun processChangedProfileStore() {
        if (isPaused) return
        val lastSync = sp.getLong(R.string.key_ns_profile_store_last_synced_timestamp, 0)
        val lastChange = sp.getLong(info.nightscout.core.utils.R.string.key_local_profile_last_change, 0)
        if (lastChange == 0L) return
        if (lastChange > lastSync) {
            if (activePlugin.activeProfileSource.profile?.allProfilesValid != true) return
            val profileStore = activePlugin.activeProfileSource.profile
            val profileJson = profileStore?.data ?: return
            // add for v3
            if (JsonHelper.safeGetLongAllowNull(profileJson, "date") == null)
                profileJson.put("date", profileStore.getStartDate())
            val dataPair = DataSyncSelector.PairProfileStore(profileJson, dateUtil.now())
            activePlugin.activeNsClient?.nsAdd("profile", dataPair, "")
            synchronized(dataPair) { dataPair.waitMillis(60000) }
            val now = dateUtil.now()
            val cont = dataPair.confirmed
            if (cont) confirmLastProfileStore(now)
        }
    }
}