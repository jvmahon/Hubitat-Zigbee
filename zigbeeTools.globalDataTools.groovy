library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "A set of tools to set up and manage data stored in a global field.",
        name: "globalDataTools",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/HubitatDriverTools",
		version: "0.0.1",
		dependencies: "none",
		librarySource:"https://raw.githubusercontent.com/jvmahon/HubitatDriverTools/main/globalDataTools.groovy"
)

import java.util.concurrent.* 
import groovy.transform.Field

@Field static ConcurrentHashMap globalDataStorage = new ConcurrentHashMap(64, 0.75, 1)

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

ConcurrentHashMap getDataRecordForEndpoint(Map inputs = [ep: null ]) {
	assert inputs.ep instanceof Integer 
	getDataRecordByNetworkId().get((inputs.ep), new ConcurrentHashMap(16, 0.75, 1))
}

ConcurrentHashMap getProfileDataRecord(Map inputs = [profileId: null , ep: null ]) {
	assert inputs.profileId instanceof String
	assert inputs.ep instanceof Integer

	return getDataRecordForEndpoint(ep:inputs.ep)
            .get("profileInfo", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1) )
                .get(inputs.profileId, new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1) )
}

ConcurrentHashMap getClusterDataRecord(Map inputs = [clusterInt: null , profileId: null , ep: null ]) {
	assert inputs.clusterInt 	instanceof Integer
	assert inputs.profileId 	instanceof String
	assert inputs.ep 			instanceof Integer
	
	return getProfileDataRecord(profileId:(inputs.profileId), ep:(inputs.ep))
                    .get("clusterInfo", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1))
                        .get(inputs.clusterInt, new ConcurrentHashMap<String,ConcurrentHashMap>(16, 0.75, 1))
}

ConcurrentHashMap getClusterDataRecord(String hexCluster, String profileId, Integer ep) {
	Integer clusterInt = Integer.parseInt(hexCluster, 16)
	return getClusterDataRecord(clusterInt:clusterInt, profileId:profileId, ep:ep)
}

Object getLastSentCommand(Map inputs = [clusterInt, Integer commandNum, String profileId, Integer ep]){
	assert inputs.clusterInt instanceof Integer
	assert inputs.commandNum instanceof Integer
	assert inputs.profileId instanceof String
	assert inputs.ep instanceof Integer
	
	return getClusterDataRecord(custerInt:(inputs.clusterInt), profileId:(inputs.profileId), ep:(inputs.ep)).get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).get(inputs.commandNum)
}

Object removeLastSentCommand(Integer clusterInt, Integer commandNum, String profileId, Integer ep){
	return getClusterDataRecord(clusterInt:clusterInt, profileId:profileId, ep:ep).get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).remove(commandNum)
}

Object setLastSentCommand(Integer clusterInt, Integer commandNum, Map commandData, String profileId, Integer ep){
    if (logEnable) log.debug "For Cluster ${clusterInt}, command ${ commandNum}: Storing ${commandData}"
	return getClusterDataRecord(clusterInt:clusterInt, profileId:profileId, ep:ep).get("lastSentCommand", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).put(commandNum, commandData)
}

Object getClusterAttributeValue(Map inputs = [clusterInt: null , attributeInt: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer
	assert inputs.attributeInt instanceof Integer
	assert inputs.profileId instanceof String
	assert inputs.ep	instanceof Integer
	
	return getClusterDataRecord(clusterInt:(inputs.clusterInt), profileId:(inputs.profileId), ep:(inputs.ep)).get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).get(inputs.attributeInt)
}

Object setClusterAttributeValue(Map inputs = [clusterInt: null , attributeInt: null , attributeData: null , profileId: null , ep: null ]){
	assert inputs.clusterInt instanceof Integer
	assert inputs.attributeInt instanceof Integer
	assert inputs.attributeData instanceof String
	assert inputs.profileId instanceof String
	assert inputs.ep	instanceof Integer
	
	return getClusterDataRecord(clusterInt:(inputs.clusterInt), profileId:(inputs.profileId), ep:(inputs.ep)).get("clusterAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).put(inputs.attributeInt, inputs.attributeData)
}
/*
Object getHubitatAttributeValue(String attribute, Integer ep){
    log.debug "getting attribute value for ${attribute}, endpoint ${ep}"
	return getDataRecordForEndpoint(ep).get("hubitatAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).get(attribute)
}
Object setHubitatAttributeValue(String attribute, Object attributeData, Integer ep){
	return getDataRecordForEndpoint(ep).get("hubitatAttributes", new ConcurrentHashMap<String,ConcurrentHashMap>(4, 0.75, 1)).put(attribute, attributeData)
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