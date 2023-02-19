package info.nightscout.androidaps.plugins.pump.medtronic.data


import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusWizardDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.common.sync.PumpSyncStorage
import info.nightscout.pump.core.utils.StringUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by dirceu
 * used by medlink
*/

// TODO: After release we need to refactor how data is retrieved from pump, each entry in history needs to be marked, and sorting
//  needs to happen according those markings, not on time stamp (since AAPS can change time anytime it drifts away). This
//  needs to include not returning any records if TZ goes into -x area. To fully support this AAPS would need to take note of
//  all times that time changed (TZ, DST, etc.). Data needs to be returned in batches (time_changed batches, so that we can
//  handle it. It would help to assign sort_ids to items (from oldest (1) to newest (x)
// All things marked with "TODO: Fix db code" needs to be updated in new 2.5 database code
@Singleton
class MedLinkMedtronicHistoryData @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    sp: SP,
    rh: ResourceHelper,
    rxBus: RxBus,
    activePlugin: ActivePlugin,
    medtronicUtil: MedtronicUtil,
    medtronicPumpHistoryDecoder: MedtronicPumpHistoryDecoder,
    medtronicPumpStatus: MedtronicPumpStatus,
    pumpSync: PumpSync,
    pumpSyncStorage: PumpSyncStorage,
    uiInteraction: UiInteraction
): MedtronicHistoryData(injector, aapsLogger, sp, rh, rxBus, activePlugin, medtronicUtil, medtronicPumpHistoryDecoder, medtronicPumpStatus, pumpSync, pumpSyncStorage,
uiInteraction ) {

    private var newHistory: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
    private var isInit = false
    private var gson // cannot be initialized in constructor because of injection
        : Gson? = null
    private var gsonCore // cannot be initialized in constructor because of injection
        : Gson? = null
    private var pumpTime: ClockDTO? = null
    private var lastIdUsed: Long = 0
    private fun gson(): Gson? {
        if (gson == null) gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        return gson
    }

    private fun gsonCore(): Gson? {
        if (gsonCore == null) gsonCore = GsonBuilder().create()
        return gsonCore
    }

    /**
     * Add New History entries
     *
     * @param result PumpHistoryResult instance
     */
    // fun addNewHistory(result: PumpHistoryResult) {
    //     val validEntries: List<PumpHistoryEntry> = result.validEntries
    //     val newEntries: MutableList<PumpHistoryEntry?> = ArrayList<PumpHistoryEntry?>()
    //     for (validEntry in validEntries) {
    //         if (!allHistory.contains(validEntry)) {
    //             newEntries.add(validEntry)
    //         }
    //     }
    //     newHistory = newEntries
    //     showLogs("List of history (before filtering): [" + newHistory!!.size + "]", gson().toJson(newHistory))
    // }

    private fun showLogs(header: String?, data: String) {
        if (header != null) {
            aapsLogger.debug(LTag.PUMP, header)
        }
        if (StringUtils.isNotBlank(data)) {
            for (token in StringUtil.splitString(data, 3500)) {
                aapsLogger.debug(LTag.PUMP, "{}", token)
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "No data.")
        }
    }

//     fun getAllHistory(): List<PumpHistoryEntry> {
//         return allHistory
//     }
//
//     fun filterNewEntries() {
//         val newHistory2: MutableList<PumpHistoryEntry?> = ArrayList<PumpHistoryEntry?>()
//         var TBRs: MutableList<PumpHistoryEntry?> = ArrayList<PumpHistoryEntry?>()
//         val bolusEstimates: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
//         val atechDate: Long = DateTimeUtil.toATechDate(GregorianCalendar())
//
//         //aapsLogger.debug(LTag.PUMP, "Filter new entries: Before {}", newHistory);
//         if (!isCollectionEmpty(newHistory)) {
//             for (pumpHistoryEntry in newHistory) {
//                 if (!allHistory.contains(pumpHistoryEntry)) {
//                     val type: PumpHistoryEntryType = pumpHistoryEntry.entryType
//                     if (type === PumpHistoryEntryType.TempBasalRate || type === PumpHistoryEntryType.TempBasalDuration) {
//                         TBRs.add(pumpHistoryEntry)
//                     } else if (type === PumpHistoryEntryType.BolusWizard || type === PumpHistoryEntryType.BolusWizard512) {
//                         bolusEstimates.add(pumpHistoryEntry)
//                         newHistory2.add(pumpHistoryEntry)
//                     } else {
//                         if (type === PumpHistoryEntryType.EndResultTotals) {
//                             if (!DateTimeUtil.isSameDay(atechDate, pumpHistoryEntry.atechDateTime)) {
//                                 newHistory2.add(pumpHistoryEntry)
//                             }
//                         } else {
//                             newHistory2.add(pumpHistoryEntry)
//                         }
//                     }
//                 }
//             }
//             TBRs = preProcessTBRs(TBRs)
//             if (bolusEstimates.size > 0) {
//                 extendBolusRecords(bolusEstimates, newHistory2)
//             }
//             newHistory2.addAll(TBRs)
//             newHistory = newHistory2
//             sort(newHistory)
//         }
//
// //        aapsLogger.debug(LTag.PUMP, "New History entries found: {}", this.newHistory.size());
//         showLogs("List of history (after filtering): [" + newHistory!!.size + "]", gson().toJson(newHistory))
//     }

    private fun extendBolusRecords(bolusEstimates: List<PumpHistoryEntry>, newHistory2: List<PumpHistoryEntry>) {
        val boluses: List<PumpHistoryEntry> = getFilteredItems(newHistory2, PumpHistoryEntryType.Bolus)
        for (bolusEstimate in bolusEstimates) {
            for (bolus in boluses) {
                if (bolusEstimate.atechDateTime == bolus.atechDateTime) {
                    bolusEstimate.decodedData["Object"]?.let { bolus.addDecodedData("Estimate", it) }
                }
            }
        }
    }

    // fun finalizeNewHistoryRecords() {
    //     if (newHistory == null || newHistory!!.size == 0) return
    //     var pheLast: PumpHistoryEntry? = newHistory!![0]
    //
    //     // find last entry
    //     for (pumpHistoryEntry in newHistory) {
    //         if (pumpHistoryEntry.atechDateTime != null && pumpHistoryEntry.isAfter(pheLast.atechDateTime)) {
    //             pheLast = pumpHistoryEntry
    //         }
    //     }
    //
    //     // add new entries
    //     Collections.reverse(newHistory)
    //     for (pumpHistoryEntry in newHistory) {
    //         if (!allHistory.contains(pumpHistoryEntry)) {
    //             lastIdUsed++
    //             pumpHistoryEntry.id = lastIdUsed
    //             allHistory.add(pumpHistoryEntry)
    //         }
    //     }
    //     if (pheLast == null) // if we don't have any valid record we don't do the filtering and setting
    //         return
    //     setLastHistoryRecordTime(pheLast.atechDateTime)
    //     sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, pheLast.atechDateTime)
    //     var dt: LocalDateTime? = null
    //     try {
    //         dt = DateTimeUtil.toLocalDateTime(pheLast.atechDateTime)
    //     } catch (ex: Exception) {
    //         aapsLogger.error("Problem decoding date from last record: {}$pheLast")
    //     }
    //     if (dt != null) {
    //         dt = dt.minusDays(1) // we keep 24 hours
    //         val dtRemove: Long = DateTimeUtil.toATechDate(dt)
    //         val removeList: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
    //         for (pumpHistoryEntry in allHistory) {
    //             if (!pumpHistoryEntry.isAfter(dtRemove)) {
    //                 removeList.add(pumpHistoryEntry)
    //             }
    //         }
    //         allHistory.removeAll(removeList)
    //         this.sort(allHistory)
    //         aapsLogger.debug(
    //             LTag.PUMP, "All History records [afterFilterCount={}, removedItemsCount={}, newItemsCount={}]",
    //             allHistory.size, removeList.size, newHistory!!.size
    //         )
    //     } else {
    //         aapsLogger.error("Since we couldn't determine date, we don't clean full history. This is just workaround.")
    //     }
    //     newHistory!!.clear()
    // }
    //
    // fun hasRelevantConfigurationChanged(): Boolean {
    //     return getStateFromFilteredList( //
    //         PumpHistoryEntryType.ChangeBasalPattern,  //
    //         PumpHistoryEntryType.ClearSettings,  //
    //         PumpHistoryEntryType.SaveSettings,  //
    //         PumpHistoryEntryType.ChangeMaxBolus,  //
    //         PumpHistoryEntryType.ChangeMaxBasal,  //
    //         PumpHistoryEntryType.ChangeTempBasalType
    //     )
    // }

    private fun isCollectionEmpty(col: List<*>?): Boolean {
        return col == null || col.isEmpty()
    }

    private fun isCollectionNotEmpty(col: List<*>?): Boolean {
        return col != null && !col.isEmpty()
    }////////

    //
    // val isPumpSuspended: Boolean
    //     get() {
    //         val items: List<PumpHistoryEntry> = dataForPumpSuspends as List<PumpHistoryEntry>
    //         showLogs("isPumpSuspended: ", gson()!!.toJson(items))
    //         return if (isCollectionNotEmpty(items)) {
    //             val pumpHistoryEntryType: PumpHistoryEntryType = items[0].entryType
    //             val isSuspended = !(pumpHistoryEntryType === PumpHistoryEntryType.TempBasalCombined || //
    //                 pumpHistoryEntryType === PumpHistoryEntryType.BasalProfileStart || //
    //                 pumpHistoryEntryType === PumpHistoryEntryType.Bolus || //
    //                 pumpHistoryEntryType === PumpHistoryEntryType.ResumePump || //
    //                 pumpHistoryEntryType === PumpHistoryEntryType.BatteryChange || //
    //                 pumpHistoryEntryType === PumpHistoryEntryType.Prime)
    //             aapsLogger.debug(LTag.PUMP, "isPumpSuspended. Last entry type={}, isSuspended={}", pumpHistoryEntryType, isSuspended)
    //             isSuspended
    //         } else false
    //     }

    //
    //
    //
    //
    //
    //
    //
    //
    //
    private val dataForPumpSuspends: List<Any>
        private get() {
            val newAndAll: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
            if (isCollectionNotEmpty(allHistory)) {
                newAndAll.addAll(allHistory)
            }
            if (isCollectionNotEmpty(newHistory)) {
                for (pumpHistoryEntry in newHistory!!) {
                    if (!newAndAll.contains(pumpHistoryEntry)) {
                        newAndAll.add(pumpHistoryEntry!!)
                    }
                }
            }
            if (newAndAll.isEmpty()) return newAndAll
            this.sort(newAndAll)
            var newAndAll2: List<PumpHistoryEntry> = getFilteredItems(
                newAndAll,  //
                PumpHistoryEntryType.Bolus,  //
                PumpHistoryEntryType.TempBasalCombined,  //
                PumpHistoryEntryType.Prime,  //
                PumpHistoryEntryType.SuspendPump,  //
                PumpHistoryEntryType.ResumePump,  //
                PumpHistoryEntryType.Rewind,  //
                PumpHistoryEntryType.NoDeliveryAlarm,  //
                PumpHistoryEntryType.BatteryChange,  //
                PumpHistoryEntryType.BasalProfileStart
            )
            newAndAll2 = filterPumpSuspend(newAndAll2, 10)
            return newAndAll2
        }

    private fun filterPumpSuspend(newAndAll: List<PumpHistoryEntry>, filterCount: Int): List<PumpHistoryEntry> {
        if (newAndAll.size <= filterCount) {
            return newAndAll
        }
        val newAndAllOut: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
        for (i in 0 until filterCount) {
            newAndAllOut.add(newAndAll[i])
        }
        return newAndAllOut
    }

    /**
     * Process History Data: Boluses(Treatments), TDD, TBRs, Suspend-Resume (or other pump stops: battery, prime)
     */
    // fun processNewHistoryData() {
    //
    //     // TODO: Fix db code
    //     // Prime (for reseting autosense)
    //     val primeRecords: List<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.Prime)
    //     aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: Prime [count={}, items={}]", primeRecords.size, gson().toJson(primeRecords))
    //     if (isCollectionNotEmpty(primeRecords)) {
    //         try {
    //             processPrime(primeRecords)
    //         } catch (ex: Exception) {
    //             aapsLogger.error("ProcessHistoryData: Error processing Prime entries: " + ex.message, ex)
    //             throw ex
    //         }
    //     }
    //
    //     // TDD
    //     val tdds: List<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.EndResultTotals, tDDType)
    //     aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: TDD [count={}, items={}]", tdds.size, gson().toJson(tdds))
    //     if (isCollectionNotEmpty(tdds)) {
    //         try {
    //             processTDDs(tdds)
    //         } catch (ex: Exception) {
    //             aapsLogger.error("ProcessHistoryData: Error processing TDD entries: " + ex.message, ex)
    //             throw ex
    //         }
    //     }
    //     pumpTime = medtronicUtil.pumpTime
    //
    //     // Bolus
    //     val treatments: MutableList<PumpHistoryEntry?> = getFilteredItems(PumpHistoryEntryType.Bolus)
    //     aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: Bolus [count={}, items={}]", treatments.size, gson().toJson(treatments))
    //     if (treatments.size > 0) {
    //         try {
    //             processBolusEntries(treatments)
    //         } catch (ex: Exception) {
    //             aapsLogger.error("ProcessHistoryData: Error processing Bolus entries: " + ex.message, ex)
    //             throw ex
    //         }
    //     }
    //
    //     // TBR
    //     val tbrs: List<PumpHistoryEntry?> = getFilteredItems(PumpHistoryEntryType.TempBasalCombined)
    //     aapsLogger.debug(LTag.PUMP, "ProcessHistoryData: TBRs Processed [count={}, items={}]", tbrs.size, gson().toJson(tbrs))
    //     if (tbrs.size > 0) {
    //         try {
    //             processTBREntries(tbrs)
    //         } catch (ex: Exception) {
    //             aapsLogger.error("ProcessHistoryData: Error processing TBR entries: " + ex.message, ex)
    //             throw ex
    //         }
    //     }
    //
    //     // 'Delivery Suspend'
    //     val suspends: List<TempBasalProcessDTO>
    //     suspends = try {
    //         suspends
    //     } catch (ex: Exception) {
    //         aapsLogger.error("ProcessHistoryData: Error getting Suspend entries: " + ex.message, ex)
    //         throw ex
    //     }
    //     aapsLogger.debug(
    //         LTag.PUMP, "ProcessHistoryData: 'Delivery Suspend' Processed [count={}, items={}]", suspends.size,
    //         gson().toJson(suspends)
    //     )
    //     if (isCollectionNotEmpty(suspends)) {
    //         try {
    //             processSuspends(suspends)
    //         } catch (ex: Exception) {
    //             aapsLogger.error("ProcessHistoryData: Error processing Suspends entries: " + ex.message, ex)
    //             throw ex
    //         }
    //     }
    // }

    // private fun processPrime(primeRecords: List<PumpHistoryEntry>) {
    //     val maxAllowedTimeInPast: Long = DateTimeUtil.getATDWithAddedMinutes(GregorianCalendar(), -30)
    //     var lastPrimeRecord = 0L
    //     for (primeRecord in primeRecords) {
    //         if (primeRecord.atechDateTime > maxAllowedTimeInPast) {
    //             if (lastPrimeRecord < primeRecord.atechDateTime) {
    //                 lastPrimeRecord = primeRecord.atechDateTime
    //             }
    //         }
    //     }
    //     if (lastPrimeRecord != 0L) {
    //         val lastPrimeFromAAPS: Long = sp.getLong(MedtronicConst.Statistics.LastPrime, 0L)
    //         if (lastPrimeRecord != lastPrimeFromAAPS) {
    //             uploadCareportalEvent(DateTimeUtil.toMillisFromATD(lastPrimeRecord), CareportalEvent.SITECHANGE)
    //             sp.putLong(MedtronicConst.Statistics.LastPrime, lastPrimeRecord)
    //         }
    //     }
    // }

    // private fun uploadCareportalEvent(date: Long, event: String) {
    //     if (databaseHelper.getCareportalEventFromTimestamp(date) != null) return
    //     try {
    //         val data = JSONObject()
    //         val enteredBy: String = sp.getString("careportal_enteredby", "")
    //         if (enteredBy != "") data.put("enteredBy", enteredBy)
    //         data.put("created_at", DateUtil.toISOString(date))
    //         data.put("eventType", event)
    //         val careportalEvent = CareportalEvent(injector)
    //         careportalEvent.date = date
    //         careportalEvent.source = Source.USER
    //         careportalEvent.eventType = event
    //         careportalEvent.json = data.toString()
    //         databaseHelper.createOrUpdate(careportalEvent)
    //         nsUpload.uploadCareportalEntryToNS(data)
    //     } catch (e: JSONException) {
    //         aapsLogger.error("Unhandled exception", e)
    //     }
    // }

    // private fun processTDDs(tddsIn: List<PumpHistoryEntry>) {
    //     val tdds: List<PumpHistoryEntry> = filterTDDs(tddsIn)
    //     aapsLogger.debug(
    //         LTag.PUMP, """
    //  ${logPrefix}TDDs found: {}.
    //  {}
    //  """.trimIndent(), tdds.size, gson().toJson(tdds)
    //     )
    //     val tddsDb: List<TDD> = databaseHelper.getTDDsForLastXDays(3)
    //     for (tdd in tdds) {
    //         val tddDbEntry: TDD? = findTDD(tdd.atechDateTime, tddsDb)
    //         val totalsDTO: DailyTotalsDTO = tdd.decodedData.get("Object") as DailyTotalsDTO
    //
    //         //aapsLogger.debug(LTag.PUMP, "DailyTotals: {}", totalsDTO);
    //         if (tddDbEntry == null) {
    //             val tddNew = TDD()
    //             totalsDTO.setTDD(tddNew)
    //             aapsLogger.debug(LTag.PUMP, "TDD Add: {}", tddNew)
    //             databaseHelper.createOrUpdateTDD(tddNew)
    //         } else {
    //             if (!totalsDTO.doesEqual(tddDbEntry)) {
    //                 totalsDTO.setTDD(tddDbEntry)
    //                 aapsLogger.debug(LTag.PUMP, "TDD Edit: {}", tddDbEntry)
    //                 databaseHelper.createOrUpdateTDD(tddDbEntry)
    //             }
    //         }
    //     }
    // }

    private enum class ProcessHistoryRecord(val description: String) {
        Bolus("Bolus"), TBR("TBR"), Suspend("Suspend");

    }

    // private fun processBolusEntries(entryList: MutableList<PumpHistoryEntry?>) {
    //     val oldestTimestamp = getOldestTimestamp(entryList)
    //     val entriesFromHistory: MutableList<out DbObjectBase?> = getDatabaseEntriesByLastTimestamp(oldestTimestamp, ProcessHistoryRecord.Bolus)
    //     if (doubleBolusDebug) aapsLogger.debug(
    //         LTag.PUMP, "DoubleBolusDebug: List (before filter): {}, FromDb={}", gson().toJson(entryList),
    //         gsonCore().toJson(entriesFromHistory)
    //     )
    //     filterOutAlreadyAddedEntries(entryList, entriesFromHistory)
    //     if (entryList.isEmpty()) {
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: EntryList was filtered out.")
    //         return
    //     }
    //     filterOutNonInsulinEntries(entriesFromHistory)
    //     if (doubleBolusDebug) aapsLogger.debug(
    //         LTag.PUMP, "DoubleBolusDebug: List (after filter): {}, FromDb={}", gson().toJson(entryList),
    //         gsonCore().toJson(entriesFromHistory)
    //     )
    //     if (isCollectionEmpty(entriesFromHistory)) {
    //         for (treatment in entryList) {
    //             aapsLogger.debug(LTag.PUMP, "Add Bolus (no db entry): $treatment")
    //             if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: Add Bolus: FromDb=null, Treatment={}", treatment)
    //             addBolus(treatment, null)
    //         }
    //     } else {
    //         for (treatment in entryList) {
    //             val treatmentDb: DbObjectBase? = findDbEntry(treatment, entriesFromHistory)
    //             aapsLogger.debug(LTag.PUMP, "Add Bolus {} - (entryFromDb={}) ", treatment, treatmentDb)
    //             if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: Add Bolus: FromDb={}, Treatment={}", treatmentDb, treatment)
    //             addBolus(treatment, treatmentDb as Treatment?)
    //         }
    //     }
    // }

    // private fun filterOutNonInsulinEntries(entriesFromHistory: MutableList<out DbObjectBase?>) {
    //     // when we try to pair PumpHistory with AAPS treatments, we need to ignore all non-insulin entries
    //     val removeList: MutableList<DbObjectBase?> = ArrayList<DbObjectBase?>()
    //     for (dbObjectBase in entriesFromHistory) {
    //         val treatment: Treatment = dbObjectBase as Treatment
    //         if (Round.isSame(treatment.insulin, 0.0)) {
    //             removeList.add(dbObjectBase)
    //         }
    //     }
    //     entriesFromHistory.removeAll(removeList)
    // }

    // private fun processTBREntries(entryList: List<PumpHistoryEntry?>) {
    //     Collections.reverse(entryList)
    //     val tbr = entryList[0].getDecodedDataEntry("Object") as TempBasalPair
    //     var readOldItem = false
    //     if (tbr.isCancelTBR) {
    //         val oneMoreEntryFromHistory: PumpHistoryEntry? = getOneMoreEntryFromHistory(PumpHistoryEntryType.TempBasalCombined)
    //         if (oneMoreEntryFromHistory != null) {
    //             entryList.add(0, oneMoreEntryFromHistory)
    //             readOldItem = true
    //         } else {
    //             entryList.removeAt(0)
    //         }
    //     }
    //     val oldestTimestamp = getOldestTimestamp(entryList)
    //     val entriesFromHistory: List<DbObjectBase?> = getDatabaseEntriesByLastTimestamp(oldestTimestamp, ProcessHistoryRecord.TBR)
    //     aapsLogger.debug(
    //         LTag.PUMP, ProcessHistoryRecord.TBR.description + " List (before filter): {}, FromDb={}", gson().toJson(entryList),
    //         gson().toJson(entriesFromHistory)
    //     )
    //     var processDTO: TempBasalProcessDTO? = null
    //     val processList: MutableList<TempBasalProcessDTO> = ArrayList<TempBasalProcessDTO>()
    //     for (treatment in entryList) {
    //         val tbr2 = treatment.getDecodedDataEntry("Object") as TempBasalPair
    //         if (tbr2.isCancelTBR) {
    //             if (processDTO != null) {
    //                 processDTO.itemTwo = treatment
    //                 if (readOldItem) {
    //                     processDTO.processOperation = TempBasalProcessDTO.Operation.Edit
    //                     readOldItem = false
    //                 }
    //             } else {
    //                 aapsLogger.error("processDTO was null - shouldn't happen. ItemTwo={}", treatment)
    //             }
    //         } else {
    //             if (processDTO != null) {
    //                 processList.add(processDTO)
    //             }
    //             processDTO = TempBasalProcessDTO()
    //             processDTO.itemOne = treatment
    //             processDTO.processOperation = TempBasalProcessDTO.Operation.Add
    //         }
    //     }
    //     if (processDTO != null) {
    //         processList.add(processDTO)
    //     }
    //     if (isCollectionNotEmpty(processList)) {
    //         for (tempBasalProcessDTO in processList) {
    //             if (tempBasalProcessDTO.processOperation === TempBasalProcessDTO.Operation.Edit) {
    //                 // edit
    //                 val tempBasal: TemporaryBasal = findTempBasalWithPumpId(tempBasalProcessDTO.itemOne.pumpId, entriesFromHistory)
    //                 if (tempBasal != null) {
    //                     tempBasal.durationInMinutes = tempBasalProcessDTO.getDuration()
    //                     databaseHelper.createOrUpdate(tempBasal)
    //                     aapsLogger.debug(LTag.PUMP, "Edit " + ProcessHistoryRecord.TBR.description + " - (entryFromDb={}) ", tempBasal)
    //                 } else {
    //                     aapsLogger.error("TempBasal not found. Item: {}", tempBasalProcessDTO.itemOne)
    //                 }
    //             } else {
    //                 // add
    //                 val treatment: PumpHistoryEntry = tempBasalProcessDTO.itemOne
    //                 val tbr2 = treatment.decodedData.get("Object") as TempBasalPair
    //                 tbr2.durationMinutes = tempBasalProcessDTO.getDuration()
    //                 val tempBasal: TemporaryBasal = findTempBasalWithPumpId(tempBasalProcessDTO.itemOne.pumpId, entriesFromHistory)
    //                 if (tempBasal == null) {
    //                     val treatmentDb: DbObjectBase? = findDbEntry(treatment, entriesFromHistory)
    //                     aapsLogger.debug(LTag.PUMP, "Add " + ProcessHistoryRecord.TBR.description + " {} - (entryFromDb={}) ", treatment, treatmentDb)
    //                     addTBR(treatment, treatmentDb as TemporaryBasal?)
    //                 } else {
    //                     // this shouldn't happen
    //                     if (tempBasal.durationInMinutes !== tempBasalProcessDTO.getDuration()) {
    //                         aapsLogger.debug(LTag.PUMP, "Found entry with wrong duration (shouldn't happen)... updating")
    //                         tempBasal.durationInMinutes = tempBasalProcessDTO.getDuration()
    //                     }
    //                 }
    //             } // if
    //         } // for
    //     } // collection
    // }

    // private fun findTempBasalWithPumpId(pumpId: Long, entriesFromHistory: List<DbObjectBase?>): TemporaryBasal {
    //     for (dbObjectBase in entriesFromHistory) {
    //         val tbr: TemporaryBasal = dbObjectBase as TemporaryBasal
    //         if (tbr.pumpId === pumpId) {
    //             return tbr
    //         }
    //     }
    //     return databaseHelper.findTempBasalByPumpId(pumpId)
    // }

    /**
     * findDbEntry - finds Db entries in database, while theoretically this should have same dateTime they
     * don't. Entry on pump is few seconds before treatment in AAPS, and on manual boluses on pump there
     * is no treatment at all. For now we look fro tratment that was from 0s - 1m59s within pump entry.
     *
     * @param treatment          Pump Entry
     * @param entriesFromHistory entries from history
     * @return DbObject from AAPS (if found)
     */
    // private fun findDbEntry(treatment: PumpHistoryEntry, entriesFromHistory: List<DbObjectBase?>): DbObjectBase? {
    //     val proposedTime: Long = DateTimeUtil.toMillisFromATD(treatment.atechDateTime)
    //
    //     //proposedTime += (this.pumpTime.timeDifference * 1000);
    //     if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}", treatment, gson().toJson(entriesFromHistory))
    //     if (entriesFromHistory.size == 0) {
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb=null", treatment)
    //         return null
    //     } else if (entriesFromHistory.size == 1) {
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}. Type=SingleEntry", treatment, entriesFromHistory[0])
    //
    //         // TODO: Fix db code
    //         // if difference is bigger than 2 minutes we discard entry
    //         val maxMillisAllowed: Long = DateTimeUtil.getMillisFromATDWithAddedMinutes(treatment.atechDateTime, 2)
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry maxMillisAllowed={}, AtechDateTime={} (add 2 minutes). ", maxMillisAllowed, treatment.atechDateTime)
    //         if (entriesFromHistory[0].getDate() > maxMillisAllowed) {
    //             if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: findDbEntry entry filtered out, returning null. ")
    //             return null
    //         }
    //         return entriesFromHistory[0]
    //     }
    //     var min = 0
    //     while (min < 2) {
    //         var sec = 0
    //         while (sec <= 50) {
    //             if (min == 1 && sec == 50) {
    //                 sec = 59
    //             }
    //             val diff = sec * 1000
    //             val outList: MutableList<DbObjectBase> = ArrayList<DbObjectBase>()
    //             for (treatment1 in entriesFromHistory) {
    //                 if (treatment1.getDate() > proposedTime - diff && treatment1.getDate() < proposedTime + diff) {
    //                     outList.add(treatment1)
    //                 }
    //             }
    //             if (outList.size == 1) {
    //                 if (doubleBolusDebug) aapsLogger.debug(
    //                     LTag.PUMP,
    //                     "DoubleBolusDebug: findDbEntry Treatment={}, FromDb={}. Type=EntrySelected, AtTimeMin={}, AtTimeSec={}",
    //                     treatment,
    //                     entriesFromHistory[0],
    //                     min,
    //                     sec
    //                 )
    //                 return outList[0]
    //             }
    //             if (min == 0 && sec == 10 && outList.size > 1) {
    //                 aapsLogger.error(
    //                     "Too many entries (with too small diff): (timeDiff=[min={},sec={}],count={},list={})",
    //                     min, sec, outList.size, gson().toJson(outList)
    //                 )
    //                 if (doubleBolusDebug) aapsLogger.debug(
    //                     LTag.PUMP, "DoubleBolusDebug: findDbEntry Error - Too many entries (with too small diff): (timeDiff=[min={},sec={}],count={},list={})",
    //                     min, sec, outList.size, gson().toJson(outList)
    //                 )
    //             }
    //             sec += 10
    //         }
    //         min += 1
    //     }
    //     return null
    // }
    //
    // private fun getDatabaseEntriesByLastTimestamp(startTimestamp: Long, processHistoryRecord: ProcessHistoryRecord): MutableList<out DbObjectBase?> {
    //     return if (processHistoryRecord == ProcessHistoryRecord.Bolus) {
    //         activePlugin.getActiveTreatments().getTreatmentsFromHistoryAfterTimestamp(startTimestamp)
    //     } else {
    //         databaseHelper.getTemporaryBasalsDataFromTime(startTimestamp, true)
    //     }
    // }
    //
    // private fun filterOutAlreadyAddedEntries(entryList: MutableList<PumpHistoryEntry?>, treatmentsFromHistory: MutableList<out DbObjectBase?>) {
    //     if (isCollectionEmpty(treatmentsFromHistory)) return
    //     val removeTreatmentsFromHistory: MutableList<DbObjectBase?> = ArrayList<DbObjectBase?>()
    //     val removeTreatmentsFromPH: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
    //     for (treatment in treatmentsFromHistory) {
    //         if (treatment.getPumpId() !== 0) {
    //             var selectedBolus: PumpHistoryEntry? = null
    //             for (bolus in entryList) {
    //                 if (bolus.pumpId == treatment.getPumpId()) {
    //                     selectedBolus = bolus
    //                     break
    //                 }
    //             }
    //             if (selectedBolus != null) {
    //                 entryList.remove(selectedBolus)
    //                 removeTreatmentsFromPH.add(selectedBolus)
    //                 removeTreatmentsFromHistory.add(treatment)
    //             }
    //         }
    //     }
    //     if (doubleBolusDebug) aapsLogger.debug(
    //         LTag.PUMP, "DoubleBolusDebug: filterOutAlreadyAddedEntries: PumpHistory={}, Treatments={}",
    //         gson().toJson(removeTreatmentsFromPH),
    //         gsonCore().toJson(removeTreatmentsFromHistory)
    //     )
    //     treatmentsFromHistory.removeAll(removeTreatmentsFromHistory)
    // }
    //
    // private fun addBolus(bolus: PumpHistoryEntry, treatment: Treatment) {
    //     val bolusDTO: BolusDTO = bolus.decodedData.get("Object") as BolusDTO
    //     if (treatment == null) {
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): Bolus={}", bolusDTO)
    //         when (bolusDTO.bolusType) {
    //             PumpBolusType.Normal                        -> {
    //                 val detailedBolusInfo = DetailedBolusInfo()
    //                 detailedBolusInfo.date = tryToGetByLocalTime(bolus.atechDateTime)
    //                 detailedBolusInfo.source = Source.PUMP
    //                 detailedBolusInfo.pumpId = bolus.pumpId
    //                 detailedBolusInfo.insulin = bolusDTO.deliveredAmount
    //                 addCarbsFromEstimate(detailedBolusInfo, bolus)
    //                 if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): DetailedBolusInfo={}", detailedBolusInfo)
    //                 val newRecord: Boolean = activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, false)
    //                 bolus.linkedObject = detailedBolusInfo
    //                 aapsLogger.debug(
    //                     LTag.PUMP, "addBolus - [date={},pumpId={}, insulin={}, newRecord={}]", detailedBolusInfo.date,
    //                     detailedBolusInfo.pumpId, detailedBolusInfo.insulin, newRecord
    //                 )
    //             }
    //
    //             PumpBolusType.Audio, PumpBolusType.Extended -> {
    //                 val extendedBolus = ExtendedBolus(injector)
    //                 extendedBolus.date = tryToGetByLocalTime(bolus.atechDateTime)
    //                 extendedBolus.source = Source.PUMP
    //                 extendedBolus.insulin = bolusDTO.deliveredAmount
    //                 extendedBolus.pumpId = bolus.pumpId
    //                 extendedBolus.isValid = true
    //                 extendedBolus.durationInMinutes = bolusDTO.duration
    //                 bolus.linkedObject = extendedBolus
    //                 if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament==null): ExtendedBolus={}", extendedBolus)
    //                 activePlugin.getActiveTreatments().addToHistoryExtendedBolus(extendedBolus)
    //                 aapsLogger.debug(
    //                     LTag.PUMP, "addBolus - Extended [date={},pumpId={}, insulin={}, duration={}]", extendedBolus.date,
    //                     extendedBolus.pumpId, extendedBolus.insulin, extendedBolus.durationInMinutes
    //                 )
    //             }
    //         }
    //     } else {
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(OldTreatment={}): Bolus={}", treatment, bolusDTO)
    //         treatment.source = Source.PUMP
    //         treatment.pumpId = bolus.pumpId
    //         treatment.insulin = bolusDTO.deliveredAmount
    //         val updateReturn: TreatmentUpdateReturn = activePlugin.getActiveTreatments().createOrUpdateMedtronic(treatment, false)
    //         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addBolus(tretament!=null): NewTreatment={}, UpdateReturn={}", treatment, updateReturn)
    //         aapsLogger.debug(
    //             LTag.PUMP, "editBolus - [date={},pumpId={}, insulin={}, newRecord={}]", treatment.date,
    //             treatment.pumpId, treatment.insulin, updateReturn.toString()
    //         )
    //         bolus.linkedObject = treatment
    //     }
    // }

    private fun addCarbsFromEstimate(detailedBolusInfo: DetailedBolusInfo, bolus: PumpHistoryEntry) {
        if (bolus.containsDecodedData("Estimate")) {
            val bolusWizard: BolusWizardDTO = bolus.decodedData.get("Estimate") as BolusWizardDTO
            if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: addCarbsFromEstimate: Bolus={}, BolusWizardDTO={}", bolus, bolusWizard)
            detailedBolusInfo.carbs = bolusWizard.carbs.toDouble()
        }
    }

    // private fun addTBR(treatment: PumpHistoryEntry, temporaryBasalDbInput: PumpSync.PumpState.TemporaryBasal?) {
    //     val tbr = treatment.decodedData.get("Object") as TempBasalPair
    //     var temporaryBasalDb: PumpSync.PumpState.TemporaryBasal? = temporaryBasalDbInput
    //     var operation = "editTBR"
    //     if (temporaryBasalDb == null) {
    //         temporaryBasalDb = PumpSync.PumpState.TemporaryBasal()
    //         temporaryBasalDb. = tryToGetByLocalTime(treatment.atechDateTime)
    //         operation = "addTBR"
    //     }
    //     temporaryBasalDb.source = Source.PUMP
    //     temporaryBasalDb.pumpId = treatment.pumpId
    //     temporaryBasalDb.durationInMinutes = tbr.durationMinutes
    //     temporaryBasalDb.absoluteRate = tbr.insulinRate
    //     temporaryBasalDb.isAbsolute = !tbr.isPercent
    //     treatment.linkedObject = temporaryBasalDb
    //     databaseHelper.createOrUpdate(temporaryBasalDb)
    //     aapsLogger.debug(
    //         LTag.PUMP, "$operation - [date={},pumpId={}, rate={} {}, duration={}]",  //
    //         temporaryBasalDb.date,  //
    //         temporaryBasalDb.pumpId,  //
    //         if (temporaryBasalDb.isAbsolute) java.lang.String.format(Locale.ENGLISH, "%.2f", temporaryBasalDb.absoluteRate) else java.lang.String.format(
    //             Locale.ENGLISH,
    //             "%d",
    //             temporaryBasalDb.percentRate
    //         ),  //
    //         if (temporaryBasalDb.isAbsolute) "U/h" else "%",  //
    //         temporaryBasalDb.durationInMinutes
    //     )
    // }
    //
    // private fun processSuspends(tempBasalProcessList: List<TempBasalProcessDTO>) {
    //     for (tempBasalProcess in tempBasalProcessList) {
    //         var tempBasal: TemporaryBasal = databaseHelper.findTempBasalByPumpId(tempBasalProcess.itemOne.pumpId)
    //         if (tempBasal == null) {
    //             // add
    //             tempBasal = TemporaryBasal(injector)
    //             tempBasal.date = tryToGetByLocalTime(tempBasalProcess.itemOne.atechDateTime)
    //             tempBasal.source = Source.PUMP
    //             tempBasal.pumpId = tempBasalProcess.itemOne.pumpId
    //             tempBasal.durationInMinutes = tempBasalProcess.getDuration()
    //             tempBasal.absoluteRate = 0.0
    //             tempBasal.isAbsolute = true
    //             tempBasalProcess.itemOne.linkedObject = tempBasal
    //             tempBasalProcess.itemTwo.linkedObject = tempBasal
    //             databaseHelper.createOrUpdate(tempBasal)
    //         }
    //     }
    // }
    //
    // // suspend/resume
    // // no_delivery/prime & rewind/prime
    // private val suspends: List<Any>
    //     private get() {
    //         val outList: MutableList<TempBasalProcessDTO> = ArrayList<TempBasalProcessDTO>()
    //
    //         // suspend/resume
    //         outList.addAll(suspendResumeRecords)
    //         // no_delivery/prime & rewind/prime
    //         outList.addAll(noDeliveryRewindPrimeRecords)
    //         return outList
    //     }// remove last and have paired items// remove last (unpaired R)// get one more from history (R S R) -> ([S] R S R)// remove last (unpaired R)// not full suspends, need to retrive one more record and discard first one (R S R S) -> ([S] R S R [xS])// full resume suspends (S R S R)

    //
    //
    // private val suspendResumeRecords: List<Any>
    //     private get() {
    //         val filteredItems: MutableList<PumpHistoryEntry> = getFilteredItems(
    //             newHistory,  //
    //             PumpHistoryEntryType.SuspendPump,  //
    //             PumpHistoryEntryType.ResumePump
    //         )
    //         val outList: MutableList<TempBasalProcessDTO> = ArrayList<TempBasalProcessDTO>()
    //         if (filteredItems.size > 0) {
    //             val filtered2Items: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
    //             if (filteredItems.size % 2 == 0 && filteredItems[0].entryType === PumpHistoryEntryType.ResumePump) {
    //                 // full resume suspends (S R S R)
    //                 filtered2Items.addAll(filteredItems)
    //             } else if (filteredItems.size % 2 == 0 && filteredItems[0].entryType === PumpHistoryEntryType.SuspendPump) {
    //                 // not full suspends, need to retrive one more record and discard first one (R S R S) -> ([S] R S R [xS])
    //                 filteredItems.drop(0)
    //                 val oneMoreEntryFromHistory: PumpHistoryEntry? = getOneMoreEntryFromHistory(PumpHistoryEntryType.SuspendPump)
    //                 if (oneMoreEntryFromHistory != null) {
    //                     filteredItems += (oneMoreEntryFromHistory)
    //                 } else {
    //                     filteredItems.removeAt(filteredItems.size - 1) // remove last (unpaired R)
    //                 }
    //                 filtered2Items.addAll(filteredItems)
    //             } else {
    //                 if (filteredItems[0].entryType === PumpHistoryEntryType.ResumePump) {
    //                     // get one more from history (R S R) -> ([S] R S R)
    //                     val oneMoreEntryFromHistory: PumpHistoryEntry? = getOneMoreEntryFromHistory(PumpHistoryEntryType.SuspendPump)
    //                     if (oneMoreEntryFromHistory != null) {
    //                         filteredItems.add(oneMoreEntryFromHistory)
    //                     } else {
    //                         filteredItems.removeAt(filteredItems.size - 1) // remove last (unpaired R)
    //                     }
    //                     filtered2Items.addAll(filteredItems)
    //                 } else {
    //                     // remove last and have paired items
    //                     filteredItems.removeAt(0)
    //                     filtered2Items.addAll(filteredItems)
    //                 }
    //             }
    //             if (filtered2Items.size > 0) {
    //                 sort(filtered2Items)
    //                 Collections.reverse(filtered2Items)
    //                 var i = 0
    //                 while (i < filtered2Items.size) {
    //                     val dto = TempBasalProcessDTO()
    //                     dto.itemOne = filtered2Items[i]
    //                     dto.itemTwo = filtered2Items[i + 1]
    //                     dto.processOperation = TempBasalProcessDTO.Operation.Add
    //                     outList.add(dto)
    //                     i += 2
    //                 }
    //             }
    //         }
    //         return outList
    //     }//////////
    //
    // //
    // private val noDeliveryRewindPrimeRecords: List<Any>
    //     private get() {
    //         val primeItems: List<PumpHistoryEntry> = getFilteredItems(
    //             newHistory,  //
    //             PumpHistoryEntryType.Prime
    //         )
    //         val outList: MutableList<TempBasalProcessDTO> = ArrayList<TempBasalProcessDTO>()
    //         if (primeItems.size == 0) return outList
    //         val filteredItems: List<PumpHistoryEntry> = getFilteredItems(
    //             newHistory,  //
    //             PumpHistoryEntryType.Prime,
    //             PumpHistoryEntryType.Rewind,
    //             PumpHistoryEntryType.NoDeliveryAlarm,
    //             PumpHistoryEntryType.Bolus,
    //             PumpHistoryEntryType.TempBasalCombined
    //         )
    //         val tempData: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
    //         var startedItems = false
    //         var finishedItems = false
    //         for (filteredItem in filteredItems) {
    //             if (filteredItem.entryType === PumpHistoryEntryType.Prime) {
    //                 startedItems = true
    //             }
    //             if (startedItems) {
    //                 if (filteredItem.entryType === PumpHistoryEntryType.Bolus ||
    //                     filteredItem.entryType === PumpHistoryEntryType.TempBasalCombined
    //                 ) {
    //                     finishedItems = true
    //                     break
    //                 }
    //                 tempData.add(filteredItem)
    //             }
    //         }
    //         if (!finishedItems) {
    //             val filteredItemsOld: List<PumpHistoryEntry> = getFilteredItems(
    //                 allHistory,  //
    //                 PumpHistoryEntryType.Rewind,
    //                 PumpHistoryEntryType.NoDeliveryAlarm,
    //                 PumpHistoryEntryType.Bolus,
    //                 PumpHistoryEntryType.TempBasalCombined
    //             )
    //             for (filteredItem in filteredItemsOld) {
    //                 if (filteredItem.entryType === PumpHistoryEntryType.Bolus ||
    //                     filteredItem.entryType === PumpHistoryEntryType.TempBasalCombined
    //                 ) {
    //                     finishedItems = true
    //                     break
    //                 }
    //                 tempData.add(filteredItem)
    //             }
    //         }
    //         if (!finishedItems) {
    //             showLogs("NoDeliveryRewindPrimeRecords: Not finished Items: ", gson().toJson(tempData))
    //             return outList
    //         }
    //         showLogs("NoDeliveryRewindPrimeRecords: Records to evaluate: ", gson().toJson(tempData))
    //         var items: List<PumpHistoryEntry> = getFilteredItems(
    //             tempData,  //
    //             PumpHistoryEntryType.Prime
    //         )
    //         val processDTO = TempBasalProcessDTO()
    //         processDTO.itemTwo = items[0]
    //         items = getFilteredItems(
    //             tempData,  //
    //             PumpHistoryEntryType.NoDeliveryAlarm
    //         )
    //         if (items.size > 0) {
    //             processDTO.itemOne = items[items.size - 1]
    //             processDTO.processOperation = TempBasalProcessDTO.Operation.Add
    //             outList.add(processDTO)
    //             return outList
    //         }
    //         items = getFilteredItems(
    //             tempData,  //
    //             PumpHistoryEntryType.Rewind
    //         )
    //         if (items.size > 0) {
    //             processDTO.itemOne = items[0]
    //             processDTO.processOperation = TempBasalProcessDTO.Operation.Add
    //             outList.add(processDTO)
    //             return outList
    //         }
    //         return outList
    //     }

    private fun getOneMoreEntryFromHistory(entryType: PumpHistoryEntryType): PumpHistoryEntry? {
        val filteredItems: List<PumpHistoryEntry> = getFilteredItems(allHistory, entryType)
        return if (filteredItems.size == 0) null else filteredItems[0]
    }

    private fun filterTDDs(tdds: List<PumpHistoryEntry>): List<PumpHistoryEntry> {
        val tddsOut: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
        for (tdd in tdds) {
            if (tdd.entryType !== PumpHistoryEntryType.EndResultTotals) {
                tddsOut.add(tdd)
            }
        }
        return if (tddsOut.size == 0) tdds else tddsOut
    }

    // private fun findTDD(atechDateTime: Long, tddsDb: List<TDD>): TDD? {
    //     for (tdd in tddsDb) {
    //         if (DateTimeUtil.isSameDayATDAndMillis(atechDateTime, tdd.date)) {
    //             return tdd
    //         }
    //     }
    //     return null
    // }
    //
    // private fun tryToGetByLocalTime(atechDateTime: Long): Long {
    //     return DateTimeUtil.toMillisFromATD(atechDateTime)
    // }

