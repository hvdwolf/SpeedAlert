#!/usr/bin/bash

######################################################################################################
#  Region/Continent definitions

CONTINENT_IDS="africa, antarctica, asia, australia-oceania, central-america, europe, north-america, south-america"

EU="albania andorra austria azores belarus belgium bosnia-herzegovina bulgaria croatia cyprus czech-republic denmark estonia faroe-islands finland france georgia germany greece hungary iceland ireland-and-northern-ireland isle-of-man italy kosovo latvia liechtenstein lithuania luxembourg macedonia malta moldova monaco montenegro netherlands norway poland portugal romania russia serbia slovakia slovenia spain sweden switzerland turkey ukraine united-kingdom"
EU_A="austria belgium denmark france germany ireland-and-northern-ireland italy luxembourg netherlands norway poland portugal spain sweden switzerland ukraine united-kingdom" 
EU_B="albania andorra azores belarus bosnia-herzegovina bulgaria croatia cyprus czech-republic estonia faroe-islands finland georgia greece guernsey-jersey hungary iceland isle-of-man kosovo latvia liechtenstein lithuania macedonia malta moldova monaco montenegro romania russia serbia slovakia slovenia turkey"
DE_regions="baden-wuerttemberg bayern berlin brandenburg bremen hamburg hessen mecklenburg-vorpommern niedersachsen nordrhein-westfalen rheinland-pfalz saarland sachsen sachsen-anhalt schleswig-holstein thueringen"
AS="afghanistan armenia azerbaijan bangladesh bhutan cambodia china iran iraq israel japan jordan kazakhstan kuwait kyrgyzstan laos lebanon malaysia-singapore-brunei maldives mongolia myanmar nepal north-korea pakistan philippines qatar saudi-arabia south-korea sri-lanka syria taiwan tajikistan thailand turkmenistan uzbekistan vietnam yemen"
CAM="bahamas belize costa-rica cuba el-salvador guatemala haiti-and-dominican-republic honduras jamaica nicaragua panama"
NAM="canada greenland mexico us"
SAM="argentina bolivia brazil chile colombia ecuador guyana paraguay peru suriname uruguay venezuela"
AF="algeria angola benin botswana burkina-faso burundi cameroon canary-islands cape-verde central-african-republic chad comores congo-brazzaville congo-democratic-republic djibouti egypt equatorial-guinea eritrea ethiopia gabon ghana guinea guinea-bissau ivory-coast kenya lesotho liberia libya madagascar malawi mali mauritania mauritius morocco mozambique namibia niger nigeria rwanda saint-helena-ascension-and-tristan-da-cunha sao-tome-and-principe senegal-and-gambia seychelles sierra-leone somalia south-africa south-sudan sudan swaziland tanzania togo tunisia uganda zambia zimbabwe"

######################################################################################################
#  functions

Info () {
    printf "Provide a country, like Netherlands or Belgium or Germany\n"
    printf "for example:  ./maxspeed_db.sh france\n\n"
    printf "* other options:\n* Duitsland, Germany, EU_A, EU_B, DE  => Regions Germany\n"
    printf "* Frankrijk, FR => Regions France\n"
    printf "* UK, VK, United_Kingdom => England, Scotland, Wales, Northern_Ireland\n"
    printf "* Examples separate countries => Gb_england, Netherlands, Belgium, Luxembourg, Denmark\n"
    printf "* Examples separate regions: Germany_thueringen, France_auvergne-rhone-alpes, Italy_liguria\n"
    printf "Note: France (Italy) will download the entire France_road (Italy) map. Frankrijk (Italie or IT)  will download all regions\n\n"
    exit
}

Create_Databases() {
    CONTINENT=$1
    printf "working on continent $CONTINENT\n\n"
    shift
    for country in "$@";
    do
        echo "Working on $country"
        ./build_camera_dbs.py --continent $CONTINENT -o $country
    done
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
                "EU" | "eu" | "EU_A" | "eu_a" | "EU_B" | "eu_b")
                    ./build_camera_dbs.py --continent europe -o $country;;
                "AS" | "as")
                   ./build_camera_dbs.py --continent asia $country;;
                "CAM" | "cam")
                   ./build_camera_dbs.py --continent central-america $country;;
                "NAM" | "nam")
                   ./build_camera_dbs.py --continent north-america $country;;
                "SAM" | "sam")
                   ./build_camera_dbs.py --continent south-america $country;;
                "AF" | "af")
                   ./build_camera_dbs.py --continent africa $country;;
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
       Create_Databases "europe" $EU;;
    "EU_A" | "eu_a")
       Create_Databases "europe" $EU_A;;
    "EU_B" | "eu_b")
       Create_Databases "europe" $EU_B;;
    "AS" | "as")
       Create_Databases "asia" $AS;;
    "CAM" | "cam")
       Create_Databases "central-america" $CAM;;
    "NAM" | "nam")
       Create_Databases "north-america" $NAM;;
    "SAM" | "sam")
       Create_Databases "south-america" $SAM;;
    "AF" | "af")
       Create_Databases "africa" $AF;;

    *) Create_Single_Country_Database $ACTION;;
esac