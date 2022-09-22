library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "group Cluster 0x0004 Tools",
        name: "groupCluster0x0004",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/HubitatDriverTools",
		version: "0.0.1"
)

List<String> getGroupData(Integer ep = 1){
	List<String> groups = getClusterDataRecord(clusterInt:0x0004, profileId:"0104", ep:ep).get("groups", [])
    if (logEnable) log.debug "Returning groups record with contents ${groups}"
    return groups
}

void processClusterResponse0x0104_0004(Map descMap) {
	// Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0104")) return 
    if (descMap.clusterInt  != 0x0004)  return 
	// End of rejection reasons!
    
	if (logEnable) log.debug "Group Cluster 0x0004 library processing message: ${descMap}"

    String status = descMap.data[0]
    String group

    switch (descMap.command){
        case "00" : //add group response
            if (status in ["00","8A"]) {
                group = descMap.data[1] + descMap.data[2]
                if (group in groupData) {
                    if (txtEnable) log.info "group membership refreshed"
                } else {
                    groupData.add(group)
                    if (txtEnable) log.info "group membership added"
                }
            } else {
                log.warn "${device.displayName}'s group table is full, unable to add group..."
            }
            break
		case "01": // View Group
			break
        case "02" : //group membership response
            Integer groupCount = hexStrToUnsignedInt(descMap.data[1])
            if (groupCount == 0 && groupData != []) {
                List<String> cmds = []
				groupData.each {
                    cmds.addAll(zigbee.command(0x0004,0x00,[:],0,"${it} 00"))
                    if (txtEnable) log.warn "update group:${it} on device"
                }
				groupData = []
                sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
            } else {
                //get groups and update groupData...
                Integer crntByte = 0
                for (int i = 0; i < groupCount; i++) {
                    crntByte = (i * 2) + 2
                    group = descMap.data[crntByte] + descMap.data[crntByte + 1]

					if ( !(group in groupData) ) {
						groupData.add(group)
                        if (txtEnable) log.info "group added to global data storage list"
                    } else {
                        if (txtEnable) log.debug "group already exists in global data storage list..."
                    }
                }
            }
            break
        case "03" : //remove group response
            group = descMap.data[1] + descMap.data[2]
			groupData.remove(group)
            if (txtEnable) log.info "group membership removed"
            break

		case "04": // Remove all groups
			groupData = []
            if (txtEnable) log.info "all group membership removed"
			break
		case "05": // Add Group if Identifying
		    if (txtEnable) log.warn "skipped Add Group If Identifying command:${descMap}"

			break
        default :
            log.error "group command not handled:${descMap}"
    }
	state.groups = groupData
}

void configure0x0104_0004() {
	List cmds = []
	cmds += zigbee.command(0x0004,0x02,[:],0,"00")
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}
void initialize0x0104_0004() {
	configure0x0104_0004()
}
void refresh0x0104_0004(Integer ep = 1) {
}

