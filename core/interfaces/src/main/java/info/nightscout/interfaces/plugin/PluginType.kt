package info.nightscout.interfaces.plugin

/**
 * Main plugin type
 *
 * set by [info.nightscout.interfaces.PluginDescription.mainType]
 */
enum class PluginType {
    GENERAL, SENSITIVITY, PROFILE, APS, PUMP, CONSTRAINTS, LOOP, BGSOURCE, INSULIN, SYNC
}