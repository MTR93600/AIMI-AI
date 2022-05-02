package info.nightscout.plugins.aps.openAPSaiSMB

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.core.ui.R
import info.nightscout.interfaces.aps.VariableSensitivityResult
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.plugins.aps.APSResultObject
import info.nightscout.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.rx.logging.LTag
import org.json.JSONException
import org.json.JSONObject

class DetermineBasalResultaiSMB private constructor(injector: HasAndroidInjector) : DetermineBasalResultSMB(injector), VariableSensitivityResult {

    var constraintStr: String = ""
    var glucoseStr: String = ""
    var iobStr: String = ""
    var profileStr: String = ""
    var mealStr: String = ""
    override var variableSens: Double? = null

    internal constructor(
        injector: HasAndroidInjector,
        requestedSMB: Float,
        constraintStr: String,
        glucoseStr: String,
        iobStr: String,
        profileStr: String,
        mealStr: String,
        reason: String
    ) : this(injector) {
        this.constraintStr = constraintStr
        this.glucoseStr = glucoseStr
        this.iobStr = iobStr
        this.profileStr = profileStr
        this.mealStr = mealStr

        this.date = dateUtil.now()

        this.isTempBasalRequested = true
        this.rate = 0.0
        this.duration = 120

        this.smb = requestedSMB.toDouble()
        if (requestedSMB > 0) {
            this.deliverAt = dateUtil.now()
        }

        this.reason = reason
    }

    override fun toSpanned(): Spanned {
        val result = "$constraintStr<br/><br/>$glucoseStr<br/><br/>$iobStr" +
            "<br/><br/>$profileStr<br/><br/>$mealStr<br/><br/><br/>$reason"
        return HtmlHelper.fromHtml(result)
    }

    override fun newAndClone(injector: HasAndroidInjector): DetermineBasalResultSMB {
        val newResult = DetermineBasalResultaiSMB(injector)
        doClone(newResult)
        return newResult
    }

    override fun json(): JSONObject? {
        try {
            return JSONObject(json.toString())
        } catch (e: JSONException) {
            aapsLogger.error(LTag.APS, "Unhandled exception", e)
        }
        return null
    }

    init {
        hasPredictions = true
    }
}