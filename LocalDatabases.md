# Using local speed limit and speed camera databases

As of version 2.0, Speedalert comes with optional local databases for speed limits on roads and local databases for speed cameras.
They do not come pre-installed as that would make it a massive application. You can download those from my Google Drive.

### Why local databases?
The application uses simple Sqlite databases (no rtree as Android doesn't support them). Sqlite db lookups take 0.2 - 3 ms (milliseconds), whereas overpass lookups take 200 ms up to 10 seconds.<br>
Next to that: overpass queries often fail because of "429 rate‑limit" errors or "504 gateway timeouts" or “Too many requests” or “Server overloaded”.<br>
Note though that no matter whether you use internet based overpass lookups or local databases, you will only get a speed limit if the road does have speed limits.<br>
SpeedAlert will on startup check for that database folder on the internal memory of your Dudu (or Xtrons or MTSC or phone). If the folder doesn't exist, the app will create it. That folder is (storage/emulated/0)/Android/media/xyz.hvdw.speedalert. That is where the databases should be saved. The app uses this folder as it is accessible for every user on every Android version.

### How does it work?
When you start the Service, SpeedAlert will do a GPS country check and then check if there is a database for that country, being a road speed limit database or a speed camera database or both.<br>
If there is/are, it/they wil be used. If not, it will do the overpass query on a shuffled overpass server.
(The country check itself is done once every 5 minutes as it is an "expensive" (time consuming) call and normally not necessary at all.)

## Speed limits on roads
The speed limit database works with "bounding boxes". These bounding boxes might return multiple speed limits (highway with parallel road, residential/city street with parallel service road, changing speed limit for next part of the road).
The app will select the nearest "way" to show the speed limit. Depending on accuracy of GPS, but also accuracy of correctly placed roads in OpenStreetMap, it might take and display an incorrect speed limit (if the road(s) do have a speed limit tag at all).


## Speed cameras
*Note: Speed camera only work from local database. It requires a have internet call to get all the data "in 300 meter range".*
Speed cameras use a "bounding half circle" of 300 meters, e.g. only looking forward. (300 m is not that big if you drive (too) fast).
It is not looking at the road, it is simply looking at the distance to a speed camera. That can be a speed camera, but also an integrated traffic light/speed camera. Currently "trajectory control" is not included ("trajectory control" meassures average speed over a certain distance. Only in very few countries).
It will play a double-tone (low-high) beep and show a "toast" at the bottom of the screen with "speed camera in xy meters" (once between 200-300 meters, once between 100 -200 meters, once within 100 meters).


## How do you use these databases?

The files are named by their 2-character ISO code as that is how Android and Overpass work. You can find your country and its ISO code inside this ["countries_iso.md"](https://github.com/hvdwolf/SpeedAlert/blob/main/countries_iso.md) file.<br>
The compressed road speed limit database files are named "xy.sqlite.7z".<br>
The compressed speed camera database files are named "xy-camera.sqlite.7z". *Note: Not all countries have speed cameras or registered speed cameras, like e.g. Greenland. These countries will miss in the list*.<br>

* Download the 7z file for your country onto your device from [my Special Database Release page](https://github.com/hvdwolf/SpeedAlert/releases/tag/V2026-03) from the folder "Speed_Limit_Databases" and/or the "Camera_Databases".
* Un7zip the file so you have "xy.sqlite" or "xy-camera.sqlite".
* Copy that **un7zipped** xy.sqlite or xy-camaera.sqlite file into "(storage/emulated/0)/Android/media/xyz.hvdw.speedalert".

Note that you really need to stop SpeedAlert and restart it to use the database. You can install databases for multiple countries if you need/want that.

## WARNING: In some countries it is NOT allowed to have speed cameras alets on your device. You have the responsibility to not use a camera database in that/your country. If you still do use the camera database in such a country, you yourself take full responsibility.
Speed limit warnings for roads are everywhere allowed.
