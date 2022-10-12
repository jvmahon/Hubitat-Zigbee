library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "A set of tools to set up and manage data stored in a global field.",
        name: "globalDataTools",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

import java.util.concurrent.* 
import groovy.transform.Field

@Field static ConcurrentHashMap globalStaticDataStorage = new ConcurrentHashMap(32, 0.75, 1) // Intended to Store info. that does not change. Default is static
@Field static ConcurrentHashMap globalDynamicDataStorage = new ConcurrentHashMap(32, 0.75, 1) // Intended to store info that changes

/*

globalDataStorage = [:]
	deviceNetworkId = [:] // The globalDataStorage includes a Map for each device, with deviceNetworkId as key
		activeEndpointList = [] // List of Active Endpoints in Hex form. Endpoint "00" = ZDO is never on the list.
		ep = [:] // Each device can then have one or more endpoint maps with endpoint as the key
			
			profileIdHex: 0x0104 // Each Endpoint can have one more profiles with profile String as key
			inClustersHex: [] // List of inclusters for the endpoint
			outClustersHex: [] // List of outclusters for the endpoint
			groups: = [] // List of groups that the endpoint is a member of. In Hex form.
			clusterInfo = [] // Information about each individual cluster.
					clusterId = [:] // Each Endpoint can have one or more clusters, cluster Hex value as key
						clusterSpecificCommandsHex = [] // List of cluster specific commands in hex form
						
						lastSentCommand =  [:] // Can have multiple last sent commands for each cluster, with command number as key
							Command#: Map[Map describing command] // each command is represented as a user-defined map
							Command#: Map[Map describing command]
						supportedAttributes = [] // to be added - list of attributes for the cluster, attribute #
						supportedCommandsHex = [] // List of commands supported by the cluster, as Hex strings
						attributeValues = [:] //  Map of attribute values, attribute # as key, value as String

					clusterId
				.
			profileId: 0x0000
				.
		ep:0
		.
	deviceNetworkId
	.
*/

ConcurrentHashMap getDataRecordByProductType(Map params = [:]) {
	Map inputs = [ isDynamicData: false ] << params
	
	assert inputs.isDynamicData instanceof Boolean

	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String model = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("model").toInteger(), 2)
	String productKey = "${manufacturer}:${model}"
	
	if (inputs.isDynamicData) {
		return globalDynamicDataStorage.get(productKey, new ConcurrentHashMap<String,ConcurrentHashMap>(8, 0.75, 1))
	} else {
		return globalStaticDataStorage.get(productKey, new ConcurrentHashMap<String,ConcurrentHashMap>(8, 0.75, 1))
	}
}

void logDataRecordByProductType(){
    log.debug getDataRecordByProductType()
}

ConcurrentHashMap getDataRecordByNetworkId(Map params = [:]) {
	Map inputs = [ isDynamicData: false ] << params
	
	assert inputs.isDynamicData instanceof Boolean
	
    String netId = device.getDeviceNetworkId()
	if (inputs.isDynamicData) {
		return globalDynamicDataStorage.get(netId, new ConcurrentHashMap(16, 0.75, 1))
	} else {
		return globalStaticDataStorage.get(netId, new ConcurrentHashMap(16, 0.75, 1))
	}
}

List<String> getAllActiveEndpointsHexList() {
	getDataRecordByNetworkId().get("activeEndpointList", [])
}

List<String> setAllActiveEndpointsHexList(List<String> activeEndpoints) {
	getDataRecordByNetworkId().put("activeEndpointList", activeEndpoints)
}

ConcurrentHashMap getDataRecordForEndpoint(Map params = [:]) {
	Map inputs = [ep: null ] << params
	
    assert ! inputs.ep.is( null )
	
	if (inputs.ep instanceof Integer) {
		String epHex = zigbee.convertToHexString(inputs.ep, 2)
		getDataRecordByNetworkId().get(epHex, new ConcurrentHashMap(4, 0.75, 1))
	} else if (inputs.ep instanceof String || inputs.ep instanceof GString) {
		assert inputs.ep.length() == 2
		getDataRecordByNetworkId().get((inputs.ep as String), new ConcurrentHashMap(4, 0.75, 1))
	}
}

