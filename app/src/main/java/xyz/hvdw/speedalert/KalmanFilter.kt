package xyz.hvdw.speedalert

class KalmanFilter(profile: KalmanProfile) {

    private var estimate = 0.0
    private var error = profile.error
    private val processNoise = profile.processNoise
    private val measurementNoise = profile.measurementNoise

    fun update(measurement: Double): Double {
        // Prediction
        error += processNoise

        // Kalman gain
        val k = error / (error + measurementNoise)

        // Correction
        estimate += k * (measurement - estimate)

        // Update error
        error *= (1 - k)

        return estimate
    }
}
