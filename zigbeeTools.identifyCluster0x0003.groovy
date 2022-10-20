library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Identify Cluster 0x0003 Tools",
        name: "identifyCluster0x0003",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

// Next function *should* start the identify routine, but I haven't been able to get it work on my devices!
void identify(Map params = [:]){
    Map inputs = [ep: getEndpointId(device) ] << params

    sendZCLAdvanced(
        clusterId: 0x0003 ,
        destinationEndpoint: inputs.ep,  
        isClusterSpecific: true , 
        commandId: 0x01,  // Identify Command. ZCL 3.5.2.3.1
        commandPayload: ["0005"] // Identify time in seconds. ZCL 3.5.2.2
    )
}

void processAttributes0x0003(Map descMap){
	String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processSpecificResponse0x0104_0003(Map descMap) {
	String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processGlobalResponse0x0104_0003(Map descMap) {
	String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processClusterResponse0x0104_0003(Map descMap){
    if (descMap.clusterInt  != 0x0003)  return 
    if (logEnable) log.debug "Received a message for cluster 0x0003. ${descMap.inspect()}"
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0003(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0003(descMap) // Global Commands
    }
}	

void configure0x0104_0003(String ep = getEndpointId(device) ) {
	String cmd = "zdo bind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0003 {${device.zigbeeId}} {}" 
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE)) 
}
void unbind0x0104_0003(String ep = getEndpointId(device) ) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0003 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0003: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0003(String ep = getEndpointId(device) ) {
	// configure0x0104_0003(ep)
	refresh0x0104_0003(ep)
}
void refresh0x0104_0003(String ep = getEndpointId(device) ) {
  
	sendZCLAdvanced(
		clusterId: 0x0003 , 
		destinationEndpoint: ep, 
		commandId: 0x00, 
		commandPayload: ["0000"] // List of attributes of interest [0x0000] in reversed octet form
	)
}

