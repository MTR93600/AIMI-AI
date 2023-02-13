package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Food

/**
 * Inserts or updates the Food
 */
class InsertOrUpdateFoodTransaction(private val food: Food) : Transaction<InsertOrUpdateFoodTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.foodDao.findById(food.id)
        if (current == null) {
            database.foodDao.insertNewEntry(food)
            result.inserted.add(food)
        } else {
            database.foodDao.updateExistingEntry(food)
            result.updated.add(food)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Food>()
        val updated = mutableListOf<Food>()
    }
}