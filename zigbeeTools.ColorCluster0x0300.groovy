library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Color Cluster 0x0300 Tools",
        name: "ColorCluster0x0300",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/HubitatDriverTools",
		version: "0.0.1"
)

void componentSetColor(com.hubitat.app.DeviceWrapper cd, Map colormap) {
    setColor(colormap, getchildEndpointId(cd))
}

void setColor(Map colormap, Integer ep = 1){
    Integer targetHue = Math.max(Math.min(colormap.hue as Integer, 100), 0)
 	Integer targetSat = Math.max(Math.min(colormap.saturation as Integer, 100), 0)
    
    String hexHue = zigbee.convertToHexString(Math.round(targetHue * 254 / 100) as Integer,2)
 	String hexSat = zigbee.convertToHexString(Math.round(targetSat * 254 / 100) as Integer,2)
    
    setLevel(level:(colormap.level), duration: 0, ep:ep) 
    List cmds = []

	cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0300 0x06 {${hexHue}${hexSat} 0000}}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
    setLastSentCommand(0x0300, 0x06, [hue:targetHue, hexHue: hexHue, saturation:targetSat, hexSat: hexSat, level:(colormap.level as Integer), command:0x06], "0104", ep)
    
}

void componentSetHue(com.hubitat.app.DeviceWrapper cd, hue) {
    setHue(hue, getchildEndpointId(cd))
}
void setHue(hue, Integer ep = 1){
	Integer targetHue = Math.max(Math.min(hue as Integer, 100), 0)
	String hexHue = zigbee.convertToHexString(Math.round(targetHue * 254 / 100) as Integer,2)
    
    List cmds = []
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0300 0x00 {${hexHue}000000 }}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
    setLastSentCommand(0x0300, 0x00, [hue:targetHue, hexHue: hexHue, command:0x00], "0104", ep)
}

void componentSetSaturation(com.hubitat.app.DeviceWrapper cd, saturation) {
    setSaturation(saturation, getchildEndpointId(cd))
}
void setSaturation(saturation, Integer ep = 1){
	Integer targetSat = Math.max(Math.min(saturation as Integer, 100), 0)
	String hexSat = zigbee.convertToHexString(Math.round(targetSat * 254 / 100) as Integer,2)
    
    List cmds = []   
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${ep} 0x0300 0x03 {${hexSat}000000}}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
    setLastSentCommand(0x0300, 0x03, [saturation:targetSat, hexSat: hexSat, command:0x03], "0104", ep)
}

void processAttributes0x0300(Map descMap){
    Integer ep = Integer.parseInt((descMap.endpoint ?: descMap.sourceEndpoint), 16)
    List<Map> hubEvents = []
    
        if (logEnable) log.debug "processing a 0x0300 cluster response type ${descMap.command}"
		    List<Map> allAttributes = []
			if (descMap.attrId != null ) {
				allAttributes += [value: (descMap.value), encoding:(descMap.encoding), attrId:(descMap.attrId), attrInt:(descMap.attrInt)]
			}

			if (descMap.additionalAttrs) allAttributes += descMap.additionalAttrs

			allAttributes.each{
				Map event
				switch(it.attrId) {
					case "0000": //current Hue
                        Integer newValue = Math.round(zigbee.convertHexToInt(it.value) / 2.54)
						hubEvents << [name:"hue", value: newValue, units:"%", descriptionText: "${device.displayName} was set to hue ${newValue}%", isStateChange:false]
						break
					case "0001": // Current Saturation
                        Integer newValue = Math.round(zigbee.convertHexToInt(it.value) / 2.54)
						hubEvents << [name:"saturation", value: newValue, units:"%", descriptionText: "${device.displayName} was set to Saturation ${newValue}%", isStateChange:false]
						break
					case "0007": // Color Temperature in Mireds
						break
					case "0008": // Color Mode
						switch (zigbee.convertHexToInt(it.value)) {
							case 0x00: // Hue and  Saturation Mode
								hubEvents << [name:"colorMode", value: "RGB", descriptionText: "${device.displayName} was set to color mode RGB", isStateChange:false]
								break

							case 0x02: // Color Temperature in Mireds
								hubEvents << [name:"colorMode", value: "CT", descriptionText: "${device.displayName} was set to color temperature mode", isStateChange:false]
								break
						}
						break
                    case "400A":
                        Integer capabilitiesInt = zigbee.convertHexToInt(it.value)
                           log.debug "Color Capabilities as a Hex number: ${it.value} and as an Integer ${capabilitiesInt}"
                
                        Map capabilities = [:]
                            if (capabilitiesInt & 0b00000001) capabilities << [0:true]
                            if (capabilitiesInt & 0b00000010) capabilities << [1:true]
                            if (capabilitiesInt & 0b00000100) capabilities << [2:true]
                            if (capabilitiesInt & 0b00001000) capabilities << [3:true]
                            if (capabilitiesInt & 0b00010000) capabilities << [4:true]
                    	hubEvents << [name:"colorCapabilities", value: capabilities, descriptionText: "${device.displayName} color Capabilities", isStateChange:false]
                        break
                }
            }
    sendEventsToEndpointByParse(hubEvents, ep)
}

