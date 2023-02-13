package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import com.google.gson.annotations.Expose
import java.util.concurrent.LinkedBlockingDeque


/**
 * used by medlink
 */
class TempBasalMicrobolusOperations {

    private var shouldBeSuspended = false
    var durationInMinutes = 0
    var absoluteRate = 0.0

    //    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);
    var operations = LinkedBlockingDeque<TempBasalMicroBolusPair>()
    @Expose var remainingOperations = operations.size
        private set
    @Expose var totalDosage = 0.0
        private set

    @Expose
    private val nextOperationInterval = 0

    @Expose
    private var suspendedTime: Int? = null

    constructor() {}
    constructor(
        remainingOperations: Int, totalDosage: Double,
        durationInMinutes: Int,
        operations: LinkedBlockingDeque<TempBasalMicroBolusPair>
    ) {
        this.remainingOperations = remainingOperations
        this.totalDosage = totalDosage
        this.operations = operations
        this.durationInMinutes = durationInMinutes
    }

    override fun toString(): String {
        return "TempBasalMicrobolusOperations{" +
            "remainingOperations=" + remainingOperations +
            ", operationDose=" + totalDosage +
            ", nextOperationInterval=" + nextOperationInterval +
            ", operations=" + operations +
            '}'
    }

    @Synchronized fun updateOperations(
        remainingOperations: Int,
        operationDose: Double,
        operations: LinkedBlockingDeque<TempBasalMicroBolusPair>,
        suspendedTime: Int?
    ) {
        this.remainingOperations = remainingOperations
        this.suspendedTime = suspendedTime
        totalDosage = operationDose
        this.operations = operations
    }

    @Synchronized fun clearOperations() {
        operations.clear()
    }

    fun setShouldBeSuspended(suspended: Boolean) {
        shouldBeSuspended = suspended
    }

    fun shouldBeSuspended(): Boolean {
        return shouldBeSuspended
    }
}