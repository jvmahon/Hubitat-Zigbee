library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "level Control Cluster 0x0008 Tools",
        name: "levelCluster0x0008",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

void componentSetLevel(com.hubitat.app.DeviceWrapper cd, level, transitionTime = null) {
	if (cd.hasCapability("FanControl") ) {
			setSpeed(level:level, speed:levelToSpeed(level as Integer), ep:getEndpointId(cd))
		} else { 
			setLevel(level:level, duration:transitionTime, ep:getEndpointId(cd)) 
		}
}

    // Command format is here: 	https://stdavedemo.readthedocs.io/en/latest/device-type-developers-guide/building-zigbee-device-handlers.html
    // Device NEtwork ID / Endpoint ID / Cluster / Command / Payload

void setLevel(inputLevel, duration = 0 ) {
    setLevel(level:inputLevel, duration:duration, ep:1)
}

void setLevel(Map inputs = [ep: 1 , level: 50 , duration: 0 ]) {
    Integer hubitatLevel = Math.max(Math.min(inputs.level as Integer, 100), 0)
 	Integer zigbeeDeviceLevel = hubitatLevel * 2.54

    if (device.hasCapability("Switch")) {
   	    if (hubitatLevel == 0) {
		    if (device.currentValue("switch") == "on") off()
		    return
        } else if (device.currentValue("switch") == "off"){
            on()
        }
	}
    
    String hexLevel = intToHexStr(zigbeeDeviceLevel)
    String hexRate = zigbee.swapOctets( ((inputs.duration.is( null )) ? "0000" : intToHexStr(inputs.duration * 10 as Integer)).padLeft(4,"0"))
	
	List<String> cmds = []
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${inputs.ep} 0x0008 0x04 {${hexLevel} ${hexRate}}"
	
    Map thisCommandData = [name:"level", level:hubitatLevel, duration:inputs.duration, hexLevel:hexLevel, hexRate:hexRate, ep:(inputs.ep)]
	setLastSentCommand(clusterInt:0x0008, commandNum:0x04, commandData:thisCommandData, profileId:"0104", ep:(inputs.ep as Integer))
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE)) 
}

void processAttributes0x0008(Map descMap) {
        List<Map> hubEvents = []
   	    Integer newLevel
        Integer ep = Integer.parseInt((descMap.endpoint ?: descMap.sourceEndpoint), 16)

        if (logEnable) log.debug "processing a 0x0008 cluster response type ${descMap.command}"
		    List<Map> allAttributes = []
			if (descMap.attrId != null ) {
				allAttributes += [value:(descMap.value), encoding:(descMap.encoding), attrId:(descMap.attrId), attrInt:(descMap.attrInt)]
			}

			if (descMap.additionalAttrs) allAttributes += descMap.additionalAttrs

			allAttributes.each{
				switch(it.attrId) {
					case "0000": //currentLevel
						newLevel = Math.round( zigbee.convertHexToInt(it.value) / 2.54 )
						hubEvents << [name:"level", value: newLevel, units:"%", descriptionText: "${device.displayName} was set to level ${newLevel}%", isStateChange:false]
						break
					case "0001": // Remaining Time
					case "0002": // MinLevel
					case "0003": // MaxLevel
					case "0004": // CurrentFrequency
					case "0005": // MinFrequency
					case "0006": // MaxFrequency
					case "0010": // OnOffTransitionTime
						hubEvents << [name:"OnOffTransitionTime", value: (zigbee.convertHexToInt(it.value) / 10 ), units:"seconds", descriptionText: "${device.displayName} On-Off TransitionTime is ${(zigbee.convertHexToInt(it.value) / 10 )} seconds", isStateChange:false]                    
                        break
					case "0011": // OnLevel
					case "0012": // OnTransitionTime
						hubEvents << [name:"OnTransitionTime", value: (zigbee.convertHexToInt(it.value) / 10 ), units:"seconds", descriptionText: "${device.displayName} On TransitionTime is ${(zigbee.convertHexToInt(it.value) / 10 )} seconds", isStateChange:false]                    
                        break                    
					case "0013": // OffTransitionTime
						hubEvents << [name:"OffTransitionTime", value: (zigbee.convertHexToInt(it.value) / 10 ), units:"seconds", descriptionText: "${device.displayName} Off TransitionTime is ${(zigbee.convertHexToInt(it.value) / 10 )} seconds", isStateChange:false]                    
                        break                    
					case "0014": // DefaultMoveRate
					case "000F": // Options
					case "4000": // StartUpCurrentLevel
					default:
						break
				}
			}    
        sendEventsToEndpointByParse(events:hubEvents, ep:ep)
}    

void processSpecificResponse0x0104_0008(Map descMap) {
    log.warn "For cluster 0x0008, received a specific response message but no specific response handling is implemented"
}

void processGlobalResponse0x0104_0008(Map descMap) {
	switch(descMap.command) {
		case "01": // Read Attributes Response
		case "0A": // Report Attributes Response
            processAttributes0x0008(descMap)
			break
		case "0B": // Default Response
            Integer ep = Integer.parseInt((descMap.endpoint ?: descMap.sourceEndpoint), 16)
            List<Map> hubEvents = []
            Integer lastCommand = (descMap.data[0] as Integer)
			Map whatWasSent = removeLastSentCommand(clusterInt:0x0008, commandNum:lastCommand, profileId:"0104", ep:ep)
            if (logEnable) log.debug "Retrieved a copy of what was sent. Got: ${whatWasSent}"        
            if (whatWasSent) {
			    hubEvents << [name:"level", value: (whatWasSent.level), units:"%", descriptionText: "${device.displayName} was set to level ${whatWasSent.level}%", isStateChange:false]
                sendEventsToEndpointByParse(events:hubEvents, ep:ep)
            }
			break
        case "12": // Discover Commands Received Response
            Integer ep = Integer.parseInt(descMap.sourceEndpoint, 16)
            setClusterCommandsSupported(clusterInt:0x0008, profileId:"0104", ep:ep, commandList:(descMap.data.tail() ) ) 
        	state.deviceData = getDataRecordByNetworkId()
            break     
		default:
			break
	}
}

void processClusterResponse0x0104_0008(Map descMap){
    if (descMap.clusterInt  != 0x0008)  return 
    assert ! (descMap.endpoint.is(null) && descMap.destinationEndpoint.is( null ))
	
	// Is this the right handler for this message? Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0104")) return 
    
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0008(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0008(descMap) // Global Commands
    }
}	

void configure0x0104_0008() {
	List cmds = []
	cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}" 
    
     // Cluster 0008, Discover Commands Received (command 11) starting at 00 and collecting as many as sixteen (0x10) commands. Profile must be specified!
    cmds += "he raw   0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {00 00 11 00 10}{0x0104}"

    if (logEnable) log.debug "Configuring 0x0008 attribute reporting: ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
}
void unbind0x0104_0008() {
	List cmds = []
	cmds += "zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0008: ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0008() {
	configure0x0104_0008()
	refresh0x0104_0008()
}

void refresh0x0104_0008(Integer ep = getEndpointId(device)){
    List cmds = []
	cmds += zigbee.readAttribute(0x0008, [0x0000, 0x0001, 0x0010, 0x0012, 0x0013], [destEndpoint:ep], 0)
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
}