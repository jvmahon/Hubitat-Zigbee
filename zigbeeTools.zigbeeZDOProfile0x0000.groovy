library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Handle Bindings in ZDO Profile 0x0000",
        name: "zigbeeZDOProfile0x0000",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

void processEndpointResponse0x0000(Map descMap) {
	setAllActiveEndpointsHexList(descMap.activeEndpointRsp.activeEPListHex)
	
	// Now get the Simple Descriptor List for each Active Endpoint
	descMap.activeEndpointRsp.activeEPListHex.each{ epHex ->
	    sendZDOAdvanced(
		    clusterId: 0x0004,
		    commandId: 0x00, // Unsure - might always be 00 for ZDO?
		    commandPayload: byteReverseParameters([device.deviceNetworkId, epHex]) // ZDO Spec Section 2.4.3.1.5, Fig. 2.24
		    ) 		
	}
}

void processSimpleDescriptorResponse(Map descMap){
	String ep = descMap.simpleDescriptorRsp.endpointHex
	getDataRecordForEndpoint(ep:ep ).put("profileIdHex", 	descMap.simpleDescriptorRsp.profileIdHex)
	getDataRecordForEndpoint(ep:ep ).put("inClustersHex", 	descMap.simpleDescriptorRsp.inClustersListHex)
	getDataRecordForEndpoint(ep:ep ).put("outClustersHex", 	descMap.simpleDescriptorRsp.outClustersListHex)
    
    switch (descMap.simpleDescriptorRsp.profileIdHex) {
        case "0104":
			descMap.simpleDescriptorRsp.inClustersListHex?.each{ it ->
				sendZCLAdvanced( // 0x11 is the Global Command to discovery commands Received by the device
						destinationEndpoint: ep , 
						clusterId: it , 
						commandId: 0x11,	// The command ID as an integer
						commandPayload: byteReverseParameters( ["00", "40"]),     // Start at 00 and collect up to 64 ("0x40") commands
					) 
				sendZCLAdvanced( // 0x0c is the Global Command to discovery attributes for a cluster
						destinationEndpoint: ep , 
						clusterId: it, 
						commandId: 0x0c,	// The command ID as an integer
						commandPayload: byteReverseParameters( ["0000", "20"]),     // Start at 00 and collect up to 16 ("10") attributes
					)    
			}
			// Only process for the inClusters. No data seems to be gathered for outClusters, so skip those!
        break
    }
}

void processClusterResponse0x0000_xxxx(Map descMap) {
	if (descMap?.profileId != "0000") return 
    
	if (logEnable) log.debug "A zigbee binding message was received: ${descMap}"
    
	switch (descMap.clusterId) {
         case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : //"device announce"
            if (logEnable) log.debug "Received a device announce binding message: ${descMap}. Configuring device."
            configure()        
			break
		case "8004" : //simple descriptor response  
            if (logEnable) log.debug "Received an Simple Descriptor Response message: ${descMap}"
            processSimpleDescriptorResponse( descMap)
            break
        case "8005" : //endpoint response
            processEndpointResponse0x0000(descMap)
            break
		case "8021" : //bind response
            if (logEnable) log.info "${device.displayName} Received ZDO Bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
		case "8022" : //unbind request
            if (logEnable) log.info "Receiveid a ZDO Unbind response message ${descMap}"
            break
        case "8034" : //leave response
            if (logEnable) log.info "${device.displayName} Received Leave Response, data=${descMap.data}"
            break
        case "8038" : // Management Network Update Notify
            if (logEnable) log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}"
            break
    	default :
			if (logEnable) log.debug "skipped zdo binding message:${descMap.clusterId}"
	}
}

void getActiveEndpointsZDO() {
    if (txtEnable) log.info "Getting Active Endpoints"

    sendZDOAdvanced(
		clusterId: 0x0005,
		commandId: 0x00,
		commandPayload: byteReverseParameters([device.deviceNetworkId])  // ZDO Spec Section 2.4.3.1.6, Fig. 2.25
		)   
}

/*
Next step is to add a function to only get this if it isn't in state!
void getActiveEndpointsZDO() {
    log.info "Getting Active Endpoints"
    if (state.deviceData.activeEndpointList) {
        processEndpointResponse0x0000([state.deviceData.activeEndpointList])
    } else {
    sendZDOAdvanced(
		clusterId: 0x0005,
		commandId: 0x00,
		commandPayload: byteReverseParameters([device.deviceNetworkId])  // ZDO Spec Section 2.4.3.1.6, Fig. 2.25
		)   
    }
}
*/
