package info.nightscout.androidaps.plugins.pump.common.defs

enum class PumpCapability {

    Bolus,  // isBolusCapable
    ExtendedBolus,  // isExtendedBolusCapable
    TempBasal,  // isTempBasalCapable
    BasalProfileSet,  // isSetBasalProfileCapable
    Refill,  // isRefillingCapable
    ReplaceBattery,  // isBatteryReplaceable
    // StoreCarbInfo,  // removed. incompatible with storing notes with carbs
    TDD,  // supportsTDDs
    ManualTDDLoad,  // needsManualTDDLoad
    BasalRate30min,  // is30minBasalRatesCapable

    // grouped by pump
    MDI(arrayOf(Bolus)),
    VirtualPumpCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery)),
    ComboCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),
    DanaCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),
    //DanaWithHistoryCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, StoreCarbInfo, TDD, ManualTDDLoad)),
    DanaWithHistoryCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),
    InsightCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, BasalRate30min)),
    MedtronicCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD)),
    OmnipodCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, BasalRate30min)),
    YpsomedCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),  // BasalRates (separately grouped)
    DiaconnCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)), //
    BasalRate_Duration15minAllowed,
    BasalRate_Duration30minAllowed,
    BasalRate_Duration15and30minAllowed(arrayOf(BasalRate_Duration15minAllowed, BasalRate_Duration30minAllowed)),
    BasalRate_Duration15and30minNotAllowed,
    MedLinkMedtronicCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, BasalRate30min) )//
    ;

    var children: ArrayList<PumpCapability> = ArrayList()

    constructor() {
        children.add(this)
    }

    constructor(list: Array<PumpCapability>) {
        children.addAll(list)
    }

    fun hasCapability(capability: PumpCapability): Boolean = children.contains(capability)
}