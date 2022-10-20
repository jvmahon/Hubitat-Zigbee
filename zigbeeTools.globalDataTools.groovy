library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "A set of tools to set up and manage data stored in a global field.",
        name: "globalDataTools",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
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
			
			profileId: 0x0104 // Each Endpoint can have one more profiles with profile String as key
			inClusters: [] // List of inclusters for the endpoint
			outClusters: [] // List of outclusters for the endpoint
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

// Recursively recreate ConcurrentHashMap structure where every sub-Map in the original data is recreated as a ConcurrentHashMap
// When passed a map, this will return a concurrentHashMap where, if the value for a key in the original map was itselve a Map item, that value gets recreated as a ConcurrentHashMap.
// Primary use: If a device has already gathered all its relevant static data at it is stored in state, can recreate that from the stored state
ConcurrentHashMap recreateConcurrentMapStructure(Map data) {
    // log.debug "called recreateMapValue with data ${data.inspect()}"
    ConcurrentHashMap rValue = new ConcurrentHashMap()
    data.each {
        if (it.value instanceof ConcurrentHashMap) {
            log.debug "Entry ${it.key} was already a concurrentHashMap! ${it.inspect()}"
            rValue.put(it.key, it.value)
        } else if (it.value instanceof Map) {
            rValue.put(it.key, recreateConcurrentMapStructure(it.value) )
        } else {
            // Any non-map gets inserted into the result without further recursion
            // If the non-map was a List or other type that included embedded maps, those embedded maps are not converted into ConcurrentHashMaps
            rValue.put(it.key, it.value)
        }
    }
    return rValue
}

void reloadStoredStaticData() {
    getDataRecordByNetworkId().putAll( recreateConcurrentMapStructure(state.deviceStaticData) )
}

// This function isn't currently used. It would allow storing data "common" to multiple devices once!
ConcurrentHashMap getDataRecordByProductType(Map params = [:]) {
	Map inputs = [ isDynamicData: false ] << params
	
	assert inputs.isDynamicData instanceof Boolean

	String manufacturer = 	device.getDataValue("manufacturer")
	String model = 			device.getDataValue("model")
	String productKey = "${manufacturer}:${model}"
	
	if (inputs.isDynamicData) {
		return globalDynamicDataStorage.get(productKey, new ConcurrentHashMap<String,ConcurrentHashMap>(8, 0.75, 1))
	} else {
		return globalStaticDataStorage.get(productKey, new ConcurrentHashMap<String,ConcurrentHashMap>(8, 0.75, 1))
	}
}

void logDataRecordByProductType(){
    log.debug getDataRecordByProductType(isDynamicData: false)
}

ConcurrentHashMap getDataRecordByNetworkId(Map params = [:]) {
	Map inputs = [ isDynamicData: false ] << params

	assert inputs.isDynamicData instanceof Boolean

    String netId = device?.getDeviceNetworkId()
    if (netId.is( null ) ) return null
	if (inputs.isDynamicData) {
		return globalDynamicDataStorage.get(netId, new ConcurrentHashMap(16, 0.75, 1))
	} else {
		return globalStaticDataStorage.get(netId, new ConcurrentHashMap(16, 0.75, 1))
	}
}

List<String> getAllActiveEndpointsHexList() {
	List rValue = getDataRecordByNetworkId(isDynamicData: false )?.get("activeEndpointList", [])
    log.debug "Returning endpoints: ${rValue}"
    return rValue
}

List<String> setAllActiveEndpointsHexList(List<String> activeEndpoints) {
	List rValue =getDataRecordByNetworkId(isDynamicData: false ).put("activeEndpointList", activeEndpoints)
	state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	// state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )	
	return rValue
}

ConcurrentHashMap getDataRecordForEndpoint(Map params = [:]) {
	Map inputs = [ep: getEndpointId(device) , isDynamicData: false  ] << params
	if (inputs.ep instanceof Integer) { inputs.ep = zigbee.convertToHexString(inputs.ep, 2) } 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	
    return getDataRecordByNetworkId(*:inputs)
				.get(inputs.ep, new ConcurrentHashMap(4, 0.75, 1))
}

