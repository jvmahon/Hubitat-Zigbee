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

@Field static ConcurrentHashMap globalDataStorage = new ConcurrentHashMap(64, 0.75, 1)

/*

globalDataStorage = [:]
	deviceNetworkId = [:] // The globalDataStorage includes a Map for each device, with deviceNetworkId as key
		ep = [:] // Each device can then have one or more endpoint maps with endpoint as the key
			
			profileId: 0x0104 = [:] // Each Endpont can have one more profiles with profile String as key
				clusterId = [:] // Each Profile can have one or more clusters, clusterInt as key
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


ConcurrentHashMap getDataRecordByProductType(){

	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	
	String model = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("model").toInteger(), 2)
	
	String productKey = "${manufacturer}:${deviceType}"
	ConcurrentHashMap dataRecord = globalDataStorage.get(productKey, new ConcurrentHashMap<String,ConcurrentHashMap>(8, 0.75, 1))
	return dataRecord
}

void logDataRecordByProductType(){
    log.debug getDataRecordByProductType()
}

ConcurrentHashMap getDataRecordByNetworkId() {
    String netId = device.getDeviceNetworkId()
	return globalDataStorage.get(netId, new ConcurrentHashMap(16, 0.75, 1))
}

List getAllActiveEndpointsHexList() {
	getDataRecordByNetworkId().get("activeEndpointList")
}

List setAllActiveEndpointsHexList(List activeEndpoints) {
	getDataRecordByNetworkId().put("activeEndpointList", activeEndpoints)
}

ConcurrentHashMap getDataRecordForEndpoint(Map inputs = [ep: null ]) {
	assert inputs.ep instanceof Integer 
	getDataRecordByNetworkId().get((inputs.ep), new ConcurrentHashMap(4, 0.75, 1))
}

ConcurrentHashMap getProfileDataRecord(Map inputs = [profileId: null , ep: null ]) {
	/* Asserts aren't strictly needed since this is only called internal to this library and all the inputs are already checked elsewhere*/
	assert inputs.profileId instanceof String
	assert inputs.ep instanceof Integer

	return getDataRecordForEndpoint(ep:(inputs.ep))
            .get("profileInfo", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1) )
                .get(inputs.profileId, new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1) )
}

ConcurrentHashMap getClusterDataRecord(Map inputs = [clusterInt: null , profileId: null , ep: null ]) {
	assert inputs.clusterInt 	instanceof Integer
	assert inputs.profileId 	instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep 			instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	
	return getProfileDataRecord(*:inputs)
                    .get("clusterInfo", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
                        .get(inputs.clusterInt, new ConcurrentHashMap<String,ConcurrentHashMap>(16, 0.75, 1))
}

List getClusterCommandsSupported(Map inputs = [clusterInt: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep instanceof Integer // Not strictly needed. Also gets checked elsewhere.

    return getClusterDataRecord(*:inputs).get("supportedCommandsHex", [])
}

List setClusterCommandsSupported(Map inputs = [clusterInt: null , profileId: null , ep: null, commandList: null ]){
	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep instanceof Integer // Not strictly needed. Also gets checked elsewhere.
    assert inputs.commandList instanceof List

    return getClusterDataRecord(*:inputs).put("supportedCommandsHex", inputs.commandList)
}


Object getLastSentCommand(Map inputs = [clusterInt: null , commandNum: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.commandNum instanceof Integer
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	
	return getClusterDataRecord(*:inputs).get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).get(inputs.commandNum)
}

Object removeLastSentCommand(Map inputs = [clusterInt: null , commandNum: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.commandNum instanceof Integer
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep instanceof Integer // Not strictly needed. Also gets checked elsewhere.

    return getClusterDataRecord(*:inputs).get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).remove(inputs.commandNum)
}

Object setLastSentCommand (Map inputs = [clusterInt: null , commandNum: null , commandData: null , profileId:  null , ep: null ]){

	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.commandNum instanceof Integer
	assert inputs.commandData instanceof Map
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep instanceof Integer // Not strictly needed. Also gets checked elsewhere.

    if (logEnable) log.debug "For Cluster ${clusterInt}, command ${ commandNum}: Storing ${commandData}"
	return getClusterDataRecord(*:inputs).get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).put(inputs.commandNum, inputs.commandData)
}

Object getClusterAttributeValue(Map inputs = [clusterInt: null , attributeInt: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.attributeInt instanceof Integer
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep	instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	
	return getClusterDataRecord(*:inputs).get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).get(inputs.attributeInt)
}

Object setClusterAttributeValue(Map inputs = [clusterInt: null , attributeInt: null , attributeData: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	assert inputs.attributeInt instanceof Integer
	assert inputs.attributeData instanceof String
	assert inputs.profileId instanceof String // Not strictly needed. Also gets checked elsewhere.
	assert inputs.ep	instanceof Integer // Not strictly needed. Also gets checked elsewhere.
	
	return getClusterDataRecord(*:inputs).get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).put(inputs.attributeInt, inputs.attributeData)
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

	log.debug "Data record in global storage is ${dataRecordByProductType.inspect()}."
}

void showFullGlobalDataRecord() {
	// Debugging function - shows the entire concurrent @Field 'global' data record for all devices using a particular driver
	log.debug "Global Data Record Is ${globalDataStorage.inspect()}."
}