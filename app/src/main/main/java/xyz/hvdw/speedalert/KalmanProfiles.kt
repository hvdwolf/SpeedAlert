package xyz.hvdw.speedalert

data class KalmanProfile(
    val error: Double,
    val processNoise: Double,
    val measurementNoise: Double
)

object KalmanProfiles {

    // Balanced, smooth
    val NORMAL = KalmanProfile(
        error = 0.2,
        processNoise = 1.0,
        measurementNoise = 1.0
    )

    // Fast, responsive
    val SPORT = KalmanProfile(
        error = 0.05,
        processNoise = 5.0,
        measurementNoise = 0.3
    )

    // RAW: closest to Google Maps
    val RAW = KalmanProfile(
        error = 0.02,
        processNoise = 8.0,
        measurementNoise = 0.2
    )
}
