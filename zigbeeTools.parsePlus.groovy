/*
Library to handle additional parsing of Zigbee messages - i.e.,  where Hubitat's own parseDescriptionAsMap leaves parts of the message as "data[]".
*/

library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Complete the Parsing of data not fully parsed by Hubitat's parseDescriptionAsMap()",
        name: "parsePlus",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

// Simple switch to detmine what additional parsing, if any, is to be performed.
// If Hubitat already produced a fully parsed message, this does nothing.
Map parsePlus(Map descMap) {
	if (descMap.isManufacturerSpecific) return descMap
	if (descMap.isClusterSpecific) return descMap
	
	switch (descMap.profileId) {
		case "0000": // profle "0000" is in ZigBee Document No. 05-3474-21, "ZigBee Specification"
			switch (descMap.clusterId) {
				case "0013" : // ZDO Announce message. ZDO 2.4.3.1.11
					descMap.put("announceData", [status:(descMap.data[0]), macCapability: (descMap.data[11])])
					break
				case "8004" : // simple descriptor response is in Section 2.4.4.2.5 of ZigBee Document No. 05-3474-21, "ZigBee Specification"
				    descMap.put("status", descMap.data[1])
					descMap = parse_Simple_Desc_rsp_0x0000( descMap)
					break
				case "8005" : //active endpoint response is in Section 2.4.4.2.6 of ZigBee Document No. 05-3474-21, "ZigBee Specification"
				    descMap.put("status", descMap.data[1])
					descMap = parse_Active_EP_rsp_0x0000(descMap)
					break
			}
			break
		case "C05E":
		case "0104":
			if (descMap.isClusterSpecific) { 
				log.warn "In parsePlus, received a cluster specific command ${descMap.inspect()}, need to confirm parsing!" 
			}
			switch (descMap.command) {
				case "07": // Configure Reporting Response ZCL 2.5.8
					if (logEnable) log.debug "Received a Configure Reporting Response ${descMap.inspect()}"
					break
				case "0B": // the Default Response, ZCL 2.5.12
					descMap.put("defaultResponse", [commandId: (descMap.data[0]), status:(descMap.data[1])]) 
					descMap.put("status", descMap.data[1])
					break
				case "0D": // Discover Attributes Response, ZCL 2.5.14
					descMap = parse_Discover_Attribute_Response(descMap)
					break
				case "12": // Commands Received Response, ZCL 2.5.19
					descMap.put("commandsReceived", descMap.data.tail() )
					break
			}
		break
	}
	return descMap
}
void process_found_attributes(descMap) {
    String ep = descMap.sourceEndpoint
    
    Map attributes = descMap.discoveredAttributesResponse.attributesDiscoveredMap

	// Store the data that was found in the global ConcurrentHashMap
    setClusterAttributesSupported( 
        clusterId: descMap.clusterId,
        ep: ep, 
        attributesMap: attributes, 
        )
    
	// If discoery isn't complete, then get the next set of attributes
    if (descMap.discoveredAttributesResponse.discoveryComplete == false) {
        
        List<String> allClusterAttributes = getClusterAttributesSupported(clusterId: descMap.clusterId, ep:ep).keySet().sort()
        Integer nextAttributeInt = Integer.parseInt( allClusterAttributes[-1] , 16) + 1
        String findFromAttribute = zigbee.convertToHexString( nextAttributeInt, 4)
        
        sendZCLAdvanced( // 0x0c is the Global Command to discovery attributes for a cluster
                destinationEndpoint: ep , 
                clusterId: descMap.clusterId, 
                isClusterSpecific: false , 
                commandId: 0x0c, 
                commandPayload: [findFromAttribute, "20"], // Continue searching for attributes
            )        
    }    
}

Map parse_Discover_Attribute_Response(Map descMap) { // ZCL specification section 2.5.14
    String ep = descMap.sourceEndpoint
    
	valuesToAdd = [
		discoveryComplete: (descMap.data[0] == "01") ? true : false ,
		attributesDiscoveredMap: [:], // Map is keyed by the Attribute ID, the value is the data type in Hex.
		]
	
	descMap.data.tail().collate(3, false) // separate data into groups of 3
		.each{ valuesToAdd.attributesDiscoveredMap.put( it[1..0].join(), it[2]) }

	valuesToAdd.attributesDiscoveredList = valuesToAdd.attributesDiscoveredMap.keySet().sort()
	
	descMap.put("discoveredAttributesResponse", valuesToAdd)
    
    process_found_attributes(descMap)
    
    return descMap
}

