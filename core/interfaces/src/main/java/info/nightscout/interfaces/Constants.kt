package info.nightscout.interfaces

/**
 * Created by mike on 07.06.2016.
 */
object Constants {

    const val MGDL = "mg/dl" // This is Nightscout's representation
    const val MMOL = "mmol"
    const val MMOLL_TO_MGDL = 18.0 // 18.0182;
    const val MGDL_TO_MMOLL = 1 / MMOLL_TO_MGDL
    const val defaultDIA = 5.0
    const val REALLYHIGHBASALRATE = 1111111.0
    const val REALLYHIGHPERCENTBASALRATE = 1111111
    const val REALLYHIGHBOLUS = 1111111.0
    const val REALLYHIGHCARBS = 1111111
    const val REALLYHIGHIOB = 1111111.0
    const val notificationID = 556677

    // SMS COMMUNICATOR
    const val remoteBolusMinDistance = 15 * 60 * 1000L

    // Circadian Percentage Profile
    const val CPP_MIN_PERCENTAGE = 30
    const val CPP_MAX_PERCENTAGE = 250
    const val CPP_MIN_TIMESHIFT = -6
    const val CPP_MAX_TIMESHIFT = 23
    const val MAX_PROFILE_SWITCH_DURATION = (7 * 24 * 60).toDouble()// [min] ~ 7 days

    //DanaR
    const val dailyLimitWarning = 0.95

    // Temp targets
    const val defaultActivityTTDuration = 90 // min
    const val defaultActivityTTmgdl = 140.0
    const val defaultActivityTTmmol = 8.0
    const val defaultEatingSoonTTDuration = 45 // min
    const val defaultEatingSoonTTmgdl = 90.0
    const val defaultEatingSoonTTmmol = 5.0
    const val defaultHypoTTDuration = 60 // min
    const val defaultHypoTTmgdl = 160.0
    const val defaultHypoTTmmol = 8.0
    const val MIN_TT_MGDL = 72.0
    const val MAX_TT_MGDL = 180.0
    const val MIN_TT_MMOL = 4.0
    const val MAX_TT_MMOL = 10.0

    //NSClientInternal
    const val MAX_LOG_LINES = 90

    //Screen: Threshold for width/height to go into small width/height layout
    const val SMALL_WIDTH = 320
    const val SMALL_HEIGHT = 480

    //Autosens
    const val DEVIATION_TO_BE_EQUAL = 2.0
    const val DEFAULT_MAX_ABSORPTION_TIME = 6.0

    // Pump
    const val PUMP_MAX_CONNECTION_TIME_IN_SECONDS = 120 - 1
    const val MIN_WATCHDOG_INTERVAL_IN_SECONDS = 12 * 60

    //SMS Communicator
    const val SMS_CONFIRM_TIMEOUT = 5L * 60 * 1000

    //Storage [MB]
    const val MINIMUM_FREE_SPACE: Long = 200

    // Overview
    const val LOW_MARK = 76.0
    const val HIGH_MARK = 180.0

    // STATISTICS
    const val STATS_TARGET_LOW_MMOL = 3.9
    const val STATS_TARGET_HIGH_MMOL = 7.8
    const val STATS_RANGE_VERY_LOW_MMOL = 3.1
    const val STATS_RANGE_LOW_MMOL = 3.9
    const val STATS_RANGE_HIGH_NIGHT_MMOL = 8.3
    const val STATS_RANGE_HIGH_MMOL = 10.0
    const val STATS_RANGE_VERY_HIGH_MMOL = 13.9

    // Local profile
    const val LOCAL_PROFILE = "LocalProfile"

    // Local Alerts
    const val DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES = 30
    const val DEFAULT_MISSED_BG_READINGS_THRESHOLD_MINUTES = 30
    // One Time Password
    /**
     * Size of generated key for TOTP Authenticator token, in bits
     * rfc6238 suggest at least 160 for SHA1 based TOTP, but it ts too weak
     * with 512 generated QRCode to provision authenticator is too detailed
     * 256 is chosen as both secure enough and small enough for easy-scannable QRCode
     */
    const val OTP_GENERATED_KEY_LENGTH_BITS = 256

    /**
     * How many old TOTP tokens still accept.
     * Each token is 30s valid, but copying and SMS transmission of it can take additional seconds,
     * so we add leeway to still accept given amount of older tokens
     */
    const val OTP_ACCEPT_OLD_TOKENS_COUNT = 1

    /**
     * Button increments
     */
    const val INSULIN_PLUS1_DEFAULT = 0.5
    const val INSULIN_PLUS2_DEFAULT = 1.0
    const val INSULIN_PLUS3_DEFAULT = 2.0
    const val CARBS_FAV1_DEFAULT = 5
    const val CARBS_FAV2_DEFAULT = 10
    const val CARBS_FAV3_DEFAULT = 20

    const val INTERVAL_BETWEEN_OPERATIONS: Int = 5
    const val SECONDS_PER_DAY = 24 * 60 *60
}