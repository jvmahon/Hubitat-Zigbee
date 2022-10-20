// Routines to handle color clusters. 

library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Color Cluster 0x0300 Tools",
        name: "ColorCluster0x0300",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

void componentSetColor(com.hubitat.app.DeviceWrapper cd, Map colormap) { setColor(colormap, getEndpointId(cd)) }

void setColor(Map colormap, String ep = getEndpointId(device) ){
	assert ! colormap.is(null)

    Integer targetHue = Math.max(Math.min(colormap.hue as Integer, 100), 0)
 	Integer targetSat = Math.max(Math.min(colormap.saturation as Integer, 100), 0)
    
    String hexHue = zigbee.convertToHexString(Math.round(targetHue * 2.54) as Integer,2)
 	String hexSat = zigbee.convertToHexString(Math.round(targetSat * 2.54) as Integer,2)
    
    setLevel(level:(colormap.level), duration: 0, ep:ep) 
	
	sendZCLAdvanced(
		destinationEndpoint: ep ,
		clusterId: 0x0300, 
		isClusterSpecific: true ,
		commandId: 0x06, /// Move to Hue and Saturation, ZCL 5.2.2.3.10
		commandPayload: [hexHue, hexSat, "0000", "00", "00"] // ZCL Fig. 5-8
		)
		
    setLastSentCommand(clusterId:"0300", commandId:0x06, commandData:[hue:targetHue, hexHue: hexHue, saturation:targetSat, hexSat: hexSat, level:(colormap.level as Integer), command:0x06], ep:ep)
}

void componentSetHue(com.hubitat.app.DeviceWrapper cd, hue) { setHue(hue:hue, ep:getEndpointId(cd)) }
void setHue(hue){ setHue(hue:hue as Integer) }
void setHue(Map params = [:]){
	Map inputs = [hue: null , ep:getEndpointId(device) ] << params
	assert inputs.hue instanceof Integer
	
	Integer targetHue = Math.max(Math.min(inputs.hue as Integer, 100), 0)
	String hexHue = zigbee.convertToHexString(Math.round(targetHue * 2.54) as Integer,2)
    
	sendZCLAdvanced(
		destinationEndpoint: inputs.ep ,
		clusterId: 0x0300, 
		isClusterSpecific: true ,
		commandId: 0x00, /// Move to Hue, ZCL 5.2.2.3.4
		commandPayload: [hexHue, "00", "0000", "00", "00"] // ZCL Fig. 5-2
		)
			
    setLastSentCommand(clusterId:"0300", commandId:0x00, commandData:[hue:targetHue, hexHue: hexHue, command:0x00], ep:inputs.ep)
}

void componentSetSaturation(com.hubitat.app.DeviceWrapper cd, saturation) {
    setSaturation(saturation:saturation, ep:getEndpointId(cd))
}
void setSaturation(saturation){ setSaturation (saturation:saturation as Integer) }
void setSaturation(Map params = [:]){
	Map inputs = [saturation: null , ep:getEndpointId(device)] << params

    try{
		assert inputs.saturation instanceof Integer
    } catch (AssertionError e) {
        log.debug "<pre>${e}"
        throw e
    }
	
	Integer targetSat = Math.max(Math.min(inputs.saturation as Integer, 100), 0)
	String hexSat = zigbee.convertToHexString(Math.round(targetSat * 2.54) as Integer,2)
    
	sendZCLAdvanced(
		destinationEndpoint: inputs.ep ,
		clusterId: 0x0300, 
		isClusterSpecific: true ,
		commandId: 0x03, /// Move to Saturation, ZCL 5.2.2.3.7
		commandPayload: [hexSat, "0000", "00", "00"] // ZCL Fig. 5-5
		)
			
    setLastSentCommand(clusterId:"0300", commandId:0x03, commandData:[saturation:targetSat, hexSat: hexSat, command:0x03], ep:inputs.ep)
}

