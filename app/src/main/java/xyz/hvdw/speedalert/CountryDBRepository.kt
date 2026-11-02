package xyz.hvdw.speedalert


private val eu_iso_codes by lazy {
    arrayOf(
        "ad", "al", "at", "ba", "be", "bg", "by", "ch", "cy", "cz", "de", "dk", "ee", "es", "fi", "fo", "fr", "gb", "ge", "gr", "hr", "hu", "ie", "is", "it", "li", "lt", "lu", "lv", "mc", "md", "me", "mk", "mt", "nl", "no", "pl", "pt", "ro", "rs", "ru", "se", "si", "sk", "tr", "ua",
    )
}

private val asia_iso_codes by lazy {
    arrayOf(
        "af", "am", "az", "bd", "bt", "kh", "cn", "ir", "iq", "jp", "jo", "kz", "kg", "la", "lb", "my", "mv", "mn", "mm", "np", "kp", "pk", "ph", "kr", "lk", "sy", "tw", "tj", "th", "tm", "uz", "vn", "ye",
    )
}

private val northamerica_iso_codes by lazy {
    arrayOf(
        "ca", "gl", "mx", "us",
    )
}

private val centralamerica_iso_codes by lazy {
    arrayOf(
        "bs", "bz", "cr", "cu", "sv", "gt", "hn", "jm", "ni", "pa",
    )
}

private val southamerica_iso_codes by lazy {
    arrayOf(
        "ar", "bo", "br", "cl", "co", "ec", "gy", "py", "pe", "sr", "uy", "ve",
    )
}

private val africa_iso_codes by lazy {
    arrayOf(
        "dz", "ao", "bj", "bw", "bf", "bi", "cm", "cv", "cf", "td", "cg", "cd", "dj", "eg", "gq", "er", "et", "ga", "gh", "gn", "gw", "ci", "ke", "ls", "lr", "ly", "mg", "mw", "ml", "mr", "mu", "ma", "mz", "na", "ne", "ng", "rw", "sh", "st", "sn", "gm", "sc", "sl", "so", "za", "ss", "sd", "sz", "tz", "tg", "tn", "ug", "zm", "zw",
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

private val centralamerica by lazy {
    arrayOf(
        "bahamas", "belize", "costa-rica", "cuba", "el-salvador", "guatemala", "haiti-and-domrep", "honduras", "jamaica", "nicaragua", "panama"
    )
}

private val northamerica by lazy {
    arrayOf(
        "canada", "greenland", "mexico", "us",
    )
}

private val southamerica by lazy {
    arrayOf(
        "argentina", "bolivia", "brazil", "chile", "colombia", "ecuador", "guyana", "paraguay", "peru", "suriname", "uruguay", "venezuela",
    )
}

private val africa by lazy {
    arrayOf(
        "algeria", "angola", "benin", "botswana", "burkina-faso", "burundi", "cameroon", "canary-islands", "cape-verde", "central-african-republic", "chad", "comores", "congo-brazzaville", "congo-democratic-republic", "djibouti", "egypt", "equatorial-guinea", "eritrea", "ethiopia", "gabon", "ghana", "guinea", "guinea-bissau", "ivory-coast", "kenya", "lesotho", "liberia", "libya", "madagascar", "malawi", "mali", "mauritania", "mauritius", "morocco", "mozambique", "namibia", "niger", "nigeria", "rwanda", "saint-helena-ascension-and-tristan-da-cunha", "sao-tome-and-principe", "senegal-and-gambia", "seychelles", "sierra-leone", "somalia", "south-africa", "south-sudan", "sudan", "swaziland", "tanzania", "togo", "tunisia", "uganda", "zambia", "zimbabwe",
    )
}

private val oceania by lazy {
    arrayOf(
        "american-oceania", "australia", "cook-islands", "fiji", "ile-de-clipperton", "kiribati", "marshall-islands", "micronesia", "nauru", "new-caledonia", "new-zealand", "niue", "palau", "papua-new-guinea", "pitcairn-islands", "french-polynesia", "samoa", "solomon-islands", "tokelau", "tonga", "tuvalu", "vanuatu",
    )
}


