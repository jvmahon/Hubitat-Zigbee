/*
Library to handle Zigbee Device Object (Profile 0000) messages.

This code is dependent on the received zigbee message having passed through the zigbeeTools.parsePlus library to 'complete' parsing of certain messages.

Other dependencies:
zigbeeTools.sendZigbeeAdvancedCommands library for sending Zigbee messages.
zigbeeTools.globalDataTools library to store received information
*/
library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Handle Bindings in ZDO Profile 0x0000",
        name: "zigbeeZDOProfile0x0000",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

// During execution of driver's "configure" method, the driver will call this function to ask the device for all of its endpoints.

void getActiveEndpointsZDO() {
    sendZDOAdvanced(
		clusterId: 0x0005,
		commandPayload: [device.deviceNetworkId]  // ZDO Spec Section 2.4.3.1.6, Fig. 2.25
		)   
}

// This code then takes the endpoint response, and requests the simple descriptors for each endpoint.
void processEndpointResponse0x0000(Map descMap) {
	setAllActiveEndpointsHexList(descMap.activeEndpointRsp.activeEPListHex) // Store the endpoints that were received
	
	// Now get the Simple Descriptor List for each Active Endpoint
	descMap.activeEndpointRsp.activeEPListHex.each{ ep ->
	    sendZDOAdvanced(
		    clusterId: 0x0004,
		    commandPayload: [device.deviceNetworkId, ep], // ZDO Spec Section 2.4.3.1.5, Fig. 2.24
		    ) 		
	}
}

// This code the receives a simple descriptor response, stores the cluster information, and requests command information as well as attribute information for each cluster.
void processSimpleDescriptorResponse(Map descMap){
	String ep = descMap.simpleDescriptorRsp.endpointId
	// Store the information in the global storage record
	getDataRecordForEndpoint(ep:ep ).put("profileId", 	descMap.simpleDescriptorRsp.profileId)
	getDataRecordForEndpoint(ep:ep ).put("inClusters", 	descMap.simpleDescriptorRsp.inClustersList)
	getDataRecordForEndpoint(ep:ep ).put("outClusters", 	descMap.simpleDescriptorRsp.outClustersList)
    
	// For all the inClusters in the message get the commands and attributes supported.
	// Nothing seems to be returned if this is done for outClusters, so those aren't processed.
    switch (descMap.simpleDescriptorRsp.profileId) {
        case "C05E":
        case "0104":
			descMap.simpleDescriptorRsp.inClustersList?.each{ it ->
				sendZCLAdvanced( // 0x11 is the Global Command to discovery commands Received by the device
						destinationEndpoint: ep , 
						clusterId: it , 
						commandId: 0x11,
						commandPayload: ["00", "40"],     // Start at 00 and collect up to 64 ("0x40") commands
					) 
				sendZCLAdvanced( // 0x0c is the Global Command to discovery attributes for a cluster
						destinationEndpoint: ep , 
						clusterId: it, 
						commandId: 0x0c,
						commandPayload: ["0000", "20"],     // Start at 00 and collect up to 16 ("10") attributes
					)    
			}
			// Only process for the inClusters. No data seems to be gathered for outClusters, so skip those!
        break
    }
}

// In addition, need to handle zigbee binding messages here!
void processClusterResponse0x0000_xxxx(Map descMap) {
	if (descMap?.profileId != "0000") return // the ZDO messages include the profileId.
    
	if (logEnable) log.debug "A zigbee binding message was received: ${descMap.inspect()}"
    
	switch (descMap.clusterId) {
         case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : //"device announce"
            if (logEnable) log.debug "Received a device announce binding message: ${descMap.inspect()}. Configuring device."
			String powerSource = (descMap.announceData?.macCapability && 0b00000100) ? "mains" : "dc"
			Map event = [name:"powerSource", value:powerSource]
			sendEvent(event)
            configure()        
			break
		case "8004" : //simple descriptor response  
            if (logEnable) log.debug "Received an Simple Descriptor Response message: ${descMap.inspect()}"
            processSimpleDescriptorResponse( descMap)
            break
        case "8005" : //endpoint response
            processEndpointResponse0x0000(descMap)
            break
		case "8021" : //bind response
			if (descMap.data[1] != "00") log.warn "Received a ZDO Bind response message with a failure code: ${descMap.inspect()}"
            break
		case "8022" : //unbind request
			if (descMap.data[1] != "00") log.warn "Received a ZDO Unbind response message with a failure code: ${descMap.inspect()}"
            break
        case "8034" : //leave response
			if (descMap.data[1] != "00") log.warn "Received a ZDO Leave response message with a failure code: ${descMap.inspect()}"
            break
        case "8038" : // Management Network Update Notify
			if (descMap.data[1] != "00") log.warn "Received a ZDO Management Network Update Notify response message with a failure code: ${descMap.inspect()}"
            break
    	default :
			if (logEnable) log.debug "skipped zdo binding message:${descMap.clusterId}"
	}
}
