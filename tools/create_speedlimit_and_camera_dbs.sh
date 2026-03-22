#!/usr/bin/bash

######################################################################################################
#  Region/Continent definitions

CONTINENT_IDS="africa, antarctica, asia, australia-oceania, central-america, europe, north-america, south-america"

EU="albania andorra austria azores belarus belgium bosnia-herzegovina bulgaria croatia cyprus czech-republic denmark estonia faroe-islands finland france georgia germany gibraltar greece hungary iceland ireland isle-of-man italy jersey latvia liechtenstein lithuania luxembourg malta moldova monaco montenegro netherlands north-macedonia norway poland portugal romania russia serbia slovakia slovenia spain sweden switzerland turkey ukraine united-kingdom"
#EU_A="austria belgium denmark france germany ireland-and-northern-ireland italy luxembourg netherlands norway poland portugal spain sweden switzerland ukraine united-kingdom" 
#EU_B="albania andorra azores belarus bosnia-herzegovina bulgaria croatia cyprus czech-republic estonia faroe-islands finland georgia greece guernsey-jersey hungary iceland isle-of-man kosovo latvia liechtenstein lithuania macedonia malta moldova monaco montenegro romania russia serbia slovakia slovenia turkey"
AS="afghanistan armenia azerbaijan bangladesh bhutan cambodia china iran iraq israel japan jordan kazakhstan kuwait kyrgyzstan laos lebanon malaysia-singapore-brunei maldives mongolia myanmar nepal north-korea pakistan philippines qatar russia saudi-arabia south-korea sri-lanka syria taiwan tajikistan thailand turkmenistan uzbekistan vietnam yemen"
CAM="bahamas belize costa-rica cuba el-salvador guatemala haiti-and-dominican-republic honduras jamaica nicaragua panama"
NAM="canada greenland mexico us"
SAM="argentina bolivia brazil chile colombia ecuador guyana paraguay peru suriname uruguay venezuela"
AF="algeria angola benin botswana burkina-faso burundi cameroon canary-islands cape-verde central-african-republic chad comores congo-brazzaville congo-democratic-republic djibouti egypt equatorial-guinea eritrea ethiopia gabon ghana guinea guinea-bissau ivory-coast kenya lesotho liberia libya madagascar malawi mali mauritania mauritius morocco mozambique namibia niger nigeria rwanda saint-helena-ascension-and-tristan-da-cunha sao-tome-and-principe senegal-and-gambia seychelles sierra-leone somalia south-africa south-sudan sudan swaziland tanzania togo tunisia uganda zambia zimbabwe"

######################################################################################################
#  functions

Info () {
    printf "Provide a continent to the script by its abbreviation: EU, AS, AF, CAM, NAM, SAM\n"
    printf "For Europe, Asia, Africa, Central America, North America, South America\n"
    printf "For example:  \"./create_speedlimit_and_camera_dbs.sh EU\" or \"./create_speedlimit_and_camera_dbs.sh eu\"\n\n"
    printf "The script will go over all countries for that continent and put these in folder \"DBs/<continent>\"\n\n"
    exit
}

Create_Databases() {
    CONTINENT=$1
    printf "working on continent $CONTINENT\n\n"
    shift
    for country in "$@";
    do
        echo "Working on $country"
        ./build_speed_and_camera_dbs.py --continent $CONTINENT -o $country
    done
}

Move_Databases() {
    CONTINENT=$1
    printf "moving created speed limit and camera databases on continent $CONTINENT to folder DBs/${CONTINENT}\n\n"
    rm -rf DBs/${CONTINENT}
    mkdir -p DBs/${CONTINENT}
    mv *.7z DBs/${CONTINENT}
}

Create_Single_Country_Database() {
    echo "Starting on $1"
    country="$1"
    found=false

    for list in EU_A EU_B AS CAM NAM SAM AF; do
        if echo "${!list}" | tr ' ' '\n' | grep -qx "$country"; then
            echo "$country is in $list"
            found=true
            case $list in
                #"EU" | "eu" | "EU_A" | "eu_a" | "EU_B" | "eu_b")
                "EU" | "eu")
                    ./build_speed_and_camera_dbs.py --continent europe -o $country;;
                "AS" | "as")
                   ./build_speed_and_camera_dbs.py --continent asia $country;;
                "CAM" | "cam")
                   ./build_speed_and_camera_dbs.py --continent central-america $country;;
                "NAM" | "nam")
                   ./build_speed_and_camera_dbs.py --continent north-america $country;;
                "SAM" | "sam")
                   ./build_speed_and_camera_dbs.py --continent south-america $country;;
                "AF" | "af")
                   ./build_speed_and_camera_dbs.py --continent africa $country;;
                *) 
                   printf "\n\nYou need to use the English names as they are used on https://download.geofabrik.de/\n"
                   printf "There is no country with the name \"%s\".\n" "$country"
                   ;;
            esac
        fi
    done
    if !($found)
    then
        printf "\n\nThere is no country with the name \"%s\".\n" "$country"
        printf "You need to use the English names as they are used on https://download.geofabrik.de/\n\n"
    fi        
}
#  functions
######################################################################################################

######################################################################################################
#  main part

# No country or abbreviation given?
if [ "$1" = "" ]
then
    Info;
fi
ACTION="$@"

case $ACTION in
    "EU" | "eu")
       Create_Databases "europe" $EU;
       Move_Databases "europe";;
    "AS" | "as")
       Create_Databases "asia" $AS;
       Move_Databases "asia";;
    "CAM" | "cam")
       Create_Databases "central-america" $CAM;
       Move_Databases "central-america";;
    "NAM" | "nam")
       Create_Databases "north-america" $NAM;
       Move_Databases "north-america";;
    "SAM" | "sam")
       Create_Databases "south-america" $SAM;
       Move_Databases "south-america";;
    "AF" | "af")
       Create_Databases "africa" $AF;
       Move_Databases "africa";;
    *) Create_Single_Country_Database $ACTION;;
esac