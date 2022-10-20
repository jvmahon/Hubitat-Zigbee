library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "group Cluster 0x0004 Tools",
        name: "groupCluster0x0004",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

List<String> getGroupData(Map params = [:]){
	Map inputs = [ep: null ] << params
	// Groups can change, so treat as dynamic data
	List<String> groups = getDataRecordForEndpoint(*:inputs, isDynamicData: true )
							.get("groups", [])
    return groups
}

// Used in the group responses to process the Get Group Membership Response Command, ZCL 3.6.2.4.3
List<String> clearGroupData(Map params = [:]){
	Map inputs = [ep: null ] << params
	// Groups can change, so treat as dynamic data
	List<String> groups = getDataRecordForEndpoint(*:inputs, isDynamicData: true ).remove("groups")
	return groups
}

void processSpecificResponse0x0104_0004(Map descMap) {
    String ep = descMap.sourceEndpoint ?: descMap.endpoint
  
    String status = descMap.data[0]

    switch (descMap.command){
        case "00" : //add group response, ZCL 3.6.2.4.1
            if (status in ["00","8A"]) { // Status enumerations are in ZCL Table 2.6.3
                group = descMap.data[1] + descMap.data[2]
                if (group in getGroupData(ep:ep)) {
                    if (txtEnable) log.info "group membership refreshed"
                } else {
                    getGroupData(ep:ep).add(group)
                    if (txtEnable) log.info "group membership added"
                }
            } else {
                log.warn "${device.displayName}'s group table is full, unable to add group..."
            }
            break
		case "01": // View Group Response, ZCL 3.6.2.4.2
			log.warn "Received a View Group command that is not processed. This is generally harmless, but may want to add processing to display the group name: ${descMap.inspect()}"
			break
        case "02" : //group membership response. Does not include a "status", ZCL 3.6.2.4.3
            Integer groupCount = hexStrToUnsignedInt(descMap.data[1])
            if (groupCount == 0 && getGroupData(ep:ep) != []) {
                List<String> cmds = []
				getGroupData(ep:ep).each {
                    cmds.addAll(zigbee.command(0x0004,0x00,[[destEndpoint:ep]],0,"${it} 00")) // Add Group Command for group ${it} with "00" as name
                    if (txtEnable) log.warn "update group:${it} on device"
                }
				clearGroupData(ep:ep)
                sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
            } else {
                //get groups and update groupData...
                Integer crntByte = 0
                for (int i = 0; i < groupCount; i++) {
                    crntByte = (i * 2) + 2
                    group = descMap.data[crntByte] + descMap.data[crntByte + 1]

					if ( !(group in getGroupData(ep:ep)) ) {
						getGroupData(ep:ep).add(group)
                        if (txtEnable) log.info "group added to global data storage list"
                    } else {
                        if (logEnable) log.debug "group already exists in global data storage list..."
                    }
                }
            }
            break
        case "03" : //remove group response, ZCL 3.6.2.4.4
			if (status == "00") {
				group = descMap.data[1] + descMap.data[2]
				getGroupData(ep:ep).remove(group)
				if (txtEnable) log.info "group membership ${group} removed"
			}
            break
        default :
            log.error "group command not handled:${descMap.inspect()}"
    }
    // state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )	

}

void processGlobalResponse0x0104_0004(Map descMap) {
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processClusterResponse0x0104_0004(Map descMap) {
    if (descMap.clusterInt  != 0x0004)  return 
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0004(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0004(descMap) // Global Commands
    }
}

void getGroupMembership(Map params = [:] ) {
    Map inputs = [ep: null ] << params

	if (logEnable) log.debug "Getting group membership with inputs: ${inputs}"
	sendZCLAdvanced(
			destinationEndpoint: (inputs.ep) ,
			clusterId: "0004" ,
			isClusterSpecific: true ,
			commandId: 0x02,             // Get Group membership command, ZCL 3.6.2.3.4, ZCL Table 3-37
			commandPayload: "00",     // Value of 00 gets all groups in whcih the device is a member. ZCL 3.6.2.3.4.1
		)
}

void configure0x0104_0004(String ep = getEndpointId(device)) {
    getGroupMembership(ep:ep)
}
void unbind0x0104_0004(String ep = getEndpointId(device)) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0004 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0004: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0004(String ep = getEndpointId(device)) {
	// configure0x0104_0004(ep)
    refresh0x0104_0004(ep)
}
void refresh0x0104_0004(String ep = getEndpointId(device)) {
    getGroupMembership(ep:ep)
}


