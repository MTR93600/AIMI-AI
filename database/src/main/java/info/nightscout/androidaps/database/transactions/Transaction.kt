package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.DelegatedAppDatabase

/**
 * Base class for database transactions
 * @param T The return type of the Transaction
 */
abstract class Transaction<T> {

    /**
     * Executes the Transaction
     */
    internal abstract fun run(): T

    internal lateinit var database: DelegatedAppDatabase

}