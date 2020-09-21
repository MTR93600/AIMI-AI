package info.nightscout.androidaps.logging

import org.slf4j.LoggerFactory

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerProduction constructor(val l: L) : AAPSLogger {

    override fun debug(message: String) {
        LoggerFactory.getLogger(LTag.CORE.tag).debug(stackLogMarker() + message)
    }

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        if (enable && l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + message)
    }

    override fun debug(tag: LTag, message: String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + message)
    }

    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + format, arguments)
    }

    override fun warn(tag: LTag, message: String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).warn(stackLogMarker() + message)
    }

    override fun warn(tag: LTag, format: String, vararg arguments: Any?) {
        LoggerFactory.getLogger(tag.tag).warn(stackLogMarker() + format, arguments)
    }

    override fun info(tag: LTag, message: String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).info(stackLogMarker() + message)
    }

    override fun info(tag: LTag, format: String, vararg arguments: Any?) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).info(stackLogMarker() + format, arguments)
    }

    override fun error(tag: LTag, message: String) {
        LoggerFactory.getLogger(tag.tag).error(stackLogMarker() + message)
    }

    override fun error(message: String) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(stackLogMarker() + message)
    }

    override fun error(message: String, throwable: Throwable) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(stackLogMarker() + message, throwable)
    }

    override fun error(format: String, vararg arguments: Any?) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(stackLogMarker() + format, arguments)
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        LoggerFactory.getLogger(tag.tag).error(stackLogMarker() + message, throwable)
    }

    override fun error(tag: LTag, format: String, vararg arguments: Any?) {
        LoggerFactory.getLogger(tag.tag).error(stackLogMarker() + format, arguments)
    }
}

fun StackTraceElement.toLogString(): String = "[${this.className.substringAfterLast(".")}.${this.methodName}():${this.lineNumber}]: "

/* Needs to be inline. Don't remove even if IDE suggests it. */
@Suppress("NOTHING_TO_INLINE")
inline fun stackLogMarker() = Throwable().stackTrace[1].toLogString()