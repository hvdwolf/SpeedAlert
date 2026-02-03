package xyz.hvdw.speedalert

class KalmanFilter {

    private var estimate = 0.0
    private var error = 1.0

    // Tuned for automotive speed smoothing
    private val processNoise = 0.125
    private val measurementNoise = 4.0

    fun update(measured: Double): Double {

        // Predict
        error += processNoise

        // Kalman gain
        val k = error / (error + measurementNoise)

        // Update estimate
        estimate += k * (measured - estimate)

        // Update error
        error *= (1 - k)

        return estimate
    }
}
