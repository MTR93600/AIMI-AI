package info.nightscout.androidaps.plugins.general.smsCommunicator

abstract class SmsAction(val pumpCommand: Boolean) : Runnable {

    var aDouble: Double? = null
    var anInteger: Int? = null
    var secondInteger: Int? = null
    var secondLong: Long? = null
    var aString: String? = null

    internal constructor(pumpCommand: Boolean, aDouble: Double) : this(pumpCommand) {
        this.aDouble = aDouble
    }

    internal constructor(pumpCommand: Boolean, aDouble: Double, secondInteger: Int) : this(pumpCommand) {
        this.aDouble = aDouble
        this.secondInteger = secondInteger
    }

    internal constructor(pumpCommand: Boolean, aString: String, secondInteger: Int) : this(pumpCommand) {
        this.aString = aString
        this.secondInteger = secondInteger
    }

    internal constructor(pumpCommand: Boolean, anInteger: Int) : this(pumpCommand) {
        this.anInteger = anInteger
    }

    internal constructor(pumpCommand: Boolean, anInteger: Int, secondInteger: Int) : this(pumpCommand) {
        this.anInteger = anInteger
        this.secondInteger = secondInteger
    }

    internal constructor(pumpCommand: Boolean, anInteger: Int, secondLong: Long) : this(pumpCommand) {
        this.anInteger = anInteger
        this.secondLong = secondLong
    }

    fun aDouble(): Double {
        return aDouble?.let {
            aDouble
        } ?: throw IllegalStateException()
    }

    fun anInteger(): Int {
        return anInteger?.let {
            anInteger
        } ?: throw IllegalStateException()
    }

    fun secondInteger(): Int {
        return secondInteger?.let {
            secondInteger
        } ?: throw IllegalStateException()
    }

    fun secondLong(): Long {
        return secondLong?.let {
            secondLong
        } ?: throw IllegalStateException()
    }

    fun aString(): String {
        return aString?.let {
            aString
        } ?: throw IllegalStateException()
    }
}