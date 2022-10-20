library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Basic Cluster 0x0000 Tools",
        name: "basicCluster0x0000",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)


void processAttributes0x0000(Map descMap){
	String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processSpecificResponse0x0104_0000(Map descMap) {
	String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processGlobalResponse0x0104_0000(Map descMap) {
	String ep = descMap.sourceEndpoint ?: descMap.endpoint
	log.debug "Received a basic response ${descMap.inspect()}"
}

void processClusterResponse0x0104_0000(Map descMap){
    if (descMap.clusterInt  != 0x0000)  return 
    if (logEnable) log.debug "Received a message for cluster 0x0000. ${descMap.inspect()}"
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0000(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0000(descMap) // Global Commands
    }
}	

void configure0x0104_0000(String ep = getEndpointId(device) ) {
	String cmd = "zdo bind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0000 {${device.zigbeeId}} {}" 
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE)) 
}
void unbind0x0104_0000(String ep = getEndpointId(device) ) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0000 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0000: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0000(String ep = getEndpointId(device) ) {
	// configure0x0104_0000(ep)
	refresh0x0104_0000(ep)
}
void refresh0x0104_0000(String ep = getEndpointId(device) ) {
  
	sendZCLAdvanced(
		clusterId: 0x0000 , 
		destinationEndpoint: ep, 
		commandId: 0x00, 
		commandPayload: ["0000", "0001", "0002", "0003", "0004", "0005", "0006"] // List of attributes of interest [0x0000] in reversed octet form
	)
	sendZCLAdvanced(
		clusterId: 0x0000 , 
		destinationEndpoint: ep, 
		commandId: 0x00, 
		commandPayload: ["0007", "0008", "0009", "000A", "0012", "4000"] // List of attributes of interest [0x0000] in reversed octet form
	)    
    sendZCLAdvanced(
		clusterId: 0x0000 , 
		destinationEndpoint: ep, 
		commandId: 0x00, 
		commandPayload: ["000B"] // List of attributes of interest [0x0000] in reversed octet form
	)   
}

