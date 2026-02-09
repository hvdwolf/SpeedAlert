package xyz.hvdw.speedalert

class KalmanFilter {

    private var estimate = 0.0
    /* Faster reaction on error and process noise, and less smoothing
    private var error = 0.8
    private val processNoise = 0.6
    private val measurementNoise = 2.0 */

    /* Very smooth and conservative
    private var error = 1.0
    private val processNoise = 0.125
    private val measurementNoise = 4.0 */

    /* Sport mode, almost raw GPS
    private var error = 0.5
    private val processNoise = 1.0
    private val measurementNoise = 1.5 */

    /* Almost Google Maps style
    private var error = 0.1
    private val processNoise = 3.0   // or 2.0
    private val measurementNoise = 0.5   // or 0.7 */

    /* Extreme mode, almost raw GPS, pure Google maps */
    private var error = 0.05   // 0.02
    private val processNoise = 5.0   // 8.0
    private val measurementNoise = 0.3 //  0.2


    fun update(measurement: Double): Double {
        // Prediction
        error += processNoise

        // Kalman gain
        val k = error / (error + measurementNoise)

        // Correction
        estimate += k * (measurement - estimate)
        error *= (1 - k)

        return estimate
    }
}