ConcurrentHashMap getClusterDataRecord(Map params = [:]) {
	Map inputs = [clusterId: null , ep: null ] << params
	
	try {
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.ep instanceof String || inputs.ep instanceof GString // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}
	
	return getDataRecordForEndpoint(ep: inputs.ep)
			.get("clusterInfo", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.get(inputs.clusterId, new ConcurrentHashMap<String,ConcurrentHashMap>(16, 0.75, 1))
}

List getClusterCommandsSupported(Map params = [:]){
	Map inputs = [ep: null , clusterId: null ] << params
	
	try { 
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.ep instanceof String || inputs.ep instanceof GString // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}

    return getClusterDataRecord(*:inputs)
			.get("clusterSpecificCommandsHex", [])
}

List setClusterCommandsSupported(Map params = [:]) {
	Map inputs = [clusterId: null , ep: null , commandList: null ] << params
	
	try {
		assert inputs.clusterId instanceof String  || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.ep instanceof String || inputs.ep instanceof GString  // Not strictly needed. Also gets checked elsewhere.
		assert inputs.commandList instanceof List
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}
	if (inputs.commandList.size() < 1) return
    return getClusterDataRecord(*:inputs)
			.put("clusterSpecificCommandsHex", inputs.commandList)
}

Map getClusterAttributesSupported(Map params = [:]){
	Map inputs = [ep: null , clusterId: null ] << params
	
	try { 
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.ep instanceof String || inputs.ep instanceof GString // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}

    return getClusterDataRecord(*:inputs)
			.get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(16, 0.75, 1))
}

List setClusterAttributesSupported(Map params = [:]) {
	Map inputs = [clusterId: null , ep: null , attributesMap: null ] << params
	
	try {
		assert inputs.clusterId instanceof String  || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.ep instanceof String || inputs.ep instanceof GString  // Not strictly needed. Also gets checked elsewhere.
		assert inputs.attributesMap instanceof Map
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}
	if (inputs.attributesMap.size() < 1) return
    return getClusterAttributesSupported(*:inputs)
			.putAll(inputs.attributesMap)
}


Object getLastSentCommand(Map params = [:] ){
	Map inputs = [clusterId: null , commandNum: null , ep: null, isDynamicData: true  ] << params
	
	try {
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.commandNum instanceof Integer
		assert inputs.ep instanceof String || inputs.ep instanceof GString // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}
	
	return getClusterDataRecord(*:inputs)
			.get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.get(inputs.commandNum)
}

Object removeLastSentCommand(Map params = [:] ){
	Map inputs = [clusterId: null , commandNum: null , ep: null, isDynamicData: true ] << params
	
	try {
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.commandNum instanceof Integer
		assert inputs.ep instanceof String || inputs.ep instanceof GString // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}

    return getClusterDataRecord(*:inputs)
			.get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.remove(inputs.commandNum)
}

Object setLastSentCommand (Map params = [:] ){
	Map inputs = [clusterId: null , commandNum: null , commandData: null , ep: null, isDynamicData: true ] << params

	try {
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.commandNum instanceof Integer
		assert inputs.commandData instanceof Map
		assert inputs.ep instanceof String || inputs.ep instanceof GString // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}

    if (logEnable) log.debug "For Cluster ${inputs.clusterId}, command ${ inputs.commandNum}: Storing ${inputs.commandData}"
	return getClusterDataRecord(*:inputs)
			.get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.put(inputs.commandNum, inputs.commandData)
}

Object getClusterAttributeValue(Map params = [:] ){
	Map inputs = [clusterId: null , attributeInt: null , ep: null , isDynamicData: true ] << params

	try {
		assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString // Not strictly needed. Also gets checked elsewhere.
		assert inputs.attributeId instanceof String || inputs.attributeId instanceof GString
		assert inputs.ep instanceof String  || inputs.ep instanceof GString  // Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}
	
	return getClusterDataRecord(*:inputs)
			.get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.get(inputs.attributeId)
}

Object setClusterAttributeValue(Map params = [:]){
	Map inputs = [clusterId: null , attributeId: null , attributeData: null , ep: null , isDynamicData: true ] << params
	
	try { 
		assert inputs.clusterId instanceof String  || inputs.clusterId instanceof GString// Not strictly needed. Also gets checked elsewhere.
		assert inputs.attributeId instanceof String || inputs.attributeId instanceof GString
		assert inputs.attributeData instanceof String || inputs.attributeData instanceof GString
		assert inputs.ep instanceof String || inputs.ep instanceof GString// Not strictly needed. Also gets checked elsewhere.
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to function. <pre>${e}"
		throw(e)
	}	
	return getClusterDataRecord(*:inputs)
			.get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.put(inputs.attributeId, inputs.attributeData)
}
/*
Object getHubitatAttributeValue(String attribute, Integer ep){
    log.debug "getting attribute value for ${attribute}, endpoint ${ep}"
	return getDataRecordForEndpoint(ep:ep).get("hubitatAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).get(attribute)
}
Object setHubitatAttributeValue(String attribute, Object attributeData, Integer ep){
	return getDataRecordForEndpoint(ep:ep).get("hubitatAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).put(attribute, attributeData)
}
*/

// Debugging Functions
void showGlobalDataRecordByProductType() {
	// Debugging function - shows the entire concurrent @Field 'global' data record for all devices using a particular driver
	log.info "Data record in global storage is ${dataRecordByProductType.inspect()}."
}

void showFullGlobalDataRecord() {
	// Debugging function - shows the entire concurrent @Field 'global' data record for all devices using a particular driver
	log.info "Global Static Data Record Is ${getDataRecordByNetworkId(isDynamicData: false ).inspect()}."
	log.info "Global Dynamic Data Record Is ${getDataRecordByNetworkId(isDynamicData: true ).inspect()}."

}