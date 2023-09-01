package info.nightscout.shared.impl.logging

import info.nightscout.rx.interfaces.L
import info.nightscout.rx.interfaces.LogElement
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LImpl @Inject constructor(
    private val sp: SP
) : L {

    private var logElements: MutableList<LogElement> = ArrayList()

    init {
        LTag.values().forEach { logElements.add(LogElementImpl(it, sp)) }
    }

    override fun findByName(name: String): LogElement {
        for (element in logElements) {
            if (element.name == name) return element
        }
        return LogElementImpl(false, sp)
    }

    override fun getLogElements(): List<LogElement> {
        return logElements
    }

    override fun resetToDefaults() {
        for (element in logElements) {
            element.resetToDefault()
        }
    }

    class LogElementImpl : LogElement {

        var sp: SP
        override var name: String
        override var defaultValue: Boolean
        override var enabled: Boolean
        private var requiresRestart = false

        internal constructor(tag: LTag, sp: SP) {
            this.sp = sp
            this.name = tag.tag
            this.defaultValue = tag.defaultValue
            this.requiresRestart = tag.requiresRestart
            enabled = sp.getBoolean(getSPName(), defaultValue)
        }

        internal constructor(defaultValue: Boolean, sp: SP) {
            this.sp = sp
            name = "NONEXISTENT"
            this.defaultValue = defaultValue
            enabled = defaultValue
        }

        private fun getSPName(): String = "log_$name"

        override fun enable(enabled: Boolean) {
            this.enabled = enabled
            sp.putBoolean(getSPName(), enabled)
        }

        override fun resetToDefault() {
            enable(defaultValue)
        }
    }
}