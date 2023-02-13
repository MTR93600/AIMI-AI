package info.nightscout.androidaps.interfaces

import info.nightscout.interfaces.pump.InMemoryMedLinkConfig

/**
 * This interface allows pump drivers to push data changes (creation and update of treatments, temporary basals and extended boluses) back to AAPS-core.
 *
 * Intended use cases for handling bolus treatments:
 *
 *  - for pumps that have a reliable history that can be read and which therefore issue a bolus on the pump,
 *    read the history back and add new bolus entries on the pump, the method [syncBolusWithPumpId]
 *    are used to inform AAPS-core of a new bolus.
 *    [info.nightscout.androidaps.danars.DanaRSPlugin] is a pump driver that
 *    takes this approach.
 *  - for pumps that don't support history or take rather long to complete a bolus, the methods
 *    [addBolusWithTempId] and [syncBolusWithTempId] provide a mechanism to notify AAPS-core of a started
 *    bolus, so AAPS-core can operate under the assumption the bolus will be delivered and effect IOB until delivery
 *    completed. Upon completion, the pump driver will call the second method to turn a temporary bolus into a finished
 *    bolus.
 */
interface MedLinkSync {

    /**
     *  Reset stored identification of last used pump
     *
     *  Call this function when new pump is paired to accept data from new pump
     *  to prevent overlapping pump histories
     * @param endRunning    if true end previous running TBR and EB
     */

    // @JvmOverloads and default value impossible on interface
    // replace by `fun connectNewPump(endRunning: Boolean = true)` after full conversion to kotlin
    fun connectNewPump(endRunning: Boolean,pumpId: String)
    fun connectNewPump(pumpId: String) = connectNewPump(true, pumpId)

    /*
     *   GENERAL STATUS
     */

    /**
     *  Query expected pump state
     *
     *  Driver may query AAPS for expecting state of the pump and use it for sanity check
     *  or generation of status for NS
     *
     *  TemporaryBasal
     *  duration in milliseconds
     *  rate in U/h or % where 100% is equal to no TBR
     *
     *  ExtendedBolus
     *  duration in milliseconds
     *  amount in U
     *  rate in U/h (synthetic only)
     *
     *  @return         data from database.
     *                  temporaryBasal (and extendedBolus) is null if there is no record in progress based on data in database
     *                  bolus is null when there is no record in database
     */
    data class PumpState(val medLinkConfig: MedLinkConfig? , val serialNumber: String) {

        data class MedLinkConfig(val timestamp: Long, val frequency: Int)
    }


    /*
     *   BOLUSES & CARBS
     */

    /**
     * Create bolus with temporary id
     *
     * Search for combination of  temporaryId, PumpType, pumpSerial
     *
     * If db record doesn't exist, new record is created.
     * If exists false is returned and data is ignored
     *
     * USAGE:
     * Generate unique temporaryId
     * Call before bolus when no pumpId is known (provide timestamp, amount, temporaryId, type, pumpType, pumpSerial)
     * After reading record from history or completed bolus call [syncBolusWithTempId] with the same temporaryId provided
     * If syncBolusWithTempId is not called afterwards record remains valid and is calculated towards iob
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of insulin
     * @param temporaryId   temporary id generated when pump id in not know yet
     * @param type          type of bolus (NORMAL, SMB, PRIME)
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/
    fun addConfigWithTempId(timestamp: Long, frequency: Int, temporaryId: Long): Boolean

    fun findLatestConfig(): InMemoryMedLinkConfig
    fun findMostCommonFrequencies(): List<Int>
}