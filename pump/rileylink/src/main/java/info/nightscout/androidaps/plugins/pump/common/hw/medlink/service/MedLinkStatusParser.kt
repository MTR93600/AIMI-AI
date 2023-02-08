package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.interfaces.pump.EnliteInMemoryGlucoseValue
import info.nightscout.pump.common.data.MedLinkPartialBolus
import info.nightscout.pump.common.data.MedLinkPumpStatus
import info.nightscout.pump.common.defs.PumpRunningState
import java.lang.Exception
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern

/**
 * Created by Dirceu on 13/12/20.
 */
class MedLinkStatusParser {

    fun partialMatch(pumpStatus: MedLinkPumpStatus): Boolean {
        return pumpStatus.lastBGTimestamp != 0L || pumpStatus.lastBolusTime != null || pumpStatus.batteryVoltage != 0.0 || pumpStatus.reservoirRemainingUnits != 0.0 || pumpStatus.currentBasal != 0.0 ||
            pumpStatus.dailyTotalUnits != null
    }

    fun fullMatch(pumpStatus: MedLinkPumpStatus): Boolean {
        return pumpStatus.lastDataTime != 0L && pumpStatus.lastBolusTime != null && pumpStatus.batteryVoltage != 0.0 && pumpStatus.dailyTotalUnits != null
    }

