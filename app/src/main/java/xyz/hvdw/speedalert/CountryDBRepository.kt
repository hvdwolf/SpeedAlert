package xyz.hvdw.speedalert

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// data classes
data class CountryDatabase(
    val isoCode: String,
    val file: File
)

data class DatabaseUpdate(
    val isoCode: String,
    val localVersion: String?,
    val remoteVersion: String
)

data class DatabaseUpdateResult(
    val updates: List<DatabaseUpdate>,
    val errors: List<String>
)

// arrays containing the iso codes for the database file and the real country names for that iso code.
val eu_iso_codes by lazy {
    arrayOf(
        "ad", "al", "at", "ba", "be", "bg", "by", "ch", "cy", "cz", "de", "dk", "ee", "es", "fi", "fo", "fr", "gb", "ge", "gr", "hr", "hu", "ie", "is", "it", "li", "lt", "lu", "lv", "mc", "md", "me", "mk", "mt", "nl", "no", "pl", "pt", "ro", "rs", "ru", "se", "si", "sk", "tr", "ua",
    )
}

val asia_iso_codes by lazy {
    arrayOf(
        "af", "am", "az", "bd", "bt", "kh", "cn", "ir", "iq", "jp", "jo", "kz", "kg", "la", "lb", "my", "mv", "mn", "mm", "np", "kp", "pk", "ph", "kr", "lk", "sy", "tw", "tj", "th", "tm", "uz", "vn", "ye",
    )
}

val americas_iso_codes by lazy {
    arrayOf(
        "ca", "gl", "mx", "us", "bs", "bz", "cr", "cu", "sv", "gt", "hn", "jm", "ni", "pa", "ar", "bo", "br", "cl", "co", "ec", "gy", "py", "pe", "sr", "uy", "ve",
    )
}

val africa_iso_codes by lazy {
    arrayOf(
        "dz", "ao", "bj", "bw", "bf", "bi", "cm", "cv", "cf", "td", "cg", "cd", "dj", "eg", "gq", "er", "et", "ga", "gh", "gn", "gw", "ci", "ke", "ls", "lr", "ly", "mg", "mw", "ml", "mr", "mu", "ma", "mz", "na", "ne", "ng", "rw", "sh", "st", "sn", "gm", "sc", "sl", "so", "za", "ss", "sd", "sz", "tz", "tg", "tn", "ug", "zm", "zw",
    )
}

val australia_oceania_iso_codes by lazy{
    arrayOf(
        "au","ck","fj","ki","mh","fm","nr","nc","nz","nu","pw","pg","pn","pf","ws","sb","tk","to","tv","vu",
    )
}

private val europe by lazy {
    arrayOf(
        "albania", "andorra", "austria", "azores", "belarus", "belgium", "bosnia-herzegovina", "bulgaria", "croatia", "cyprus", "czech-republic", "denmark", "estonia", "faroe-islands", "finland", "france", "georgia", "germany", "greece", "hungary", "iceland", "ireland-and-northern-ireland", "isle-of-man", "italy", "latvia", "liechtenstein", "lithuania", "luxembourg", "malta", "moldova", "monaco", "montenegro", "netherlands", "macedonia", "norway", "poland", "portugal", "romania", "russia", "serbia", "slovakia", "slovenia", "spain", "sweden", "switzerland", "turkey", "ukraine", "united-kingdom",
    )
}

private val asia by lazy {
    arrayOf(
        "afghanistan", "armenia", "azerbaijan", "bangladesh", "bhutan", "cambodia", "china", "iran", "iraq", "israel-and-palestine", "japan", "jordan", "kazakhstan", "kuwait", "kyrgyzstan", "laos", "lebanon", "malaysia-singapore-brunei", "maldives", "mongolia", "myanmar", "nepal", "north-korea", "pakistan", "philippines", "qatar", "russia", "saudi-arabia", "south-korea", "sri-lanka", "syria", "taiwan", "tajikistan", "thailand", "turkmenistan", "uzbekistan", "vietnam", "yemen",
    )
}

private val americas by lazy {
    arrayOf(
        "bahamas", "belize", "costa-rica", "cuba", "el-salvador", "guatemala", "haiti-and-domrep", "honduras", "jamaica", "nicaragua", "panama", "canada", "greenland", "mexico", "us", "argentina", "bolivia", "brazil", "chile", "colombia", "ecuador", "guyana", "paraguay", "peru", "suriname", "uruguay", "venezuela",
    )
}

private val africa by lazy {
    arrayOf(
        "algeria", "angola", "benin", "botswana", "burkina-faso", "burundi", "cameroon", "canary-islands", "cape-verde", "central-african-republic", "chad", "comores", "congo-brazzaville", "congo-democratic-republic", "djibouti", "egypt", "equatorial-guinea", "eritrea", "ethiopia", "gabon", "ghana", "guinea", "guinea-bissau", "ivory-coast", "kenya", "lesotho", "liberia", "libya", "madagascar", "malawi", "mali", "mauritania", "mauritius", "morocco", "mozambique", "namibia", "niger", "nigeria", "rwanda", "saint-helena-ascension-and-tristan-da-cunha", "sao-tome-and-principe", "senegal-and-gambia", "seychelles", "sierra-leone", "somalia", "south-africa", "south-sudan", "sudan", "swaziland", "tanzania", "togo", "tunisia", "uganda", "zambia", "zimbabwe",
    )
}

