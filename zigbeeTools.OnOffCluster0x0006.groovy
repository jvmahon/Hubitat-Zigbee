library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "On Off Cluster 0x0006 Tools",
        name: "OnOffCluster0x0006",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/HubitatDriverTools",
		version: "0.0.1"
)

void componentOn(com.hubitat.app.DeviceWrapper cd){ on(cd:cd) }
void on(Map inputs = [:]){
	Map params = [cd: null , duration: null , level: null ] << inputs
	
    List<String> cmds = []
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}"
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void componentOff(com.hubitat.app.DeviceWrapper cd){ off(cd:cd) }
void off(Map inputs = [:]){
	Map params = [cd: null , duration: null , level: null ] << inputs
    List<String> cmds = []
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE)) 
}

void processAttributes0x0006(Map descMap){
    List<Map> hubEvents = []

    List<Map> allAttributes = []
    if (descMap.attrId != null ) {
		allAttributes += [value: (descMap.value), encoding:(descMap.encoding), attrId:(descMap.attrId), attrInt:(descMap.attrInt)]
	}

	if (descMap.additionalAttrs) allAttributes += descMap.additionalAttrs

	allAttributes.each{
		switch(it.attrId) {
			case "0000": //onOff
				newSwitchState = ((it.value as Integer) ? "on" : "off")
				hubEvents << [name:"switch", value: newSwitchState, descriptionText: "${device.displayName} is ${newSwitchState}", isStateChange:false]
				break
			case "4000": //GlobalSceneControl
			case "4001": // onTime
			case "4002": // OffWaitTime
			case "4003": // StartUpOnOff
			default:
				break
			}
	}
    Integer ep = Integer.parseInt(descMap.endpoint, 16) // I'm assuming it is in hex!
    sendEventsToEndpointByParse(hubEvents, ep)
}

void processClusterResponse0x0104_0006(Map descMap){
    if (descMap.clusterInt  != 0x0006)  return 
    assert ! (descMap.endpoint.is(null) && descMap.destinationEndpoint.is( null ))
    
	String newSwitchState
	
	// Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0104")) return 

	// End of rejection reasons!
    switch (descMap.command) {
        case "01":  // Read Attributes Response
            processAttributes0x0006(descMap)
            break
        case "OB": // Default Response
        	List<Map> hubEvents = []
		    newSwitchState = (descMap.data[0] == "01") ? "on" : "off"
            Integer ep = Integer.parseInt(descMap.destinationEndpoint, 16)
		
		    hubEvents << [name:"switch", value: newSwitchState, descriptionText: "${device.displayName} was turned ${newSwitchState}", isStateChange:false]
		
            // log.debug "Endpoint is: ${ep}, hubEvents are: ${hubEvents}"
            sendEventsToEndpointByParse(hubEvents, ep)
            break
        // case "0D": // Discover Attributes Response
           //  break
        case "12": // Discover Commands Received Response
        log.warn "Processing a Command Supported Response frame. Commands supported are ${ descMap.data.tail() }. Processing code is not complete. ${descMap}"
            break
        // case "14": // Discover Commands Generated Response
           // break

        default:
            log.debug "Cluster 0x0006 received a command which was not processed: ${descMap}"
    }



}	

void configure0x0104_0006() {
    
/*
he raw 0x5580 1 0x01 0x0006 {00 00 11 00 10}{0x0104} // Cluster 0006, Discover Commands Received 11 starting at 00 and collecting as many as sixteen 10

he raw 0x5580 1 0x01 0x0006 {00 00 0C 0000 10}{0x0104} // Cluster 0006, Discover attributes 0C starting at 0000 and collecting as many as sixteen 10
*/
	List cmds = []
	cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}" 
    cmds += "he raw   0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {00 00 11 00 10}{0x0104}" // Cluster 0006, Discover Commands Received (command 11) starting at 00 and collecting as many as sixteen (0x10) commands.

	if (logEnable) log.debug "Configuring 0x0006 attribute reporting: ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
}
void initialize0x0104_0006() {
	configure0x0104_0006()
	refresh0x0104_0006()
}
void refresh0x0104_0006(Integer ep = 1) {
    List cmds = []
    cmds += zigbee.readAttribute(0x0006, [0x0000, 0x4000, 0x4001, 0x4002, 0x4003, 0x4004], [destEndpoint:ep], 0)
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
}

