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