ConcurrentHashMap getClusterDataRecord(Map params = [:]) {
	Map inputs = [clusterId: null , ep: null , isDynamicData: false ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2

	return getDataRecordForEndpoint(*:inputs)
			.get("clusterInfo", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.get(inputs.clusterId, new ConcurrentHashMap<String,ConcurrentHashMap>(16, 0.75, 1))
}

List getClusterCommandsSupported(Map params = [:]){
	Map inputs = [ep: null , clusterId: null , isDynamicData: false  ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2

    return getClusterDataRecord(*:inputs)
			.get("clusterSpecificCommandsHex", [])
}

List setClusterCommandsSupported(Map params = [:]) {
	Map inputs = [clusterId: null , ep: null , commandList: null , isDynamicData: false  ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	assert inputs.commandList instanceof List

	if (inputs.commandList.size() < 1) return
    List rValue = getClusterDataRecord(*:inputs)
					.put("clusterSpecificCommandsHex", inputs.commandList)
    state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	// state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )						
	return rValue
}

ConcurrentHashMap getClusterAttributesSupported(Map params = [:]){
	Map inputs = [ep: null , clusterId: null , isDynamicData: false  ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2

    return getClusterDataRecord(*:inputs)
			.get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(16, 0.75, 1))
}

ConcurrentHashMap setClusterAttributesSupported(Map params = [:]) {
	Map inputs = [clusterId: null , ep: null , attributesMap: null , isDynamicData: false  ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	assert inputs.attributesMap instanceof Map

	if (inputs.attributesMap.size() < 1) return
    ConcurrentHashMap rValue = getClusterAttributesSupported(*:inputs)
								.putAll(inputs.attributesMap)
    state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	// state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )									
	return rValue
}

String getClusterAttributeDataType(Map params = [:]) {
	Map inputs = [clusterId: null , ep: getEndpointId(device) , attributeId: null , isDynamicData: false  ] << params
	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2	
	assert inputs.attributeId instanceof String || inputs.attributeId instanceof GString
	assert inputs.attributeId.length() == 4
	
	return getClusterAttributesSupported(*:inputs).get(inputs.attributeId)
}

ConcurrentHashMap getLastSentCommand(Map params = [:] ){
	Map inputs = [clusterId: null , commandId: null , ep: null, isDynamicData: true  ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	if (inputs.commandId instanceof Integer) inputs.commandId = zigbee.convertToHexString(inputs.commandId,2)
	assert inputs.commandId instanceof String || inputs.commandId instanceof GString
	assert inputs.commandId.length() == 2
	
	return getClusterDataRecord(*:inputs)
			.get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.get(inputs.commandId)
}

ConcurrentHashMap removeLastSentCommand(Map params = [:] ){
	Map inputs = [clusterId: null , commandId: null , ep: null, isDynamicData: true ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	if (inputs.commandId instanceof Integer) inputs.commandId = zigbee.convertToHexString(inputs.commandId,2)
	assert inputs.commandId instanceof String || inputs.commandId instanceof GString
	assert inputs.commandId.length() == 2

    ConcurrentHashMap rValue = getClusterDataRecord(*:inputs)
								.get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
									.remove(inputs.commandId)
    // state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )										
	return rValue // Returns the value associted with the removed key
}

ConcurrentHashMap setLastSentCommand (Map params = [:] ){
	Map inputs = [clusterId: null , commandId: null , commandData: null , ep: null, isDynamicData: true ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	if (inputs.commandId instanceof Integer) inputs.commandId = zigbee.convertToHexString(inputs.commandId,2)
	assert inputs.commandId instanceof String || inputs.commandId instanceof GString
	assert inputs.commandId.length() == 2
	assert inputs.commandData instanceof Map

    if (logEnable) log.debug "For Cluster ${inputs.clusterId}, command ${ inputs.commandId}: Storing ${inputs.commandData}"
	ConcurrentHashMap rValue = getClusterDataRecord(*:inputs)
								.get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
									.put(inputs.commandId, inputs.commandData)
    // state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )										
	return rValue
}

ConcurrentHashMap getClusterAttributeValue(Map params = [:] ){
	Map inputs = [clusterId: null , attributeInt: null , ep: null , isDynamicData: true ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	assert inputs.attributeId instanceof String || inputs.attributeId instanceof GString
	assert inputs.attributeId.length() == 4
	
	return getClusterDataRecord(*:inputs)
			.get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
				.get(inputs.attributeId)
}

ConcurrentHashMap setClusterAttributeValue(Map params = [:]){
	Map inputs = [clusterId: null , attributeId: null , attributeData: null , ep: null , isDynamicData: true ] << params

	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.clusterId.length() == 4 
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2
	assert inputs.attributeId instanceof String || inputs.attributeId instanceof GString
	assert inputs.attributeId.length() == 4 

	assert inputs.attributeData instanceof String || inputs.attributeData instanceof GString

	
	ConcurrentHashMap rValue = getClusterDataRecord(*:inputs)
								.get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
									.put(inputs.attributeId, inputs.attributeData)
									
    // state.deviceStaticData = getDataRecordByNetworkId(isDynamicData: false )
	state.deviceDynamicData  = getDataRecordByNetworkId(isDynamicData: true )									
	return rValue
}

// Debugging Functions

void showFullGlobalDataRecord() {
	// Debugging function - shows the entire concurrent @Field 'global' data record for all devices using a particular driver
	log.info "Global Static Data Record Is ${getDataRecordByNetworkId(isDynamicData: false ).inspect()}."
	log.info "Global Dynamic Data Record Is ${getDataRecordByNetworkId(isDynamicData: true ).inspect()}."
}