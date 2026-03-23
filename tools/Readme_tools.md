# Installation and usage

In this folder you will find a number of python scripts and a number of bash shell scripts.<br>
The python scripts are meant for "single country" creation of databases.<br>
The bash shell scripts are for bulk creation of databases for continents and they use the python scripts to create country by country.<br>
The bash scripts will only run on Linux/MacOS (or Windows with WSL). The python scripts will run on Linux/MacOS as these come pre-installed with python. You can also install python on Windows, or install it inside WSL on Windows to run the python scripts on Windows.


## Single country database creation

 * build_camera_db.py : This python script will only build a speed camera database for one country.
 * build_speedlimit_db.py : This python script will only build a road speed limit database for one country.
 * build_speedlimit_db_ui.py : This is a graphical UI python script that will only build a road speed limit database for one country.
 * build_speed_and_camera_dbs.py: This python script will only build a road speed limit database and a speed camera database for one country.

## Continent generation of databases

* create_camera_sqlite_countries.sh : This will create speed camera databases for one continent.
* create_speedlimit_and_camera_dbs.sh : This will create road speed limit databases and speed camera databases for one continent.
* create_speedlimit_sqlite_countries.sh : This will create road speed limit databases for one continent.


## General

All scripts, python and/or bash, show you an quick info/help if you start them without parameter(s).<br>
To create the biggest countries (when looking at data) like Germany, US, France, China and a few others you need 6GB internal memory. Other countries need less, even down to 50MB.