//     private fun getOldestDateDifference(treatments: List<PumpHistoryEntry>): Int {
//         var dt = Long.MAX_VALUE
//         var currentTreatment: PumpHistoryEntry? = null
//         if (isCollectionEmpty(treatments)) {
//             return 8 // default return of 6 (5 for diif on history reading + 2 for max allowed difference) minutes
//         }
//         for (treatment in treatments) {
//             if (treatment.atechDateTime < dt) {
//                 dt = treatment.atechDateTime
//                 currentTreatment = treatment
//             }
//         }
//         var oldestEntryTime: LocalDateTime
//         try {
//             oldestEntryTime = DateTimeUtil.toLocalDateTime(dt)
//             oldestEntryTime = oldestEntryTime.minusMinutes(3)
//
// //            if (this.pumpTime.timeDifference < 0) {
// //                oldestEntryTime = oldestEntryTime.plusSeconds(this.pumpTime.timeDifference);
// //            }
//         } catch (ex: Exception) {
//             aapsLogger.error("Problem decoding date from last record: {}$currentTreatment")
//             return 8 // default return of 6 minutes
//         }
//         val now = LocalDateTime()
//         val minutes = Minutes.minutesBetween(oldestEntryTime, now)
//
//         // returns oldest time in history, with calculated time difference between pump and phone, minus 5 minutes
//         aapsLogger.debug(
//             LTag.PUMP, "Oldest entry: {}, pumpTimeDifference={}, newDt={}, currentTime={}, differenceMin={}", dt,
//             pumpTime.timeDifference, oldestEntryTime, now, minutes.minutes
//         )
//         return minutes.minutes
//     }
//
//     private fun getOldestTimestamp(treatments: List<PumpHistoryEntry?>): Long {
//         var dt = Long.MAX_VALUE
//         var currentTreatment: PumpHistoryEntry? = null
//         for (treatment in treatments) {
//             if (treatment.atechDateTime < dt) {
//                 dt = treatment.atechDateTime
//                 currentTreatment = treatment
//             }
//         }
//         if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: getOldestTimestamp. Oldest entry found: time={}, object={}", dt, currentTreatment)
//         return try {
//             val oldestEntryTime: GregorianCalendar = DateTimeUtil.toGregorianCalendar(dt)
//             if (doubleBolusDebug) aapsLogger.debug(LTag.PUMP, "DoubleBolusDebug: getOldestTimestamp. oldestEntryTime: {}", DateTimeUtil.toString(oldestEntryTime))
//             oldestEntryTime.add(Calendar.MINUTE, -2)
//             if (doubleBolusDebug) aapsLogger.debug(
//                 LTag.PUMP,
//                 "DoubleBolusDebug: getOldestTimestamp. oldestEntryTime (-2m): {}, timeInMillis={}",
//                 DateTimeUtil.toString(oldestEntryTime),
//                 oldestEntryTime.timeInMillis
//             )
//             oldestEntryTime.timeInMillis
//         } catch (ex: Exception) {
//             aapsLogger.error("Problem decoding date from last record: {}", currentTreatment)
//             8 // default return of 6 minutes
//         }
//     }
//
//     private val tDDType: PumpHistoryEntryType
//         private get() = if (medtronicUtil.medtronicPumpModel == null) {
//             PumpHistoryEntryType.EndResultTotals
//         } else when (medtronicUtil.medtronicPumpModel) {
//             MedtronicDeviceType.Medtronic_515, MedtronicDeviceType.Medtronic_715                                                                                           -> PumpHistoryEntryType.DailyTotals515
//             MedtronicDeviceType.Medtronic_522, MedtronicDeviceType.Medtronic_722                                                                                           -> PumpHistoryEntryType.DailyTotals522
//             MedtronicDeviceType.Medtronic_523_Revel, MedtronicDeviceType.Medtronic_723_Revel, MedtronicDeviceType.Medtronic_554_Veo, MedtronicDeviceType.Medtronic_754_Veo -> PumpHistoryEntryType.DailyTotals523
//
//             else                                                                                                                                                           -> {
//                 PumpHistoryEntryType.EndResultTotals
//             }
//         }

    // override fun hasBasalProfileChanged(): Boolean {
    //     val filteredItems: List<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile)
    //     aapsLogger.debug(LTag.PUMP, "hasBasalProfileChanged. Items: " + gson().toJson(filteredItems))
    //     return filteredItems.size > 0
    // }

    fun processLastBasalProfileChange(pumpType: PumpType?, mdtPumpStatus: MedLinkMedtronicPumpStatus) {
        val filteredItems: List<PumpHistoryEntry> = getFilteredItems(PumpHistoryEntryType.ChangeBasalProfile_NewProfile)
        aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. Items: $filteredItems")
        var newProfile: PumpHistoryEntry? = null
        var lastDate: Long? = null
        if (filteredItems.size == 1) {
            newProfile = filteredItems[0]
        } else if (filteredItems.size > 1) {
            for (filteredItem in filteredItems) {
                if (lastDate == null || lastDate < filteredItem.atechDateTime) {
                    newProfile = filteredItem
                    lastDate = newProfile.atechDateTime
                }
            }
        }
        if (newProfile != null) {
            aapsLogger.debug(LTag.PUMP, "processLastBasalProfileChange. item found, setting new basalProfileLocally: $newProfile")
            val basalProfile = newProfile.decodedData.get("Object") as BasalProfile
            mdtPumpStatus.basalsByHour = basalProfile.getProfilesByHour(pumpType!!)
        }
    }

    // override fun hasPumpTimeChanged(): Boolean {
    //     return getStateFromFilteredList(
    //         PumpHistoryEntryType.NewTimeSet,  //
    //         PumpHistoryEntryType.ChangeTime
    //     )
    // }

    fun setLastHistoryRecordTime(lastHistoryRecordTime: Long?) {

        // this.previousLastHistoryRecordTime = this.lastHistoryRecordTime;
    }

    // override fun setIsInInit(init: Boolean) {
    //     isInit = init
    // }

    // HELPER METHODS
    private fun sort(list: List<PumpHistoryEntry>?) {
        Collections.sort(list, PumpHistoryEntry.Comparator())
    }

    // private fun preProcessTBRs(TBRs_Input: List<PumpHistoryEntry?>): MutableList<PumpHistoryEntry?> {
    //     val TBRs: MutableList<PumpHistoryEntry?> = ArrayList<PumpHistoryEntry?>()
    //     val map: MutableMap<String, PumpHistoryEntry> = HashMap<String, PumpHistoryEntry>()
    //     for (pumpHistoryEntry in TBRs_Input) {
    //         if (map.containsKey(pumpHistoryEntry.DT)) {
    //             medtronicPumpHistoryDecoder.decodeTempBasal(map[pumpHistoryEntry.DT], pumpHistoryEntry)
    //             pumpHistoryEntry.setEntryType(medtronicUtil.medtronicPumpModel, PumpHistoryEntryType.TempBasalCombined)
    //             TBRs.add(pumpHistoryEntry)
    //             map.remove(pumpHistoryEntry.DT)
    //         } else {
    //             map[pumpHistoryEntry.DT] = pumpHistoryEntry
    //         }
    //     }
    //     return TBRs
    // }

    private fun getFilteredItems(vararg entryTypes: PumpHistoryEntryType): List<PumpHistoryEntry> {
        return getFilteredItems(newHistory, *entryTypes)
    }

    private fun getStateFromFilteredList(vararg entryTypes: PumpHistoryEntryType): Boolean {
        return if (isInit) {
            false
        } else {
            val filteredItems: List<PumpHistoryEntry> = getFilteredItems(*entryTypes)
            aapsLogger.debug(LTag.PUMP, "Items: $filteredItems")
            filteredItems.size > 0
        }
    }

    private fun getFilteredItems(inList: List<PumpHistoryEntry>, vararg entryTypes: PumpHistoryEntryType): MutableList<PumpHistoryEntry> {

        // aapsLogger.debug(LTag.PUMP, "InList: " + inList.size());
        val outList: MutableList<PumpHistoryEntry> = ArrayList<PumpHistoryEntry>()
        if (inList != null && inList.isNotEmpty()) {
            for (pumpHistoryEntry in inList) {
                if (!isEmpty(*entryTypes)) {
                    for (pumpHistoryEntryType in entryTypes) {
                        if (pumpHistoryEntry.entryType === pumpHistoryEntryType) {
                            outList.add(pumpHistoryEntry)
                            break
                        }
                    }
                } else {
                    outList.add(pumpHistoryEntry)
                }
            }
        }

        // aapsLogger.debug(LTag.PUMP, "OutList: " + outList.size());
        return outList
    }

    private fun isEmpty(vararg entryTypes: PumpHistoryEntryType?): Boolean {
        return entryTypes == null || entryTypes.size == 1 && entryTypes[0] == null
    }

    private val logPrefix: String
        private get() = "MedtronicHistoryData::"

    companion object {

        /**
         * Double bolus debug. We seem to have small problem with double Boluses (or sometimes also missing boluses
         * from history. This flag turns on debugging for that (default is off=false)... Debuging is pretty detailed,
         * so log files will get bigger.
         * Note: June 2020. Since this seems to be fixed, I am disabling this per default. I will leave code inside
         * in case we need it again. Code that turns this on is commented out RileyLinkMedtronicService#verifyConfiguration()
         */
        const val doubleBolusDebug = false
    }

    init {
        allHistory = ArrayList<PumpHistoryEntry>()
        // this.injector = injector
        // this.aapsLogger = aapsLogger
        // this.sp = sp
        // this.activePlugin = activePlugin
        // this.nsUpload = nsUpload
        // this.medtronicUtil = medtronicUtil
        // this.medtronicPumpHistoryDecoder = medtronicPumpHistoryDecoder
        // databaseHelper = databaseHelperInterface
    }
}