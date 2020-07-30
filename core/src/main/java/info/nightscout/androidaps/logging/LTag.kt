package info.nightscout.androidaps.logging

enum class LTag(val tag: String, val defaultValue : Boolean = true, val requiresRestart: Boolean = false) {
    CORE("CORE"),
    APS("APS"),
    AUTOSENS("AUTOSENS", defaultValue = false),
    AUTOMATION("AUTOMATION"),
    BGSOURCE("BGSOURCE"),
    CONFIGBUILDER("CONFIGBUILDER"),
    CONSTRAINTS("CONSTRAINTS"),
    DATABASE("DATABASE"),
    DATAFOOD("DATAFOOD", defaultValue = false),
    DATASERVICE("DATASERVICE"),
    DATATREATMENTS("DATATREATMENTS"),
    EVENTS("EVENTS", defaultValue = false, requiresRestart = true),
    GLUCOSE("GLUCOSE"),
    LOCATION("LOCATION"),
    NOTIFICATION("NOTIFICATION"),
    NSCLIENT("NSCLIENT"),
    PUMP("PUMP"),
    PUMPBTCOMM("PUMPBTCOMM", defaultValue = true),
    PUMPCOMM("PUMPCOMM"),
    PUMPQUEUE("PUMPQUEUE"),
    PROFILE("PROFILE"),
    SMS("SMS"),
    TIDEPOOL("TIDEPOOL"),
    UI("UI", defaultValue = false),
    WEAR("WEAR", defaultValue = false)
}