void processClusterResponse0x0104_0300(Map descMap){
    if (descMap.clusterInt  != 0x0300)  return 
    assert ! (descMap.endpoint.is(null) && descMap.destinationEndpoint.is( null ))
	
	// Is this the right handler for this message? Should processing be rejected?
	// Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0104")) return 

	// End of rejection reasons!	
	
	if (logEnable) log.debug "processing a 0x0300 cluster response message ${descMap}"
	
	switch(descMap.command) {
		case "01": // Read Attributes Response
		case "0A": // Report Attributes Response
            processAttributes0x0300(descMap)
			break
			
		case "0B": // Default Response
            Integer ep = Integer.parseInt((descMap.endpoint ?: descMap.sourceEndpoint), 16)
            List<Map> hubEvents = []
			Map whatWasSent = removeLastSentCommand(0x0300, (descMap.data[0] as Integer), (descMap.profileId), ep )
            if (logEnable) log.debug "Last Sent Color Command for command ${ (descMap.data[0] as Integer)} endpoint ${ep} was: ${whatWasSent}"
      
            if (whatWasSent?.hue != null) {
                hubEvents << [name:"hue", value: (whatWasSent.hue), units:"%", descriptionText: "${device.displayName} was set to hue ${(whatWasSent.hue)}%", isStateChange:false]
            }

            if (whatWasSent?.saturation != null)	 {
                hubEvents << [name:"saturation", value: (whatWasSent.saturation), units:"%", descriptionText: "${device.displayName} was set to Saturation ${(whatWasSent.saturation)}%", isStateChange:false]
            }
            sendEventsToEndpointByParse(hubEvents, ep)
			break
        case "12": // Discover Commands Received Response
           log.warn "Processing a Command Supported Response frame. Commands supported are ${descMap.data.tail()}. Processing code is not complete. ${descMap}"
            brea        
		default:
			break
	}

}



void configure0x0104_0300() {
	List cmds = []
	cmds += "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0300 {${device.zigbeeId}} {}" 
    cmds += "he raw   0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0300 {00 00 11 00 10}{0x0104}" // Cluster 0300, Discover Commands Received (command 11) starting at 00 and collecting as many as sixteen (0x10) commands.

	if (logEnable) log.debug "Configuring 0x0300 attribute reporting: ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
}
void initialize0x0104_0300() {
	configure0x0104_0300()
	refresh0x0104_0300()
}
void refresh0x0104_0300(Integer ep = 1) {
    List cmds = []
    cmds += zigbee.readAttribute(0x0300, [0x400a], [destEndpoint:ep], 0) // Want the Color Capabilities response before anything else, so read that first!
	cmds += zigbee.readAttribute(0x0300, [0x0000, 0x0001, 0x0007, 0x0008], [destEndpoint:ep], 0)
	if (logEnable) log.debug "Getting Color Cluster Attributes 0x0300"
	sendHubCommand(new hubitat.device.HubMultiAction( cmds, hubitat.device.Protocol.ZIGBEE)) 
}


