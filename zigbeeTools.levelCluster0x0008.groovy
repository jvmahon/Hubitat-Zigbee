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
    setLevel(level:inputLevel, duration:duration)
}

void setLevel(Map params = [:]) {

	Map inputs = [ep: device.endpointId , level: 50 , duration: 0 ] << params

    Integer hubitatLevel = Math.max(Math.min(inputs.level as Integer, 100), 0)
 	Integer zigbeeDeviceLevel = hubitatLevel * 2.54

    if (device.hasCapability("Switch")) {
   	    if (hubitatLevel == 0) {
		    if (device.currentValue("switch") == "on") off(ep:inputs.ep)
		    return
        } else if (device.currentValue("switch") == "off"){
            on(ep:inputs.ep)
        }
	}
    
    String hexLevel = intToHexStr( zigbeeDeviceLevel )
    String hexRate  = intToHexStr( (int) inputs.duration * 10 ).padLeft(4,"0")
	
	sendZCLAdvanced(
		destinationNetworkId: device.deviceNetworkId,
		destinationEndpoint: inputs.ep,
		clusterId: 0x0008, 
		isClusterSpecific: true,
		commandId: 0x04,
		commandPayload: byteReverseParameters([hexLevel, hexRate])
		)
		
    Map thisCommandData = [name:"level", level:hubitatLevel, duration:inputs.duration, hexLevel:hexLevel, hexRate:hexRate, ep:(inputs.ep)]
	setLastSentCommand(clusterId:"0008", commandNum:0x04, commandData:thisCommandData, ep:(inputs.ep))
}

void processAttributes0x0008(Map descMap) {
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
    
        List<Map> hubEvents = []
   	    Integer newLevel

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
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
}

void processGlobalResponse0x0104_0008(Map descMap) {
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
    
	switch(descMap.command) {
		case "01": // Read Attributes Response
		case "0A": // Report Attributes Response
            processAttributes0x0008(descMap)
			break
		case "0B": // Default Response
            List<Map> hubEvents = []
            Integer lastCommand = (descMap.data[0] as Integer)
			Map whatWasSent = removeLastSentCommand(clusterId:"0008", commandNum:lastCommand, ep:ep)
            if (logEnable) log.debug "Retrieved a copy of what was sent. Got: ${whatWasSent.inspect()}"        
            if (whatWasSent) {
			    hubEvents << [name:"level", value: (whatWasSent.level), units:"%", descriptionText: "${device.displayName} was set to level ${whatWasSent.level}%", isStateChange:false]
                sendEventsToEndpointByParse(events:hubEvents, ep:ep)
            }
			break

		default:
			break
	}
}

void processClusterResponse0x0104_0008(Map descMap){
    if (descMap.clusterInt  != 0x0008)  return 
 
    if(descMap.isClusterSpecific == true){ 
        processSpecificResponse0x0104_0008(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0008(descMap) // Global Commands
    }
}	

void configure0x0104_0008(String ep = device.endpointId) {
    // if (logEnable) log.debug "Configuring 0x0008 attribute reporting: ${cmds}"	
	String cmd = "zdo bind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0008 {${device.zigbeeId}} {}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE)) 
}
void unbind0x0104_0008(String ep = device.endpointId) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0008 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0008: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0008(String ep = device.endpointId) {
	configure0x0104_0008(ep)
	refresh0x0104_0008(ep)
}

void refresh0x0104_0008(String ep = device.endpointId){

       sendZCLAdvanced(
            clusterId: 0x0008 ,             // specified as an Integer
            destinationEndpoint: ep, 
            commandId: 0x00,             // 00 = Read attributes, ZCL Table 2-3.
            commandPayload: byteReverseParameters(["0000", "0001", "0010", "0012", "0013"]) // List of attributes of interest [0x0000, 0x0001, 0x0010, 0x0012, 0x0013] in reversed octet form
        )
}