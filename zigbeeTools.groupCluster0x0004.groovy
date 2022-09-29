library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "group Cluster 0x0004 Tools",
        name: "groupCluster0x0004",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

List<String> getGroupData(Integer ep = getEndpointId(device)){
	List<String> groups = getClusterDataRecord(clusterInt:0x0004, profileId:"0104", ep:ep).get("groups", [])
    if (logEnable) log.debug "Returning groups record with contents ${groups}"
    return groups
}
void clearGroupData(Integer ep = getEndpointId(device)){
	List<String> groups = getClusterDataRecord(clusterInt:0x0004, profileId:"0104", ep:ep).remove("groups")
}

void processSpecificResponse0x0104_0004(Map descMap) {
	Integer ep = Integer.parseInt(descMap.sourceEndpoint, 16)
    
	if (logEnable) log.debug "Group Cluster 0x0004 library processing message: ${descMap}"

    String status = descMap.data[0]

    switch (descMap.command){
        case "00" : //add group response
            if (status in ["00","8A"]) {
                group = descMap.data[1] + descMap.data[2]
                if (group in getGroupData(ep)) {
                    if (txtEnable) log.info "group membership refreshed"
                } else {
                    getGroupData(ep).add(group)
                    if (txtEnable) log.info "group membership added"
                }
            } else {
                log.warn "${device.displayName}'s group table is full, unable to add group..."
            }
            break
		case "01": // View Group Response
			if(logEnable) log.debug "Received a View Group command that is not processed: ${descMap}"
			break
        case "02" : //group membership response
            Integer groupCount = hexStrToUnsignedInt(descMap.data[1])
            if (groupCount == 0 && getGroupData(ep) != []) {
                List<String> cmds = []
				getGroupData(ep).each {
                    cmds.addAll(zigbee.command(0x0004,0x00,[[destEndpoint:(inputs.ep)]],0,"${it} 00")) // Add Group Command for gorup ${it} with "00" as name
                    if (txtEnable) log.warn "update group:${it} on device"
                }
				clearGroupData(ep)
                sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
            } else {
                //get groups and update groupData...
                Integer crntByte = 0
                for (int i = 0; i < groupCount; i++) {
                    crntByte = (i * 2) + 2
                    group = descMap.data[crntByte] + descMap.data[crntByte + 1]

					if ( !(group in getGroupData(ep)) ) {
						getGroupData(ep).add(group)
                        if (txtEnable) log.info "group added to global data storage list"
                    } else {
                        if (txtEnable) log.debug "group already exists in global data storage list..."
                    }
                }
            }
            break
        case "03" : //remove group response
            group = descMap.data[1] + descMap.data[2]
			getGroupData(ep).remove(group)
            if (txtEnable) log.info "group membership removed"
            break
        default :
            log.error "group command not handled:${descMap}"
    }
    if (getGroupData(ep)) {
	    if (state.deviceData.is( null ) ) state.deviceData = [:]
	    state.deviceData = getDataRecordByNetworkId()
    }
}

void processGlobalResponse0x0104_0004(Map descMap) {
    switch (descMap.command){
        case "12":
            Integer ep = Integer.parseInt(descMap.sourceEndpoint, 16)
            setClusterCommandsSupported(clusterInt:0x0004, profileId:"0104", ep:ep, commandList:(descMap.data.tail() ) ) 
        	state.deviceData = getDataRecordByNetworkId()
            break
    }
}

void processClusterResponse0x0104_0004(Map descMap) {
	// Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0104")) return 
    if (descMap.clusterInt  != 0x0004)  return 
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0004(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0004(descMap) // Global Commands
    }
}

void getGroupMembership(Map inputs = [ep: null ] ) {
    assert inputs.ep instanceof Integer 
	List cmds = []
	cmds += zigbee.command(0x0004,0x02,[destEndpoint:(inputs.ep)],0,"00") // Get Group Membership Request. "00" payload means to get all groups!
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void configure0x0104_0004(Integer ep = getEndpointId(device)) {
    getGroupMembership(ep:ep)
    List cmds = []
    // Cluster 0004, Discover Commands Received (command 11) starting at 00 and collecting as many as sixteen (0x10) commands. Profile must be specified.
    cmds += "he raw   0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0004 {00 00 11 00 10}{0x0104}" 

    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))

}
void unbind0x0104_0004() {
	List cmds = []
	cmds += "zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0004 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0004: ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0004() {
	configure0x0104_0004()
    	    state.deviceData = getDataRecordByNetworkId()
}
void refresh0x0104_0004(Integer ep = getEndpointId(device)) {
}


