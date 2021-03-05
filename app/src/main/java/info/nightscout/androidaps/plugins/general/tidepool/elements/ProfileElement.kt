package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import java.util.*
import kotlin.collections.ArrayList

class ProfileElement(ps: ProfileSwitch, serialNumber: String)
    : BaseElement(ps.date, UUID.nameUUIDFromBytes(("AAPS-profile" + ps.date).toByteArray()).toString()) {

    @Expose
    internal var activeSchedule = "Normal"
    @Expose
    internal var basalSchedules: BasalProfile = BasalProfile()
    @Expose
    internal var units: Units = Units()
    @Expose
    internal var bgTargets: TargetProfile = TargetProfile()
    @Expose
    internal var carbRatios: IcProfile = IcProfile()
    @Expose
    internal var insulinSensitivities: IsfProfile = IsfProfile()
    @Expose
    internal var deviceId: String = TidepoolUploader.PUMP_TYPE + ":" + serialNumber
    @Expose
    internal var deviceSerialNumber: String = serialNumber
    @Expose
    internal var clockDriftOffset: Long = 0
    @Expose
    internal var conversionOffset: Long = 0

    init {
        type = "pumpSettings"
        val profile: Profile? = ps.profileObject
        checkNotNull(profile)
        for (br in profile.basalValues)
            basalSchedules.Normal.add(BasalRate(br.timeAsSeconds * 1000, br.value))
        for (target in profile.singleTargetsMgdl)
            bgTargets.Normal.add(Target(target.timeAsSeconds * 1000, target.value))
        for (ic in profile.ics)
            carbRatios.Normal.add(Ratio(ic.timeAsSeconds * 1000, ic.value))
        for (isf in profile.isfsMgdl)
            insulinSensitivities.Normal.add(Ratio(isf.timeAsSeconds * 1000, isf.value))
    }

    inner class BasalProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<BasalRate> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class BasalRate internal constructor(
            @field:Expose
            internal var start: Int,
            @field:Expose
            internal var rate: Double
    )

    inner class Units internal constructor(
            @field:Expose
            internal var carb: String = "grams",
            @field:Expose
            internal var bg: String = "mg/dL"
    )

    inner class TargetProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<Target> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class Target internal constructor(
            @field:Expose
            internal var start: Int,
            @field:Expose
            internal var target: Double
    )

    inner class IcProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<Ratio> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class IsfProfile internal constructor(
            @field:Expose
            internal var Normal: ArrayList<Ratio> = ArrayList() // must be the same var name as activeSchedule
    )

    inner class Ratio internal constructor(
            @field:Expose
            internal var start: Int,
            @field:Expose
            internal var amount: Double
    )
}


