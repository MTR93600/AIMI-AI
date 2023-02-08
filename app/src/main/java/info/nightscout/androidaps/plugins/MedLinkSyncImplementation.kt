// package info.nightscout.androidaps.plugins
//
// import info.nightscout.androidaps.database.transactions.InsertOrUpdateMedLinkConfigTransaction
// import info.nightscout.androidaps.interfaces.MedLinkSync
// import info.nightscout.database.ValueWrapper
// import info.nightscout.database.impl.AppRepository
// import info.nightscout.interfaces.pump.InMemoryMedLinkConfig
// import info.nightscout.rx.logging.AAPSLogger
// import info.nightscout.rx.logging.LTag
// import javax.inject.Inject
//
// class MedLinkSyncImplementation @Inject constructor(
//     val aapsLogger: AAPSLogger,
//     private val repository: AppRepository,
// ) : MedLinkSync {
//
//     override fun connectNewPump(endRunning: Boolean, pumpId: String) {
//         TODO("Not yet implemented")
//     }
//
//     override fun addConfigWithTempId(timestamp: Long, frequency: Int, temporaryId: Long): Boolean {
//         val config = MedLinkSync.PumpState.MedLinkConfig(referenceId = timestamp, currentFrequency = frequency)
//         repository.runTransactionForResult(InsertOrUpdateMedLinkConfigTransaction(config))
//             .doOnError { aapsLogger.error(LTag.DATABASE, "Error while saving MedLinkConfig", it) }
//             .blockingGet()
//             .also { result ->
//                 result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted MedLinkConfig $it") }
//                 return result.inserted.size > 0
//             }
//     }
//
//     override fun findLatestConfig(): InMemoryMedLinkConfig {
//         val lastDbMedLinkConfig = repository.getLastConfig().blockingGet()
//         return if (lastDbMedLinkConfig is ValueWrapper.Existing) InMemoryMedLinkConfig(lastDbMedLinkConfig.value) else {
//             InMemoryMedLinkConfig(
//                 id = System.currentTimeMillis(),
//                 currentFrequency = 0
//             )
//         }
//     }
//
//     override fun findMostCommonFrequencies(): List<Int> {
//         val values = repository.getMostCommonFrequencies().blockingGet()
//         return if (values is ValueWrapper.Existing) values.value else emptyList()
//
//     }
// }