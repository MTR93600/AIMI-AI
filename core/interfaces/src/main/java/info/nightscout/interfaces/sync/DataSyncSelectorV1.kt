package info.nightscout.interfaces.sync

interface DataSyncSelectorV1 : DataSyncSelector {

    fun confirmLastBolusIdIfGreater(lastSynced: Long)
    suspend fun processChangedBoluses()

    suspend fun processChangedCarbs()
    fun confirmLastCarbsIdIfGreater(lastSynced: Long)

    fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long)
    suspend fun processChangedBolusCalculatorResults()

    fun confirmLastTempTargetsIdIfGreater(lastSynced: Long)
    suspend fun processChangedTempTargets()

    fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long)
    suspend fun processChangedGlucoseValues()

    fun confirmLastTherapyEventIdIfGreater(lastSynced: Long)
    suspend fun processChangedTherapyEvents()

    fun confirmLastFoodIdIfGreater(lastSynced: Long)
    suspend fun processChangedFoods()

    fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long)
    suspend fun processChangedDeviceStatuses()

    fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long)
    suspend fun processChangedTemporaryBasals()

    fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long)
    suspend fun processChangedExtendedBoluses()

    fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long)
    suspend fun processChangedProfileSwitches()

    fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long)
    suspend fun processChangedEffectiveProfileSwitches()

    fun confirmLastOfflineEventIdIfGreater(lastSynced: Long)
    suspend fun processChangedOfflineEvents()

    fun confirmLastProfileStore(lastSynced: Long)
    suspend fun processChangedProfileStore()
}