package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class WizardElement(carbs: Carbs, dateUtil: DateUtil)
    : BaseElement(carbs.timestamp, UUID.nameUUIDFromBytes(("AAPS-wizard" + carbs.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose var units = "mg/dL"
    @Expose var carbInput: Double = 0.toDouble()
    @Expose var insulinCarbRatio: Double = 0.toDouble()
    @Expose var bolus: BolusElement? = null

    init {
        type = "wizard"
        carbInput = carbs.amount
        val fake = Bolus(
            amount = 0.0001,
            timestamp = carbs.timestamp,
            type = Bolus.Type.NORMAL
        )
        bolus = BolusElement(fake, dateUtil) // fake insulin record
    }
}

/* TODO fill the rest
{
    "type": "wizard",
    "bgInput": 16.152676653942503,
    "bgTarget": {
        "low": 3.6079861941795968,
        "high": 6.938434988806917
    },
    "bolus": "22239d4d592b48ae920b28971cceb48b",
    "carbInput": 57,
    "insulinCarbRatio": 24,
    "insulinOnBoard": 24.265,
    "insulinSensitivity": 4.329583433015516,
    "recommended": {
        "carb": 2.5,
        "correction": 2.25,
        "net": 0
    },
    "units": "mmol/L",
    "_active": true,
    "_groupId": "abcdef",
    "_schemaVersion": 0,
    "_version": 0,
    "clockDriftOffset": 0,
    "conversionOffset": 0,
    "createdTime": "2018-05-14T08:17:14.353Z",
    "deviceId": "DevId0987654321",
    "deviceTime": "2018-05-14T18:17:09",
    "guid": "18d90ea0-5915-4e95-a8b2-cb22819ce696",
    "id": "087c94ccdae84eb5a76b8205a244ec6b",
    "time": "2018-05-14T08:17:09.353Z",
    "timezoneOffset": 600,
    "uploadId": "SampleUploadId"
}
 */