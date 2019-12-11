#!/usr/bin/env bash

#set -x

DX_DISPLAY_USAGE="Script to perform operations in multiple bundles using the Module management REST API.\n
if parameter '--start' is specified, the files are started\n
if parameter '--stop' is specified, the bundles are stopped\n
if parameter '--update' is specified, the bundles are updated\n
if parameter '--refresh' is specified, the bundles are refreshed\n
if parameter '--install' is specified, the files are installed\n
if parameter '--uninstall' is specified, the bundles are uninstalled\n
if parameter '--flushcache' is specified, the cache is flushed\n
Configuration is described in deployconfig.json file
The script will perform all actions described as parameters in the same order they were called
\nUsage:\n$0 bundleFilePath [bundleFilePathZ ... bundleFilePathN] [ --start | --stop | --update | --refresh ... N ]\n"

if [  $# -le 0 ]
then
	echo -e $DX_DISPLAY_USAGE
	exit 1
fi

# Must be set if you are running out of deploy.sh PATH
LOCAL_FOLDER=$(pwd)

# Choosing the proper jq binnary for your OS
JQ_BIN=${LOCAL_FOLDER}/jq-osx-amd64
UNAME_CMD_OUTPUT=$(uname -a)
if [[ "$UNAME_CMD_OUTPUT" == *"Linux"* && "$UNAME_CMD_OUTPUT" == *"i386"* ]]; then
	JQ_BIN=${LOCAL_FOLDER}/jq-linux32
elif [[ "$UNAME_CMD_OUTPUT" == *"Linux"* && "$UNAME_CMD_OUTPUT" == *"x86_64"* ]]; then
	JQ_BIN=${LOCAL_FOLDER}/jq-linux64
fi

tempFile=${LOCAL_FOLDER}/temp.tmp
configFile=${LOCAL_FOLDER}/deployconfig.json

# Read from configuration file
DX_HOST=$(cat $configFile | $JQ_BIN -r ".dx_host")
DX_USERNAME=$(cat $configFile | $JQ_BIN -r ".dx_username")
DX_PASSWORD=$(cat $configFile | $JQ_BIN -r ".dx_password")
DX_TOOL_USERNAME=$(cat $configFile | $JQ_BIN -r ".dx_tool_username")
DX_TOOL_PASSWORD=$(cat $configFile | $JQ_BIN -r ".dx_tool_password")
DELAY_BETWEEN_REST_SEC=$(cat $configFile | $JQ_BIN -r ".delay_seconds")

DX_REST_URL="${DX_HOST}/modules/api/bundles"
FLUSH_CACHE_URL="${DX_HOST}/modules/tools/cache.jsp?action=flushAllCaches&propagate=false"
CURL_OPTIONS="-s --user $DX_USERNAME:$DX_PASSWORD"
DATA="target="

count=0
countOrder=0
opOrder[0]=""
moduleFileList[0]=""
currentEnvBundleList[0]=""

while [ "$1" != "" ]; do
	case $1 in
		-h | --help )
			echo -e $DX_DISPLAY_USAGE
			exit 0
			;;
		--start )
			opOrder[$countOrder]="_start"
			countOrder=$((countOrder+1))
			;;
		--stop )
			opOrder[$countOrder]="_stop"
			countOrder=$((countOrder+1))
			;;
		--refresh )
			opOrder[$countOrder]="_refresh"
			countOrder=$((countOrder+1))
			;;
		--update )
			opOrder[$countOrder]="_update"
			countOrder=$((countOrder+1))
			;;
		--install )
			opOrder[$countOrder]="installFiles"
			countOrder=$((countOrder+1))
			;;
		--uninstall )
			opOrder[$countOrder]="_uninstall"
			countOrder=$((countOrder+1))
			;;
		--flushcache )
			if [[ $DX_TOOL_USERNAME == "" || $DX_TOOL_PASSWORD == "" ]]; then
				echo "--flushcache: dx_tool_username and dx_tool_password must be configured!"
				echo -e $DX_DISPLAY_USAGE
				exit 1
			else
				opOrder[$countOrder]="flushCache"
				countOrder=$((countOrder+1))
			fi
			;;
		* )
			moduleFileList[$count]=$1
			count=$((count+1))
			;;
	esac
	shift
