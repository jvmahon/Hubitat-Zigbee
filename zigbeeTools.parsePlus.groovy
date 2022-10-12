library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Complete the Parsing of data not fully parsed by Hubitat's parseDescriptionAsMap()",
        name: "parsePlus",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

Map parsePlus(Map descMap) {
	if (descMap.isManufacturerSpecific) return descMap
	if (descMap.isClusterSpecific) return descMap
	
	switch (descMap.profileId) {
		case "0000": // profle "0000" is in ZigBee Document No. 05-3474-21, "ZigBee Specification"
			switch (descMap.clusterId) {
				case "8004" : //simple descriptor response is in Section 2.4.4.2.5 of ZigBee Document No. 05-3474-21, "ZigBee Specification"
					descMap = parse_Simple_Desc_rsp_0x0000( descMap)
					break
				case "8005" : //active endpoint response is in Section 2.4.4.2.6 of ZigBee Document No. 05-3474-21, "ZigBee Specification"
					descMap = parse_Active_EP_rsp_0x0000(descMap)
					break
			}
			break
		case "0104":
			switch (descMap.command) {
				case "0D":
				// log.warn "Not yet parsing attribute responses"
                descMap = parse_Discover_Attribute_Response(descMap)
				break
			}
			break
	}
	return descMap
}
void process_found_attributes(descMap) {
    String ep = descMap.sourceEndpoint
    
    Map attributes = descMap.discoveredAttributesResponse.attributesDiscoveredMap
    
    setClusterAttributesSupported( 
        clusterId: descMap.clusterId,
        ep: ep, 
        attributesMap: attributes, 
        )
    
    if (descMap.discoveredAttributesResponse.discoveryComplete == false) {
        
        List<String> allClusterAttributes = getClusterAttributesSupported(clusterId: descMap.clusterId, ep:ep).keySet().sort()
        Integer nextAttributeInt = Integer.parseInt( allClusterAttributes[-1] , 16) + 1
        String findFromAttribute = zigbee.convertToHexString( nextAttributeInt, 4)
        
        sendZCLAdvanced( // 0x0c is the Global Command to discovery attributes for a cluster
                destinationEndpoint: ep , 
                clusterId: descMap.clusterId, 
                isClusterSpecific: false , 
                commandId: 0x0c,             // The command ID as an integer
                commandPayload: byteReverseParameters([findFromAttribute, "20"]) // Continue searching for attributes
            )        
    }    
}

Map parse_Discover_Attribute_Response(Map descMap) { // ZCL specification section 2.5.14
    String ep = descMap.sourceEndpoint
    
	valuesToAdd = [
		discoveryComplete: (descMap.data[0] == "01") ? true : false ,
		attributesDiscoveredMap: [:], // Map is keyed by the Attribute ID, the value is the data type in Hex.
		]
	List attributeData = descMap.data.tail()

	for(int i = 0; i < attributeData.size(); i += 3) {
		valuesToAdd.attributesDiscoveredMap.put(attributeData[i+1..i].join() , attributeData[i+2])
	}
	valuesToAdd.attributesDiscoveredList = valuesToAdd.attributesDiscoveredMap.keySet().sort()
	
	descMap.put("discoveredAttributesResponse", valuesToAdd)
    
    process_found_attributes(descMap)
    
    return descMap
}

Map parse_Active_EP_rsp_0x0000(Map descMap) { // ZDO specification section 2.4.4.2.6
	Map valuesToAdd
   // descMap.data[0] appears to be a transmission sequence counter. Ignore that.
	if (descMap.data[1] != "00") { // failed status
		valuesToAdd = [
			status: descMap.data[1] , 
			// NWKAddrOfInterest: null , // Redundant. Already present as descMap.dni
			ActiveEPCountHex: null ,
			ActiveEPCountInt: null ,
			activeEPListHex: null ,
			activeEPListInt: null ,
			]
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

List<String> joinClustersOctetsReversed(List clusterList) {
    List rData = []
    for (int i = 0; i < (clusterList.size); i += 2) {
        rData <<  clusterList[i+1] + clusterList[i]
    }
    return rData
}

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
			endpointHex: null , 
            endpointInt: null,
			profileIdHex: null , 
			deviceIdHex: null ,
			deviceVersionHex: null ,
			inClusterCountHex: null , 
			inClustersListHex: [] , 
			outClusterCountHex: null , 
			outClustersListHex: [] ,
			]
	if (descMap.data[1] != "00") { // 00 is suceess, anything else is a failure, so return.
		descMap.put("simpleDescriptorRsp", valuestoAdd)
		return descMap
	}

	valuesToAdd << [
		endpointHex:		descMap.data[5] , 
        endpointInt:        Integer.parseInt(descMap.data[5],16),
		profileIdHex:		descMap.data[7..6].join() , 
		deviceIdHex:		descMap.data[9..8].join() , 
		deviceVersionHex: 	descMap.data[10],
		inClusterCountHex:	descMap.data[11] ,
		inClusterCountInt: 	Integer.parseInt( descMap.data[11], 16)
		]
	Integer listStart = 12
	Integer listEnd = listStart + (2 * valuesToAdd.inClusterCountInt) -1
	if (valuesToAdd.inClusterCountInt > 0) {
		for(int i = listStart; i < listEnd; i +=2) {
			valuesToAdd.inClustersListHex << descMap.data[i+1..i].join()
		}
		valuesToAdd.inClustersListInt = valuesToAdd.inClustersListHex.collect{ Integer.parseInt(it, 16) }
	}

	valuesToAdd << [
			outClusterCountHex: descMap.data[listEnd+1], // outclusters follows the inClusters
            outClusterCountInt: Integer.parseInt(descMap.data[listEnd+1], 16)
		]

	listStart = listEnd + 2 // outClusterList is after the count (2 after the end of the inCluster list).
	listEnd = 	listStart + (2 * valuesToAdd.outClusterCountInt)-1

    if (valuesToAdd.outClusterCountInt > 0) {
		for(int i = listStart; i < listEnd; i +=2) {
			valuesToAdd.outClustersListHex << descMap.data[i+1..i].join()
		}
		valuesToAdd.outClustersListInt = valuesToAdd.outClustersListHex.collect{ Integer.parseInt(it, 16) }
	}
	descMap.put("simpleDescriptorRsp", valuesToAdd)
    
	return descMap
}