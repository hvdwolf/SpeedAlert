**Changelog V1.7 (22 Feb 2026)**

 - Currently the app simply measures every 4 seconds. In this version added options to influence the performance: 
   - Minimum Distance option (0m, 10m, 25m, 50m)
   - Interval: 2 seconds (4–8 MB/hour), 4 seconds (2–4 MB/hour), 8 seconds (1–2 MB/hour)
 - When you select "Start service on opening app", you now have a second switch to minimize the app (after starting the service).
 - Add a "checkForNewVersion" at startup with a dialog: "A newer version X.Y is available. Open download page?"  _(Of course this will only start working as of next release)_
 - Add Portuguese, Korean, simplified Chinese (China) and tradional Chinese (Taiwan) language.


**Changelog V1.6 (18 Feb 2026)**

**Use hybrid form of location positioning:**
In version 1.5, I switched from locationmanager to FusedLocationProviderClient, which is supposed to be the way forward on Android 14/15/16, but it gives away a lot of control.

- If Android "thinks" you do not need GPS, you do not get GPS.
- If Android "thinks" that  WiFi/cell (A-GPS) location is good enough, you don't get GPS.
- And you need to very strict use "ignore battery optimization".

In these cases you see "no GPS" or very bad accuracy.

Locationmanager uses only GPS (GNSS) and is faster. Only in cities with high buildings, FusedLocationProviderClient can combine a bad GPS signal with a not so accurate GPS/cell signal and thereby improve the position.

So I switched back to locationmanager that will continue to function till Android 17, with fallback to FusedLocationProviderClient if for some reason the locationmanager functionality does not deleiver a valid GPS signal.

**Changelog V1.5 (16 Feb 2026)**

- Fix crash on startup of app on (real) Android 14/15/16.
- Switch from LocationManager and LocationListener (Android 13 and down) to Android 14+ FusedLocationProviderClient.
- Add country speedlimit fallbacks for a number of countries. Currently: NL, BE, DE, FR, GB, DK, RU, PL, UA, IL, NO, SE, IT, ES, PT, US (in general, no state specifics), VN, KR, JP, TR, BR, AU, CA, IE, CN, IN, TW. This will use the country defaults when the road has no speed limit tag. As this is a ["data source code"](https://github.com/hvdwolf/SpeedAlert/blob/main/app/src/main/java/xyz/hvdw/speedalert/CountrySpeedFallbacks.kt) file, your country can easily be added when it is missing.

**Changelog V1.4 (15 Feb 2026)**

- Add switch in Settings to start service on start of SpeedAlert.
- With regard to the Beep: Replace mediaplayer use for soundpool. This should not interfere with (BT) music and is also lighter on resources.

**Changelog V1.3  (14 Feb 2026)**

- Fix: return of mph/kmh switch with "Read this!" button. What does it do:
  - SpeedAlert can convert both GPS speed and road speed limits.
  - If you drive in a km/h country but prefer mph, both values are converted.
  - If you drive in an mph country but prefer km/h, both values are converted.
  - If your preference matches the country, no conversion is done.
  - This ensures the display always matches your chosen unit.
 - Bring all the settings to a separate Settings screen.
 - Add an About button with alertdialog.
 - Button colors did not switch to night mode. Now they do.
 - Do some further AI translations of strings added after V1.0

**Changelog V1.2  (13 Feb 2026)**

- Add volume slider to decrease "beep" volume.
- Play beep immediately and then only every 10 seconds on continuous overspeeding.
- Make "Day" background from pure white to light grey.
- Move speed lookup test to Debug screen
- Move check overlay permission to debug screen
- Overlay text:
  - Make white "normal" text follow day-night mode as well (day-> white; night -> grey)
  - Make a slider to reduce text brightness in the overlay from pure white to very dim.
  - Make a slider to change overlay text in 5 steps: smallest, smaller, default, bigger, biggest.
- Completely remove kmh / mph switch. It was used for speed conversions, but that is nonsence. "mph countries"  (United States, United Kingdom, Liberia, Myanmar, Bahamas, Belize, Cayman Islands) have road speeds already in mph.  No conversion necessary. Only use the nominatim country identifier and inside an mph country only change the extension: 50 kmh or 50 mph.
- Switch to dynamic speedlimit radius depending on GPS accuracy to increase "get_speed_limit". The higher the GPS accuracy, the smaller the search circle to prevent mismatches (GPS accuracy <= 2.5m -> r=10m;  gps acc <= 5m  -> r=15m;  gps acc <=10m -> r=20m; else r=25m)


**Changelog V1.1  (10 Feb 2026)**

- Add kmh (default) to mph switch.
- Add overspeed tolerance slider with percentage and real speed (kmh or mph) switch. The overlay will still turn red when overspeeding, but the alarm "beep-beep-beep" will only trigger on overspeed setting (percentage or kmh/mph).
- Overlay can now be dragged anywhere on screen and it remembers its position.
- Change Kalman filter: This is used to smoothen "spikes" in the GPS chip measurements.
  1.  The GPS chips in Chinese head units are not that good, but the chip in the Dudu7 is very good. So the accuracy tolerance in the code has been decreased (less miss-matches on parallel roads, viaducts/crossovers, etcetera)
  2.  The Kalman filter has been set from "conservative" to "aggressive" (Google Maps settings). Speed changes will be reflected immediately and no longer be "smoothened" out.
- GPS-location and "get_speed_limit" ran sequentially in one loop. GPS location is immediate (200ms), but internet speed limit call can take up to max. 10 seconds, which suspends also speed updates in the overlay. They now run in their own parallel co-routines/threads.
- Move Debug scrollview (debug log) and the copy/share/empty buttons to a separate Debug screen via the new "Debug Screen" button.

**Changelog V1.0  (8 Feb 2026)**

A simple tool that uses an overlay to display speed and max speed and plays an alarm sound when you exceed the speed limit.<br>
See the [Readme](https://github.com/hvdwolf/SpeedAlert/blob/main/README.md).