void processAttributes0x0300(Map descMap){
    String ep = descMap.sourceEndpoint ?: descMap.endpoint

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
						Integer miredValue = 1000000 / zigbee.convertHexToInt(it.value)
						hubEvents << [name:"colorTemperature", value: miredValue, descriptionText: "${device.displayName} was set to new color temperature: ${miredValue} Kelvin", unit: "°K", isStateChange:false]
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
    sendEventsToEndpointByParse(events:hubEvents, ep:ep )
}

void processSpecificResponse0x0104_0300(Map descMap) {
    log.error "Please report this error on Github: For cluster 0x0300, received a cluster specific response message but no specific response handling is implemented. Message was ${descMap.inspect()}"
}

void processGlobalResponse0x0104_0300(Map descMap) {
        assert ! (descMap.sourceEndpoint.is( null ) && descMap.endpoint.is (null)  )
        String ep = descMap.sourceEndpoint ?: descMap.endpoint
    
    	switch(descMap.command) {
		case "01": // Read Attributes Response
		case "0A": // Report Attributes Response
            processAttributes0x0300(descMap)
			break
			
		case "0B": // Default Response
            List<Map> hubEvents = []
			Map whatWasSent = removeLastSentCommand(clusterId:"0300", commandId:descMap.data[0], ep:ep )
           
            if (logEnable) log.debug "Last Sent Color Command for command 0x${ (descMap.data[0])} endpoint ${ep} was: ${whatWasSent}"
      
            if (whatWasSent?.hue != null) {
                hubEvents << [name:"hue", value: (whatWasSent.hue), units:"%", descriptionText: "${device.displayName} was set to hue ${(whatWasSent.hue)}%", isStateChange:false]
            }

            if (whatWasSent?.saturation != null)	 {
                hubEvents << [name:"saturation", value: (whatWasSent.saturation), units:"%", descriptionText: "${device.displayName} was set to Saturation ${(whatWasSent.saturation)}%", isStateChange:false]
            }
            sendEventsToEndpointByParse(events:hubEvents, ep:ep )
			break
		default:
			break
	}
}

void processClusterResponse0x0104_0300(Map descMap){
    if (descMap.clusterInt  != 0x0300)  return 
    
    if( descMap.isClusterSpecific == true ){ 
        processSpecificResponse0x0104_0300(descMap) // Cluster Specific Commands
    } else {
        processGlobalResponse0x0104_0300(descMap) // Global Commands
    }
}

void configure0x0104_0300(String ep = getEndpointId(device) ) {
	// if (logEnable) log.debug "Configuring 0x0300 attribute reporting: ${cmds}"
	String cmd = "zdo bind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0300 {${device.zigbeeId}} {}" 
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))
}
void unbind0x0104_0300(String ep = getEndpointId(device) ) {
	String cmd = "zdo unbind 0x${device.deviceNetworkId} 0x${ep} 0x01 0x0300 {${device.zigbeeId}} {}" 
	if (logEnable) log.debug "Unbinding 0x0300: ${cmd}"
	sendHubCommand(new hubitat.device.HubAction( cmd, hubitat.device.Protocol.ZIGBEE))     
}
void initialize0x0104_0300() {
	// configure0x0104_0300()
	refresh0x0104_0300()
}
void refresh0x0104_0300(String ep = getEndpointId(device) ) {
 
       sendZCLAdvanced(
            clusterId: 0x0300 , 
            destinationEndpoint: ep, 
            commandId: 0x00,             // 00 = Read attributes, ZCL Table 2-3.
			commandPayload: ["400A"] // List of attributes of interest [0x400A].
        )   
      sendZCLAdvanced(
            clusterId: 0x0300 ,
            destinationEndpoint: ep, 
            commandId: 0x00,             // 00 = Read attributes, ZCL Table 2-3.
			commandPayload: ["0000", "0001", "0007", "0008"] // List of attributes of interest.
        )
    
}
