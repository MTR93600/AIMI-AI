package info.nightscout.androidaps.logging

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerTest : AAPSLogger {

    override fun debug(message: String) {
        println("DEBUG: $message")
    }

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        println("DEBUG: " + message)
    }

    override fun debug(tag: LTag, message: String) {
        println("DEBUG: : " + tag.tag + " " + message)
    }

    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {
        println("DEBUG: : " + tag.tag + " " + String.format(format, arguments))
    }

    override fun warn(tag: LTag, message: String) {
        println("WARN: " + tag.tag + " " + message)
    }

    override fun warn(tag: LTag, format: String, vararg arguments: Any?) {
        println("INFO: : " + tag.tag + " " + String.format(format, arguments))
    }

    override fun info(tag: LTag, message: String) {
        println("INFO: " + tag.tag + " " + message)
    }

    override fun info(tag: LTag, format: String, vararg arguments: Any?) {
        println("INFO: : " + tag.tag + " " + String.format(format, arguments))
    }

    override fun error(tag: LTag, message: String) {
        println("ERROR: " + tag.tag + " " + message)
    }

    override fun error(message: String) {
        println("ERROR: " + message)
    }

    override fun error(message: String, throwable: Throwable) {
        println("ERROR: " + message + " " + throwable)
    }

    override fun error(format: String, vararg arguments: Any?) {
        println("ERROR: : " + String.format(format, arguments))
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        println("ERROR: " + tag.tag + " " + message + " " + throwable)
    }

    override fun error(tag: LTag, format: String, vararg arguments: Any?) {
        println("ERROR: : " + tag.tag + " " + String.format(format, arguments))
    }
}