package info.nightscout.androidaps.plugins.pump.common.defs

enum class PumpRunningState(val status: String) {

    Running("normal"),
    Suspended("suspended");
}