done

# if no bundles display usage
if [ $count -le 0 ]; then
	echo -e $DX_DISPLAY_USAGE
fi

printBundleOpResult() {
	#"{\"bundleInfos\":[{\"version\":\"2.7.0\",\"groupId\":\"org.jahia.modules\",\"symbolicName\":\"file-uploader\",\"key\":\"org.jahia.modules/file-uploader/2.7.0\"}],\"message\":\"Operation successful\"}"
	#"{\"status\":404,\"reasonPhrase\":\"Not Found\",\"message\":\"Unable to find a module bundle corresponding to the key: org.jahia.modules/file-uploader/2.8.0\",\"cause\":null}"
	curlOutput=$1
	operation=$2
	bundleNameCheck=$3
	if [[ $curlOutput == *"Operation successful"* ]]; then
		echo "Bundle $bundleNameCheck $operation successful"
	else
		REQUEST_STATUS=$(echo $curlOutput | $JQ_BIN ". | .status")
		REQUEST_MESSAGE=$(echo $curlOutput | $JQ_BIN ". | .message")
		echo "Error $REQUEST_STATUS $operation bundle $bundleNameCheck: $REQUEST_MESSAGE"
	fi
}

getUpdatedBundlesState() {
	unset currentEnvBundleList
	curl $CURL_OPTIONS $DX_REST_URL/*/_info > $tempFile
	
	if [[ "$?" != "0" || "$(cat $tempFile)" == *"reasonPhrase"* ]]; then
		echo "Error getting information from bundles. Please check your configuration."
		exit 1
	fi
}

getBundleInfoObject() {
	bundleName=$1
	cat $tempFile | $JQ_BIN "[.[] ] | .[0] | with_entries( select(.key|contains(\"${bundleName}\") ) )"
}

refreshFullBundlesList() {
	count=0
	getUpdatedBundlesState
	for module in "${moduleFileList[@]}"
	do
		bundleName=$(echo ${module##*/} | rev | cut -d"-" -f2- | rev)
		installedVersionsCount=$(getBundleInfoObject $bundleName | $JQ_BIN 'length')

		for (( i=0; i < $installedVersionsCount; i++ ))
		do
			completeBundleName=$(getBundleInfoObject $bundleName | $JQ_BIN "keys | .[${i}]" | sed -e 's/^"//' -e 's/"$//')
			currentEnvBundleList[$count]="$completeBundleName"
			count=$((count+1))
		done
	done
}

restBundles() {
	restService=$1
	for bundleName in "${currentEnvBundleList[@]}"
	do
		CURL_OUTPUT=`curl $CURL_OPTIONS --data DATA --request POST $DX_REST_URL/$bundleName/$restService`
		printBundleOpResult "$CURL_OUTPUT" $restService $bundleName
		sleep $DELAY_BETWEEN_REST_SEC
	done
}

installFiles() {
	for moduleFile in "${moduleFileList[@]}"
	do
		CURL_OUTPUT=`curl $CURL_OPTIONS --form bundle=@$moduleFile --form start=false $DX_REST_URL/`
		printBundleOpResult "$CURL_OUTPUT" "installing" $moduleFile
		sleep $DELAY_BETWEEN_REST_SEC
	done
}

flushAllCache() {
	curl --user $DX_TOOL_USERNAME:$DX_TOOL_PASSWORD $FLUSH_CACHE_URL > /dev/null 2>&1
	echo "Done flushing cache"
	sleep $DELAY_BETWEEN_REST_SEC
}

for operationBundle in "${opOrder[@]}"
do
	refreshFullBundlesList
	if [[ $operationBundle == "installFiles" ]]; then
		 installFiles
	elif [[ $operationBundle == "flushCache" ]]; then
		flushAllCache
	else
		restBundles $operationBundle
	fi
done

rm -f $tempFile

echo "Deployment done!"