private val australia_oceania by lazy {
    arrayOf(
        "american-oceania", "australia", "cook-islands", "fiji", "ile-de-clipperton", "kiribati", "marshall-islands", "micronesia", "nauru", "new-caledonia", "new-zealand", "niue", "palau", "papua-new-guinea", "pitcairn-islands", "french-polynesia", "samoa", "solomon-islands", "tokelau", "tonga", "tuvalu", "vanuatu",
    )
}


// The code that makes above work
private const val GITHUB_API =
"https://api.github.com/repos/hvdwolf/SpeedAlert_Databases/releases"

private const val GITHUB_USER_AGENT = "SpeedAlert"


private val continentIsoCodes by lazy {
    mapOf(
        "europe" to eu_iso_codes.toList(),
          "asia" to asia_iso_codes.toList(),
          "americas" to americas_iso_codes.toList(),
          "africa" to africa_iso_codes.toList(),
          "australia-oceania" to australia_oceania_iso_codes.toList()
    )
}


/**
 * Checks the available updates for all given databases
 *
 * This function should be called from a co-routine.
 * The GitHub-check is done on Dispatchers.IO.
 */
suspend fun checkDatabaseUpdates(
    databases: List<CountryDatabase>
): DatabaseUpdateResult = withContext(Dispatchers.IO) {

    val updates = mutableListOf<DatabaseUpdate>()
    val errors = mutableListOf<String>()

    val latestVersions = try {
        getLatestVersions()
    } catch (e: Exception) {
        return@withContext DatabaseUpdateResult(
            updates = emptyList(),
            errors = listOf("GitHub: ${e.message}")
        )
    }

    for (database in databases) {

        val isoCode = database.isoCode.lowercase()

        val continent = continentIsoCodes.entries
        .firstOrNull { isoCode in it.value }
        ?.key

        if (continent == null) {
            errors.add("Onbekende ISO-code: $isoCode")
            continue
        }

        val remoteVersion = latestVersions[continent]

        if (remoteVersion == null) {
            errors.add("Geen GitHub-versie gevonden voor $continent")
            continue
        }

        val localVersion = getLocalVersion(database.file)

        if (localVersion == null || localVersion < remoteVersion) {

            updates.add(
                DatabaseUpdate(
                    isoCode = isoCode,
                    localVersion = localVersion,
                    remoteVersion = remoteVersion
                )
            )
        }
    }

    DatabaseUpdateResult(
        updates = updates,
        errors = errors
    )
}


/**
 * Reads the local version from the SQLite-database.
 *
 * A missing version table or invalid version is considered
 * as an old database.
 */
private fun getLocalVersion(file: File): String? {

    if (!file.exists()) return null

        return try {

            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->

                val tableExists = db.rawQuery(
                    """
                    SELECT name
                    FROM sqlite_master
                    WHERE type = 'table'
                AND name = 'version'
                """.trimIndent(),
                                              null
                ).use { cursor ->
                    cursor.moveToFirst()
                }

                if (!tableExists) {
                    return null
                }

                db.rawQuery(
                    "SELECT version FROM version LIMIT 1",
                    null
                ).use { cursor ->

                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                        ?.takeIf { it.matches(Regex("\\d{6}")) }
                    } else {
                        null
                    }
                }
            }

        } catch (e: Exception) {
            null
        }
}


/**
 * Retrieves the latest version per continent from the GitHub Releases.
 *
 * Example:
 * europe-202611
 * asia-202610
 */
private fun getLatestVersions(): Map<String, String> {

    val result = mutableMapOf<String, String>()

    var page = 1
    var hasMore = true

    while (hasMore) {

        val url = URL("$GITHUB_API?per_page=100&page=$page")

        val connection = url.openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                GITHUB_USER_AGENT
            )
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            val json = connection.inputStream
            .bufferedReader()
            .use { it.readText() }

            val releases = JSONArray(json)

            for (i in 0 until releases.length()) {

                val release = releases.getJSONObject(i)

                if (release.optBoolean("draft", false)) continue
                    if (release.optBoolean("prerelease", false)) continue

                        val tag = release.optString("tag_name")

                        val match = Regex(
                            "^(europe|asia|americas|africa|australia-oceania)-(\\d{6})$"
                        ).matchEntire(tag) ?: continue

                        val continent = match.groupValues[1]
                        val version = match.groupValues[2]

                        val current = result[continent]

                        if (current == null || version > current) {
                            result[continent] = version
                        }
            }

            hasMore = releases.length() == 100
            page++

        } finally {
            connection.disconnect()
        }
    }

    return result
}
