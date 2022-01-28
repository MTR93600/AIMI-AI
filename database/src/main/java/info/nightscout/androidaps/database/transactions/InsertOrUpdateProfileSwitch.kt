package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ProfileSwitch

class InsertOrUpdateProfileSwitch(val profileSwitch: ProfileSwitch) : Transaction<InsertOrUpdateProfileSwitch.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current = database.profileSwitchDao.findById(profileSwitch.id)
        if (current == null) {
            database.profileSwitchDao.insertNewEntry(profileSwitch)
            result.inserted.add(profileSwitch)
        } else {
            database.profileSwitchDao.updateExistingEntry(profileSwitch)
            result.updated.add(profileSwitch)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<ProfileSwitch>()
        val updated = mutableListOf<ProfileSwitch>()
    }
}