    companion object {

        private var previousAge: Int = 0
        private var previousReservoirRemaining: Double = 0.0
        private val dateTimeFullPattern = Pattern.compile("\\d{2}-\\d{2}-\\d{4}\\s\\d{2}:\\d{2}")
        private val dateTimePartialPattern = Pattern.compile("\\d{2}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}")
        private val timePartialPattern = Pattern.compile("\\d{1,2}:\\d{2}")
        private var bgUpdated = false

        @JvmStatic
        fun parseStatus(pumpAnswer: Array<String>, pumpStatus: MedLinkPumpStatus, injector: HasAndroidInjector): MedLinkPumpStatus {

//        13‑12‑2020 18:36  54%
//        String ans = "1623436309132\n" +
//                "fifo reading fault\n" +
//                "crc-8 invalid data\n" +
//                "pump no response for 0x70 command\n" +
//                "11-06-2021 20:30 100%\n" +
//                "bg: --- -------- --:--\n" +
//                "last bolus: 1.6u 10-06-21 17:39\n" +
//                "square bolus: 0.0u delivered: 0.000u\n" +
//                "square bolus time: 0h:00m / 0h:00m\n" +
//                "isig: 00.00na\n" +
//                "calibration factor:  5.342\n" +
//                "next calibration time: --:--\n" +
//                "sensor uptime:  731min\n" +
//                "bg target:  75-155\n" +
//                "pump battery voltage: 1.26v\n" +
//                "reservoir: 246.12u\n" +
//                "basal scheme: std\n" +
//                "basal: 0.275u/h\n" +
//                "tbr: 100%   0h:00m\n" +
//                "insulin today:  3.175u\n" +
//                "insulin yesterday:  5.350u\n" +
//                "max. bolus:  7.8u\n" +
//                "easy bolus step: 0.2u\n" +
//                "max. basal rate: 2.450u/h\n" +
//                "insulin duration time: 5h\n" +
//                "pump status: normal\n" +
//                "enlite transmitter id: 0000000\n" +
//                "ready\n";
//        pumpAnswer = ans.split("\n");
            return try {
                val messageIterator =
                    Arrays.stream(pumpAnswer).map { f: String -> f.lowercase() }.iterator()
                var message: String? = null
                while (messageIterator.hasNext()) {
                    message = messageIterator.next().trim { it <= ' ' }
                    if (parseDateTime(message, dateTimeFullPattern, true) != null) {
                        break
                    }
                }
                val timeMedLinkPumpStatus = parsePumpTimeMedLinkBattery(message, pumpStatus)
                val bgMedLinkPumpStatus = parseBG(messageIterator, timeMedLinkPumpStatus, injector)
                val lastBolusStatus = parseBolusInfo(messageIterator, bgMedLinkPumpStatus) as MedLinkPumpStatus
                //                18:36:49.381
//        18:36:49.495 Last bolus: 0.2u 13‑12‑20 18:32

//        18:36:49.496 Square bolus: 0.0u delivered: 0.000u
//            moveIterator(messageIterator);
//        18:36:49.532 Square bolus time: 0h:00m / 0h:00m
                val isigStatus = parseISIG(messageIterator, lastBolusStatus)
                //        18:36:49.570 ISIG: 20.62nA
                val calibrationFactorStatus = parseCalibrationFactor(messageIterator, isigStatus)
                //        18:36:49.607 Calibration factor: 6.419
                val nextCalibrationStatus = parseNextCalibration(messageIterator, calibrationFactorStatus, injector)
                //        18:36:49.681 Next calibration time:  5:00
//        moveIterator(messageIterator);
//        18:36:49.683 Sensor uptime: 1483min
                val sageStatus = parseSensorAgeStatus(messageIterator, nextCalibrationStatus)
                //        18:36:49.719 BG target:  75‑160
                moveIterator(messageIterator)
                val batteryStatus = parseBatteryVoltage(messageIterator, sageStatus)
                //        18:36:49.832 Pump battery voltage: 1.43V
                val reservoirStatus = parseReservoir(messageIterator, batteryStatus)

//        18:36:49.907 Reservoir:  66.12u
                val basalStatus = parseCurrentBasal(messageIterator, reservoirStatus)
                //        18:36:49.982 Basal scheme: STD
//        moveIterator(messageIterator);
//        18:36:49.983 Basal: 0.600u/h
                val tempBasalStatus = parseTempBasal(messageIterator, basalStatus)
                //        18:36:50.020 TBR: 100%   0h:00m
                val dailyTotal = parseDayInsulin(messageIterator, tempBasalStatus)
                //        18:36:50.058 Insulin today: 37.625u
                val yesterdayTotal = parseDayInsulin(messageIterator, dailyTotal)
                //        18:36:50.095 Insulin yesterday: 48.625u
                moveIterator(messageIterator)

//        18:36:50.132 Max. bolus: 15.0u
//        moveIterator(messageIterator);
//        18:36:50.171 Easy bolus step: 0.1J
//        moveIterator(messageIterator);
//        18:36:50.244 Max. basal rate: 2.000J/h
//        moveIterator(messageIterator);
//        18:36:50.282 Insulin duration time: 3h
//        moveIterator(messageIterator);
                val pumpState = parsePumpState(yesterdayTotal, messageIterator)
                //        18:36:50.448 Pump status: NORMAL
                moveIterator(messageIterator)
                //            transmitter id: medlink_id
                //            bg level alarms are on in pump
//        18:36:50.471 EomEomEom
                parseBgLevelAlarms(pumpState, messageIterator)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        fun parseBolusInfo(messageIterator: Iterator<String>, pumpStatus: MedLinkPartialBolus): MedLinkPartialBolus {
            var status = pumpStatus
            while (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                if (currentLine.contains("last bolus")) {
                    status = parseLastBolus(currentLine, pumpStatus)
                }
                if (currentLine.contains("square bolus")) {

                    status = squareBolus(currentLine, status)
                    return status
                }
            }
            return pumpStatus
        }

        private fun squareBolus(currentLine: String, lastBolusStatus: MedLinkPartialBolus): MedLinkPartialBolus {
            if (currentLine.contains("square bolus:")) {
                val bolusPat = Pattern.compile("\\d+\\.\\d+")
                val bolusMatcher = bolusPat.matcher(currentLine)
                if (bolusMatcher.find()) {
                    val bolusAmount = bolusMatcher.group().toDouble()
                    if (bolusAmount > 0.0 && bolusMatcher.find()) {
                        lastBolusStatus.lastBolusAmount = bolusAmount
                        lastBolusStatus.bolusDeliveredAmount = bolusMatcher.group().toDouble()
                    } else {
                        lastBolusStatus.bolusDeliveredAmount = 0.0
                    }
                }
            }
            //                "square bolus: 0.0u delivered: 0.000u\n" +
//                "square bolus time: 0h:00m / 0h:00m\n" +

            return lastBolusStatus
        }

        private fun parseBgLevelAlarms(pumpStatus: MedLinkPumpStatus, messageIterator: Iterator<String>): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        18:36:49.681 Next calibration time:  5:00
                pumpStatus.bgAlarmOn = currentLine.contains("bg level alarms are on in pump")
            }
            return pumpStatus
        }

        private fun parseNextCalibration(
            messageIterator: Iterator<String>,
            pumpStatus: MedLinkPumpStatus,
            injector: HasAndroidInjector,
        ): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        18:36:49.681 Next calibration time:  5:00
                if (currentLine.contains("next calibration time:")) {
                    val pattern = Pattern.compile("\\d{1,2}\\:\\d{2}")
                    val matcher = pattern.matcher(currentLine)
                    if (matcher.find()) {
                        pumpStatus.nextCalibration = parseTime(currentLine, timePartialPattern)
                    }
                    //                if (bgUpdated) {
//                    pumpStatus.sensorDataReading = new SensorDataReading(injector,
//                            pumpStatus.bgReading, pumpStatus.isig,
//                            pumpStatus.calibrationFactor);
//                    bgUpdated = false;
//                }
                }
            }
            return pumpStatus
        }

        private fun parseCalibrationFactor(
            messageIterator: Iterator<String>,
            pumpStatus: MedLinkPumpStatus,
        ): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        18:36:49.607 Calibration factor: 6.419
                if (currentLine.contains("calibration factor:")) {
                    val pattern = Pattern.compile("\\d+\\.\\d+")
                    val matcher = pattern.matcher(currentLine)
                    if (matcher.find()) {
                        pumpStatus.calibrationFactor = java.lang.Double.valueOf(matcher.group())
                    }
                    if (bgUpdated) {
                        pumpStatus.sensorDataReading = BgSync.BgHistory.BgValue(
                            timestamp = pumpStatus.bgReading.timestamp,
                            value = pumpStatus.bgReading.value,
                            noise = 0.0,
                            arrow = BgSync.BgArrow.NONE,
                            isig = pumpStatus.isig,
                            calibrationFactor = pumpStatus.calibrationFactor,
                            sensorUptime = pumpStatus.sensorAge,
                            sourceSensor = BgSync.SourceSensor.MM_ENLITE,
                            raw = 0.0
                        )
                        bgUpdated = false
                    }
                }
            }
            return pumpStatus
        }

        private fun parseISIG(
            messageIterator: Iterator<String>,
            pumpStatus: MedLinkPumpStatus,
        ): MedLinkPumpStatus {
            var currentLine = ""
            while (messageIterator.hasNext()) {
                currentLine = messageIterator.next()
                if (currentLine.contains("isig:")) {
                    break
                }
            }
            // 18:36:49.570 ISIG: 20.62nA
            if (currentLine.contains("isig:")) {
                val pattern = Pattern.compile("\\d+\\.\\d+")
                val matcher = pattern.matcher(currentLine)
                if (matcher.find()) {
                    pumpStatus.isig = java.lang.Double.valueOf(matcher.group())
                }
            }

            return pumpStatus
        }

        private fun parsePumpState(
            pumpStatus: MedLinkPumpStatus,
            messageIterator: Iterator<String>,
        ): MedLinkPumpStatus {
//        18:36:50.448 Pump status: NORMAL
            while (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                if (currentLine.contains("pump status")) {
                    val status = currentLine.split(":".toRegex()).toTypedArray()[1]
                    if (status.contains("normal")) {
                        pumpStatus.pumpRunningState = PumpRunningState.Running
                    } else if (status.contains("suspend")) {
                        pumpStatus.pumpRunningState = PumpRunningState.Suspended
                    }
                    break
                } else if (currentLine.contains("eomeom") || currentLine.contains("ready")) {
                    break
                }
            }
            return pumpStatus
        }

        private fun parseSensorAgeStatus(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        18:36:49.683 Sensor uptime: 1483min
                if (currentLine.contains("sensor uptime:")) {
                    val pattern = Pattern.compile("\\d+")
                    val matcher = pattern.matcher(currentLine)
                    if (matcher.find()) {
                        val currentAge = Integer.valueOf(matcher.group())
                        if (pumpStatus.sensorAge != null) {
                            previousAge = pumpStatus.sensorAge
                        }
                        pumpStatus.sensorAge = currentAge
                    }
                }
            }
            return pumpStatus
        }

        private fun parseDayInsulin(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        Insulin today: 37.625u
                val reservoirPattern = Pattern.compile("\\d+\\.\\d+u")
                val matcher = reservoirPattern.matcher(currentLine)
                if (currentLine.contains("insulin today:")) {
                    if (matcher.find()) {
                        val totalInsulinToday = matcher.group()
                        pumpStatus.dailyTotalUnits = totalInsulinToday.substring(0, totalInsulinToday.length - 1).toDouble()
                    }
                } else if (currentLine.contains("insulin yesterday:")) {
                    if (matcher.find()) {
                        val totalInsulinToday = matcher.group()
                        pumpStatus.yesterdayTotalUnits = totalInsulinToday.substring(0, totalInsulinToday.length - 1).toDouble()
                    }
                }
            }
            return pumpStatus
        }

        private fun parseTempBasal(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        18:36:50.020 TBR: 100%   0h:00m
                if (currentLine.contains("tbr:")) {
                    val reservoirPattern = Pattern.compile("\\d+%")
                    val matcher = reservoirPattern.matcher(currentLine)
                    if (matcher.find()) {
                        val tempBasalRatio = matcher.group()
                        pumpStatus.tempBasalAmount = tempBasalRatio.substring(0, tempBasalRatio.length - 1).toDouble()
                        val remTempTimePattern = Pattern.compile("\\d+h:\\d+m")
                        val remTempTimeMatcher = remTempTimePattern.matcher(currentLine)
                        if (remTempTimeMatcher.find()) {
                            val remaingTime = remTempTimeMatcher.group()
                            val hourMinute = remaingTime.split(":".toRegex()).toTypedArray()
                            val hour = hourMinute[0]
                            val minute = hourMinute[1]
                            pumpStatus.tempBasalRemainMin = 60 * hour.substring(0, hour.length - 1).toInt() + minute.substring(0, minute.length - 1).toInt()
                        }
                    }
                }
            }
            return pumpStatus
        }

        private fun parseCurrentBasal(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                var currentLine = messageIterator.next()
                //        18:36:49.983 Basal: 0.600u/h
                if (currentLine.contains("basal scheme:")) {
                    val basalScheme = currentLine.split(":".toRegex()).toTypedArray()
                    pumpStatus.activeProfileName = basalScheme[1].trim { it <= ' ' }
                    currentLine = messageIterator.next()
                    if (currentLine.contains("basal:")) {
                        val reservoirPattern = Pattern.compile("\\d+\\.\\d+u/h")
                        val matcher = reservoirPattern.matcher(currentLine)
                        if (matcher.find()) {
                            val currentBasal = matcher.group()
                            pumpStatus.currentBasal = currentBasal.substring(0, currentBasal.length - 3).toDouble()
                        }
                    }
                }
            }
            return pumpStatus
        }

        private fun parseReservoir(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                //        18:36:49.907 Reservoir:  66.12u
                if (currentLine.contains("reservoir")) {
                    val reservoirPattern = Pattern.compile("\\d+\\.\\d+u")
                    val matcher = reservoirPattern.matcher(currentLine)
                    if (matcher.find()) {
                        val reservoirRemaining = matcher.group()
                        val reservoirRemainingDouble = reservoirRemaining.substring(0, reservoirRemaining.length - 1).toDouble()
                        previousReservoirRemaining = pumpStatus.reservoirRemainingUnits
                        if (reservoirRemainingDouble != 0.0 && pumpStatus.sensorAge != 0) {
                            pumpStatus.reservoirRemainingUnits = reservoirRemainingDouble
                        } else {
                            pumpStatus.sensorAge = previousAge
                        }
                    }
                }
            }
            return pumpStatus
        }

        private fun moveIterator(messageIterator: Iterator<String>) {
            if (messageIterator.hasNext()) {
                messageIterator.next()
            }
        }

        private fun parseBatteryVoltage(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
//        18:36:49.832 Pump battery voltage: 1.43V
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()
                if (currentLine.contains("pump battery voltage")) {
                    val lastBolusPattern = Pattern.compile("\\d\\.\\d{1,2}v")
                    val matcher = lastBolusPattern.matcher(currentLine)
                    if (matcher.find()) {
                        val batteryVoltage = matcher.group()
                        val batteryVoltageValue = java.lang.Double.valueOf(batteryVoltage.substring(0, batteryVoltage.length - 1))
                        pumpStatus.isBatteryChanged = pumpStatus.batteryVoltage != null && batteryVoltageValue > 0.01 + pumpStatus.batteryVoltage!! &&
                            pumpStatus.lastBatteryChanged >= System
                            .currentTimeMillis() -
                            1800000L
                        pumpStatus.batteryVoltage = batteryVoltageValue
                    }
                }
            }
            return pumpStatus
        }

        private fun parseLastBolus(currentLine: String, pumpStatus: MedLinkPartialBolus): MedLinkPartialBolus {
            //        18:36:49.495 Last bolus: 0.2u 13‑12‑20 18:32
            if (currentLine.contains("last bolus")) {
                val lastBolusPattern = Pattern.compile("\\d{1,2}\\.\\du")
                val matcher = lastBolusPattern.matcher(currentLine)
                if (matcher.find()) {
                    val lastBolusAmount = matcher.group()
                    pumpStatus.lastBolusAmount = java.lang.Double.valueOf(lastBolusAmount.substring(0, lastBolusAmount.length - 1))
                    val dateTime = parseDateTime(currentLine, dateTimePartialPattern, false)
                    if (dateTime != null) {
                        pumpStatus.lastBolusTime = dateTime
                    }
                }
            }

            return pumpStatus
        }

        private fun parseBG(messageIterator: Iterator<String>, pumpStatus: MedLinkPumpStatus, injector: HasAndroidInjector): MedLinkPumpStatus {
            if (messageIterator.hasNext()) {
                val currentLine = messageIterator.next()

//            BG: 120 13‑12‑20 18:33
                val bgLinePattern = Pattern.compile("bg:\\s+\\d{2,3}")
                val matcher = bgLinePattern.matcher(currentLine)
                if (matcher.find()) {
                    val matched = matcher.group(0)
                    val bg = matched.substring(3).toDouble()
                    val bgDate = parseDateTime(currentLine, dateTimePartialPattern, false)
                    if (bgDate != null) {
                        bgUpdated = true
                        pumpStatus.bgReading = EnliteInMemoryGlucoseValue(
                            timestamp = bgDate.time,
                            value = bg,
                            lastTimestamp = pumpStatus.lastBGTimestamp,
                            lastValue = pumpStatus.latestBG
                        )
                        pumpStatus.lastBGTimestamp = bgDate.time
                    }
                    pumpStatus.latestBG = bg
                }
            }
            return pumpStatus
        }

        private fun parsePumpTimeMedLinkBattery(currentLine: String?, pumpStatus: MedLinkPumpStatus): MedLinkPumpStatus {
            if (currentLine != null) {
//            String currentLine = messageIterator.next();
//            while(currentLine.startsWith("ready")){
//                currentLine = messageIterator.next();
//            }
                //        13‑12‑2020 18:36  54%
                val dateTime = parseDateTime(currentLine, dateTimeFullPattern, true)
                if (dateTime != null) {
                    pumpStatus.lastDataTime = dateTime.time
                }
                val battery = Pattern.compile("\\d+%")
                val batteryMatcher = battery.matcher(currentLine)
                //            if (batteryMatcher.find()) {
//                String percentage = batteryMatcher.group(0);
//                pumpStatus.deviceBatteryRemaining = Integer.parseInt(percentage.substring(0, percentage.length() - 1));
//            }
            }
            return pumpStatus
        }

        private fun parseDateTime(currentLine: String, pattern: Pattern, fourDigitYear: Boolean): Date? {
            val matcher = pattern.matcher(currentLine)
            return if (matcher.find()) {
                val datePattern: String
                datePattern = if (fourDigitYear) {
                    "dd-MM-yyyy HH:mm"
                } else {
                    "dd-MM-yy HH:mm"
                }
                val formatter = SimpleDateFormat(datePattern, Locale.getDefault())
                formatter.parse(matcher.group(), ParsePosition(0))
            } else {
                null
            }
        }

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private fun parseTime(currentLine: String, pattern: Pattern): ZonedDateTime? {
            val matcher = pattern.matcher(currentLine)
            return if (matcher.find()) {
                var timeString = matcher.group(0)
                if (timeString.length < 5) {
                    timeString = "0$timeString"
                }
                val time = LocalTime.parse(timeString, timeFormatter)
                val result: LocalDateTime = if (LocalTime.now().isAfter(time)) {
                    LocalDateTime.of(LocalDate.now().plusDays(1), time)
                } else {
                    LocalDateTime.of(LocalDate.now(), time)
                }
                result.atZone(ZoneOffset.systemDefault())
            } else {
                null
            }
        }
    }
}