Map parse_Active_EP_rsp_0x0000(Map descMap) { // ZDO specification section 2.4.4.2.6
	Map valuesToAdd
   // descMap.data[0] appears to be a transmission sequence counter. Ignore that.
	if (descMap.data[1] != "00") { // failed status
		if (logEnable) log.debug "Received a Active Endpoint response with a failure code: ${descMap.inspect()}"
		return null
	} else { // status is 00 = success
		valuesToAdd = [
			status: descMap.data[1] , 
			// NWKAddrOfInterest: descMap.data[3..2].join(), // Redundant. Already present as descMap.dni
			ActiveEPCountHex: null ,
			ActiveEPCountHex: descMap.data[4],
			ActiveEPCountInt: Integer.parseInt(descMap.data[4], 16),
			activeEPListHex:  descMap.data[5..-1],
			activeEPListInt: descMap.data[5..-1].collect{ Integer.parseInt(it, 16) } 
			]
	}
	descMap.put("activeEndpointRsp", valuesToAdd)
	return descMap
}

// This function parses the simple descriptor response detailed in ZDO specification section 2.3.2.5 and 2.4.4.2.5
Map parse_Simple_Desc_rsp_0x0000(Map descMap) { // ZDO specification section 2.3.2.5 and 2.4.4.2.5
    // Status Values from ZDP 2.4.4.2.5, 2.4.5 and 2.5.4.6.1
	List remainingData = descMap.data
	
	// rData is the return data map with the parsed results
	// All values are in hex except inClusterCountInt and outClusterCountInt
	Map valuesToAdd = [ // Fields set below are present for all status codes, so set them now
			status: descMap.data[1] , 
			NWKAddrOfInterest: descMap.data[3..2].join() ,  // Redundant. Already present as descMap.dni
			// lengthHex: descMap.data[4] ,  // Only relevant in message transport; not needed as a return value
			// lengthInt: Integer.parseInt( descMap.data[4] , 16),  // Only relevant in message transport; not needed as a return value
			endpointId: descMap.data[5] , 
			profileId: null , 
			deviceId: null ,
			deviceVersion: null ,
			inClusterCountHex: null , 
			inClustersList: [] , 
			outClusterCountHex: null , 
			outClustersList: [] ,
			]
	if (descMap.data[1] != "00") { // 00 is suceess, anything else is a failure, so return null.
		if (logEnable) log.debug "Received a simple descriptor response with a failure code: ${descMap.inspect()}"
		return null
	}

	valuesToAdd << [
		profileId:		descMap.data[7..6].join() , 
		deviceId:		descMap.data[9..8].join() , 
		deviceVersion: 	descMap.data[10],
		inClusterCountHex:	descMap.data[11] ,
		inClusterCountInt: 	Integer.parseInt( descMap.data[11], 16)
		]
	
	// break inCluster list into groups of 2, reverse bytes, join, store.
	Integer inClustersStart = 12
	Integer inClustersEnd = inClustersStart + (2 * valuesToAdd.inClusterCountInt) -1
	valuesToAdd.inClustersList = descMap.data[inClustersStart..inClustersEnd]
									.collate(2).collect{ it[1..0].join() }
	valuesToAdd << [
			outClusterCountHex: descMap.data[inClustersEnd+1], // outclusters follows the inClusters
            outClusterCountInt: Integer.parseInt(descMap.data[inClustersEnd+1], 16)
		]
		
	// break outCluster list into groups of 2, reverse bytes, join, store.
	Integer outClustersStart = inClustersEnd + 2 // starts 2 after the end of the inCluster list).
	Integer outClustersEnd = 	outClustersStart + (2 * valuesToAdd.outClusterCountInt)-1
	valuesToAdd.outClustersList = descMap.data[outClustersStart..outClustersEnd]
									.collate(2,).collect{ it[1..0].join() }
									
	descMap.put("simpleDescriptorRsp", valuesToAdd)
    
	return descMap
}