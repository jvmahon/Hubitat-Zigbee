library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Processing Common to Profile ID 0x0104 Clusters",
        name: "commonClusterProcessing",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

void commonProcessingSpecificResponse(Map descMap) {
	
	// For some reason, Hubitat parsing doesn't always include the status. I prefer it always be there!
	if (descMap.status.is( null ) ) descMap.status = descMap.data[0] // Status enumerations are in ZCL Table 2.6.3
	
}
void commonProcessingGlobalResponse(Map descMap) {
    switch(descMap.command) {
        case "12": // Discover Commands Received Response
            if (descMap.data[0] != "01") log.error "In function processCommonClusterResponses0x0104, received a respond with a first data byte note equal to 01. This means that there were too many commands to fit in the response. Code needs to be updated to address this."
            setClusterCommandsSupported(clusterId:descMap.clusterId, ep:(descMap.sourceEndpoint), commandList:(descMap.data.tail() ) ) 
        	state.deviceData = getDataRecordByNetworkId()
            break
    }
}

void processCommonClusterResponses0x0104(Map descMap){
	if ( descMap?.profileId != "0104") return 
	if (descMap.isClusterSpecific == true ) {
		commonProcessingSpecificResponse(descMap)
	} else {
		commonProcessingGlobalResponse(descMap)
	}
}