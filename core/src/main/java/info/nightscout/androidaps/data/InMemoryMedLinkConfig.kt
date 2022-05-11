package info.nightscout.androidaps.data

import info.nightscout.androidaps.database.entities.MedLinkConfig

class InMemoryMedLinkConfig constructor(var id: Long = 0L, var currentFrequency: Int = 0) {

    constructor(medLinkConfig: MedLinkConfig) : this(medLinkConfig.id, medLinkConfig.currentFrequency)
    // var generated : value doesn't correspond to real value with timestamp close to real BG

    fun getFrequency() = getFrequency(currentFrequency)
    fun getFrequency(currentFrequency: Int) = when {
        currentFrequency > 9  -> null
        currentFrequency < -9 -> null
        currentFrequency == 0 -> "" + currentFrequency
        currentFrequency > 0  -> "+$currentFrequency"
        else                  -> "$currentFrequency"
    }

    fun getReverseFrequency() = when {
        currentFrequency == 0 -> "" + currentFrequency
        currentFrequency > 0  -> "-$currentFrequency"
        else                  -> "+${currentFrequency*-1}"
    }

    fun getNearestNeighbors() = getNearestNeighbors(currentFrequency)
    fun getNearestNeighbors(freq: Int) = if (freq == 0) listOf<String>("-1", "+1") else listOf(getFrequency(freq + 1), getFrequency(freq - 1))
}