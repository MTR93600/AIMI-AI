package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data

import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType

data class HistoryRecord(
    val id: Long,
    val createdAt: Long, // creation date of the record
    val date: Long, // when event actually happened
    val commandType: OmnipodCommandType,
    val initialResult: InitialResult,
    val record: Record?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?
) {
    fun pumpId(): Long {
        return id
    }

    fun displayTimestamp(): Long {
        return date
    }

    fun isSuccess(): Boolean {
        return initialResult == InitialResult.SENT && resolvedResult == ResolvedResult.SUCCESS
    }
}
