package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump

object CalibrationFactor {

    var calibrationFactor: List<CalibrationFactorPair> = listOf()

    fun getCalibrationFactor(timeOf: Long): Double {
        var result: Double = 0.0;
        for (calibFact in calibrationFactor) {
            if (calibFact.initialTime <= timeOf) {
                result = calibFact.calibrationFactor
            } else {
                break;
            }
        }
        return result;
    }

    fun addCalibrationFactor(pair: CalibrationFactorPair) {
        if (!calibrationFactor.isEmpty() &&
            calibrationFactor.last().calibrationFactor != pair.calibrationFactor) {
            calibrationFactor = calibrationFactor.plus(pair)
        }
    }
}