package xyz.hvdw.speedalert

/**
 * Default fallback speed limits per country.
 * Values are in km/h.
 *
 * urban = inside built‑up area
 * rural = outside built‑up area (single carriageway)
 * divided = dual carriageway / expressway
 * motorway = full motorway / autobahn
 */
object CountrySpeedFallbacks {

    data class FallbackSpeeds(
        val urban: Int,
        val rural: Int,
        val divided: Int,
        val motorway: Int
    )

    // ISO country code → fallback speeds
    private val defaults: Map<String, FallbackSpeeds> = mapOf(

        // Netherlands
        "NL" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 100
        ),

        // Belgium
        "BE" to FallbackSpeeds(
            urban = 50,
            rural = 70,
            divided = 120,
            motorway = 120
        ),

        // Germany
        "DE" to FallbackSpeeds(
            urban = 50,
            rural = 100,
            divided = 120,
            motorway = 130   // advisory
        ),

        // France
        "FR" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 110,
            motorway = 130
        ),

        // United Kingdom
        "GB" to FallbackSpeeds(
            urban = 30,
            rural = 60,
            divided = 70,
            motorway = 70
        ),

        // Denmark
        "DK" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 110,
            motorway = 130
        ),

        // Russia
        "RU" to FallbackSpeeds(
            urban = 60,
            rural = 90,
            divided = 110,
            motorway = 110
        ),

        // Poland
        "PL" to FallbackSpeeds(
            urban = 50,
            rural = 90,
            divided = 120,
            motorway = 140
        ),

        // Ukraine
        "UA" to FallbackSpeeds(
            urban = 50,
            rural = 90,
            divided = 110,
            motorway = 130
        ),

        // Israel
        "IL" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 90,
            motorway = 110
        ),

        // Norway
        "NO" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 90,
            motorway = 110
        ),

        // Sweden
        "SE" to FallbackSpeeds(
            urban = 50,
            rural = 70,
            divided = 100,
            motorway = 120
        ),

        // Italy
        "IT" to FallbackSpeeds(
            urban = 50,
            rural = 90,
            divided = 110,
            motorway = 130
        ),

        // Spain
        "ES" to FallbackSpeeds(
            urban = 50,
            rural = 90,
            divided = 100,
            motorway = 120
        ),

        // Portugal
        "PT" to FallbackSpeeds(
            urban = 50,
            rural = 90,
            divided = 100,
            motorway = 120
        ),

        // USA (varies by state)
        "US" to FallbackSpeeds(
            urban = 40,
            rural = 70,
            divided = 90,
            motorway = 120
        ),

        // Vietnam
        "VN" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 120
        ),

        // Turkey
        "TR" to FallbackSpeeds(
            urban = 50,
            rural = 90,
            divided = 110,
            motorway = 120
        ),

        // Korea
        "KR" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 90,
            motorway = 110
        ),

        // Japan
        "JP" to FallbackSpeeds(
            urban = 40,
            rural = 60,
            divided = 80,
            motorway = 100
        ),

        // Brazil
        "BR" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 110
        ),

        // Australia
        "AU" to FallbackSpeeds(
            urban = 50,
            rural = 100,
            divided = 110,
            motorway = 110
        ),

        // Canada
        "CA" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 110
        ),

        // Ireland
        "IE" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 120
        ),

        // China
        "CN" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 120
        ),

        // India
        "IN" to FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 120
        ),

        // Taiwan
        "TW" to FallbackSpeeds(
            urban = 50,
            rural = 70,
            divided = 90,
            motorway = 110
        ),
    )

    /**
     * Returns fallback speed limits for a given ISO country code.
     * If unknown, returns a safe global default.
     */
    fun get(countryCode: String?): FallbackSpeeds {
        if (countryCode == null) return globalDefault()
        return defaults[countryCode.uppercase()] ?: globalDefault()
    }

    /**
     * Global fallback if country is unknown.
     */
    private fun globalDefault(): FallbackSpeeds =
        FallbackSpeeds(
            urban = 50,
            rural = 80,
            divided = 100,
            motorway = 120
        )
}
