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
   // log.debug "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}, Full data map is ${descMap}"

   setAllActiveEndpointsHexList(descMap.data[5..-1])

   device.updateDataValue("endpointIdHex", descMap.data[5])
   device.updateDataValue("endpointId", "${Integer.parseInt(descMap.data[5], 16)}" )
	
    Integer zdoEndpoint = 0
    List cmds = []
	getAllActiveEndpointsHexList().each{ epHex ->
		cmds += ["he raw ${device.deviceNetworkId}  0x${zdoEndpoint}  0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} ${epHex}} {0x0000}"]  
		}

    log.warn "Sending commands to get endpoint Simple Descriptor Responses ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void processClusterResponse0x0000_xxxx(Map descMap) {
    
	if (logEnable) log.debug "A zigbee binding message was received: ${descMap}"
   
    // Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0000")) return 
	// End of rejection reasons!	
    
	switch (descMap.clusterId) {
         case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : //"device announce"
            if (logEnable) log.debug "Received a device announce binding message: ${descMap}. Configuring device."
            configure()        
			break
		case "8004" : //simple descriptor response  
            log.warn "Received an Simple Descriptor Response message: ${descMap}"
            parseSimpleDescriptorResponse( descMap)
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

void getActiveEndpoints() {
    log.info "Getting Active Endpoints"
    List<String> cmds = []
    Integer zdoEndpoint = 0
	
    cmds += ["he raw ${device.deviceNetworkId}  0x${zdoEndpoint}  0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] //get active endpoints...

	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}


/*
ZDO Transmission Info.
Cluster name	            Cluster ID	Description
Network Address Request	    0x0000	    Request a 16-bit address of the radio with a matching 64-bit address (required parameter).
Active Endpoints Request	0x0005	    Request a list of endpoints from a remote device.
LQI Request	                0x0031	    Request data from a neighbor table of a remote device.
Routing Table Request	    0x0032	    Request to retrieve routing table entries from a remote device.
Network Address Response	0x8000	    Response that includes the 16-bit address of a device.
LQI Response	            0x8031	    Response that includes neighbor table data from a remote device.
Routing Table Response	    0x8032	    Response that includes routing table entry data from a remote device.
*/

List<String> joinClustersOctetsReversed(List clusterList) {
    List rData = []
    for (int i = 0; i < (clusterList.size); i += 2) {
        rData <<  clusterList[i+1] + clusterList[i]
    }
    return rData
}

Map parseSimpleDescriptorResponse(Map descMap) {
    
    // Status Values from ZDP 2.4.4.2.5, 2.4.5 and 2.5.4.6.1
	List remainingData = descMap.data
	
	// rData is the return data map with the parsed results
	// All values are in hex except inClusterCountInt and outClusterCountInt
	Map rData = [data0: null , status: null , address: null , length: null , endPoint: null , profileId: null , appVersion: null , appProfile: null , inClusterCountInt: null , inClusters: null , outClusterCountInt: null , outClusters: null ]
	
	rData <<  [
		data0:	( descMap.data[0] ), 
		status:		( descMap.data[1] ), 
		address: 	( descMap.data[3..2].join() )
	]

	switch (rData.status) {
		case "00":
			if (logEnable) log.info "Received a Simple Descriptor Response with status 00 : Success"
			break
		case "80":
			if (logEnable) log.debug "Received a Simple Descriptor Response with status 80 : Invalid Request Type"
			return
		case "81":
			if (logEnable) log.debug "Received a Simple Descriptor Response with status 81 : Device Not Found"
			return
		case "82":
			if (logEnable) log.debug "Received a Simple Descriptor Response with status 82 : Invalid Endpoint"
			return
		case "83":
			if (logEnable) log.debug "Received a Simple Descriptor Response with status 83 : Not Active"
			return
		case "89":
			if (logEnable) log.debug "Received a Simple Descriptor Response with status 89 : No Descriptor"
			return
		default:
			if (logEnable) log.error "Received a Simple Descriptor Response with status ${returnData.status}. Incorrect Status."
	}

	rData << [
		length: 	( descMap.data[4] ), 
		endPoint:	( descMap.data[5] ), 
		profileId:	( descMap.data[7..6].join() ), 
		appVersion:	( descMap.data[9..8].join() ), 
		appProfile:	( descMap.data[10] )
		]
	remainingData = remainingData.drop(11)	 // trim off 11 data items 0..10 as they have already been processed!	
		
	rData.inClusterCountInt = Integer.parseInt(remainingData[0], 16)
    if (rData.inClusterCountInt > 0) {
		rData.inClusters = joinClustersOctetsReversed( remainingData[1..(rData.inClusterCountInt*2)])
	}
	
	remainingData = remainingData.drop(1+rData.inClusterCountInt *2)// trim off what's already been processed!
	
	rData.outClusterCountInt = Integer.parseInt(remainingData[0], 16)
    if (rData.outClusterCountInt > 0) {
		rData.outClusters = joinClustersOctetsReversed(remainingData[1..(rData.outClusterCountInt*2)] )
	}
    if (logEnable) log.debug "Processed Simple Descriptor. Return Value is ${rData}"
    return rData
}