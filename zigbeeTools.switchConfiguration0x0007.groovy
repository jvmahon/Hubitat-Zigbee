/*
This library is currently just a stub.
*/
library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "switch Configuration Cluster 0x0007 Tools",
        name: "switchConfiguration0x0007",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

void processAttributes0x0007(Map descMap){
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processSpecificResponse0x0104_0007(Map descMap) {
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processGlobalResponse0x0104_0007(Map descMap) {
        String ep = descMap.sourceEndpoint ?: descMap.endpoint

}

void processClusterResponse0x0104_0007(Map descMap){
    if (descMap.clusterInt  != 0x0007)  return 
    log.debug "Received a message for cluster 0x0007. ${descMap.inspect()}"
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0007(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0007(descMap) // Global Commands
    }
}	

void configure0x0104_0007(String ep = getEndpointId(device) ) {
	String cmd = "zdo bind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0007 {${device.zigbeeId}} {}" 
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE)) 
}
void unbind0x0104_0007(String ep = getEndpointId(device) ) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0007 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0007: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0007(String ep = getEndpointId(device) ) {
	// configure0x0104_0007(ep)
	refresh0x0104_0007(ep)
}
void refresh0x0104_0007(String ep = getEndpointId(device) ) {
    
        sendZCLAdvanced(
            clusterId: 0x0007 ,
            destinationEndpoint: ep, 
            commandId: 0x00,
            commandPayload: ["0000"], // List of attributes of interest [0x0000] in reversed octet form
        )
}

