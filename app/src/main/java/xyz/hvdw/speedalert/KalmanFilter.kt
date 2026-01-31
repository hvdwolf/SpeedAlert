package xyz.hvdw.speedalert

class KalmanFilter(
    private var processNoise: Double = 0.125,   // Q
    private var measurementNoise: Double = 1.0  // R
) {
    // Q and R values
    // Q -> More responsive: 0.5 - 1.0; More smoothing: 0.05 - 0.01
    // R -> How noisy is my GPS? noisy: 2.0 - 3.0 (5.0); stable: 0.5 - 0.2
    // Some defaults for Q and R:
    // City driving, want smoothness:  Q = 0.1, R = 2.0
    // More responsive, highway focus: Q = 0.3, R = 1.0
    private var estimate = 0.0
    private var errorCovariance = 1.0
    private var initialized = false

    fun update(measurement: Double): Double {
        if (!initialized) {
            estimate = measurement
            initialized = true
            return estimate
        }

        // Prediction update
        errorCovariance += processNoise

        // Measurement update
        val kalmanGain = errorCovariance / (errorCovariance + measurementNoise)
        estimate += kalmanGain * (measurement - estimate)
        errorCovariance *= (1 - kalmanGain)

        return estimate
    }
}
