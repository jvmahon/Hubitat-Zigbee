library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "On Off Cluster 0x0006 Tools",
        name: "OnOffCluster0x0006",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

void componentOn(com.hubitat.app.DeviceWrapper cd){ on( ep:getEndpointId(cd) ) }
void on(Map params = [:]){
    Map inputs = [ep: getEndpointId(device) ] << params

	assert (inputs.ep instanceof String || inputs.ep instanceof GString) && (inputs.ep.length() == 2) : "Endpoint must be specified as a 2 Hex Character String"
    
    if (useOnOffTimer  && supportsOffTimer()  ) {
        inputs.timer = settings.get("offTime") as Integer
        timedOn(*:inputs)
        return
    }

    sendZCLAdvanced(
        clusterId: 0x0006 , 
        destinationEndpoint: inputs.ep,
        isClusterSpecific: true , 
        disableDefaultResponse: false , 
        commandId: 0x01
    )
}

void componentOff(com.hubitat.app.DeviceWrapper cd){ off(ep:getEndpointId(cd)) }
void off(Map params = [:]){
    Map inputs = [ep: getEndpointId(device)] << params

	assert (inputs.ep instanceof String || inputs.ep instanceof GString) && (inputs.ep.length() == 2) : "Endpoint must be specified as a 2 Hex Character String"
	
    sendZCLAdvanced(
        clusterId: 0x0006 , 
        destinationEndpoint: inputs.ep, 
        isClusterSpecific: true ,  
        disableDefaultResponse: false , 
        commandId: 0x00 
    )
}

void timedOn(timeInSeconds){ timedOn(timer:timeInSeconds as Integer) }
void timedOn(Map params = [:]) {
    Map inputs = [ep: getEndpointId(device) , timer: null ] << params
        
    String timeString = zigbee.convertToHexString((int) Math.round(inputs.timer * 10), 4)
    
    if (logEnable) log.debug "timedOn parameters: ep: ${ep}, timer: ${timer}, timerHex: ${timerHex}, timeString: ${timeString}"
    
    sendZCLAdvanced(
        clusterId: 0x0006 , 
        destinationEndpoint: inputs.ep,
        isClusterSpecific: true , 
        disableDefaultResponse: false , 
        commandId: 0x42, // Turn On with Timed off. ZCL 3.8.2.3, Table 3-47
        commandPayload: ["00", timeString , "0000"] // ZCL 3.8.2.3.6 for format
    )
}

void processAttributes0x0006(Map descMap){
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
    
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
				break
			case "4003": // StartUpOnOff
				String startupState = [    "00":"Off", 
                                           "01":"On", 
                                           "02":"Off", 
                                           "FF":"Prior State"].get(it.value)
                updateDataValue("stateOnPowerup", startupState)
				break
			default:
				break
			}
	}

    sendEventsToEndpointByParse(events:hubEvents, ep:ep)
}

void processSpecificResponse0x0104_0006(Map descMap) {
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processGlobalResponse0x0104_0006(Map descMap) {
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
    
	String newSwitchState
    switch (descMap.command) { // From ZCL Table 2-3
        case "01":  // Read Attributes Response, ZCL 2.5.2.  This is in response to a read request.
        case "0A": // Report Attributes, ZCL 2.5.11. This can be autonymously generated.
            processAttributes0x0006(descMap)
            break
        case "0B": // Default Response, ZCL 2.5.12
			switch (descMap.data[0]) { // See ZCL3.8.2.3 for the specific coding
				case "00": // Turned Off
					List<Map> hubEvents = []
					hubEvents << [name:"switch", value: "off", descriptionText: "${device.displayName} was turned off", isStateChange:false]
					sendEventsToEndpointByParse(events:hubEvents, ep:ep)
					break
				case "01": // Turned On
					List<Map> hubEvents = []
					hubEvents << [name:"switch", value: "on", descriptionText: "${device.displayName} was turned on", isStateChange:false]
					sendEventsToEndpointByParse(events:hubEvents, ep:ep)
					break
				case "42": // On with Timed Off
					List<Map> hubEvents = []
					hubEvents << [name:"switch", value: "on", descriptionText: "${device.displayName} was turned on with an automatic off timer", isStateChange:false]
					sendEventsToEndpointByParse(events:hubEvents, ep:ep)
					break
				default:
					if (logEnable) log.debug "In cluster 0x0006, received a default response code ${descMap.data[0]} in response to a ZCL command. This response is not handled by the Cluster 0x0006 code. See ZCL 2.5.12 for command meaning. Received report was ${descMap.inspect()}"
			}
            break
        case "12":
            if (descMap.commandsReceived.contains("42")) { 
                   updateDataValue("supportsOnOffTimer", "true") 
               } else {
                   updateDataValue("supportsOnOffTimer", "false")
               }

        default:
            if (logEnable) log.debug "Cluster 0x0006 received a command which was not processed: ${descMap.inspect()}"
    }
}

void processClusterResponse0x0104_0006(Map descMap){
    if (descMap.clusterInt  != 0x0006)  return 
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0006(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0006(descMap) // Global Commands
    }
}	

void configure0x0104_0006(String ep = getEndpointId(device) ) {
	String cmd = "zdo bind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0006 {${device.zigbeeId}} {}" 
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE)) 
}
void unbind0x0104_0006(String ep = getEndpointId(device) ) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0006 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0006: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0006(String ep = getEndpointId(device) ) {
	// configure0x0104_0006(ep)
	refresh0x0104_0006(ep)
}
void refresh0x0104_0006(String ep = getEndpointId(device) ) {
    
        sendZCLAdvanced(
            clusterId: 0x0006 , 
            destinationEndpoint: ep, 
            commandId: 0x00,             // Global read attributes command.
            commandPayload: ["0000", "4000", "4001", "4002", "4003"] // List of attributes of interest.
        )
}

