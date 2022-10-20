// #include zigbeeTools.formatZigbeeCommands
/*
#include zigbeeTools.globalDataTools
#include zigbeeTools.sendZigbeeAdvancedCommands
#include zigbeeTools.parsePlus
#include zigbeeTools.endpointAndChildDeviceTools

*/

metadata {
    definition (name: "zigbeeTools Simple Test", namespace: "zigbeeTools", author: "ABCDE") {
        // capability "Switch"
        // command "setTestData1"  
        // command "setTestData2"        

        // command "getTestData"   
        // command "dropAndTake"
        // command "formatRawTest"
        // command "logDeviceData"
        // command "removeData"
        // command "simpleDescriptorTest"
        // command "activeEndpointTest"
        // command "buildString"
        // command "reverseit"
        
        /*
        command "addNewChildDevice", [[name:"Device Name*", type:"STRING"], 
                                      [name:"componentDriverName*",type:"ENUM", constraints:(getDriverChoices()) ], 
                                      [name:"Endpoint*",type:"ENUM", constraints:( getAllActiveEndpointsHexList() ), description:"Endpoint HEX String, Use 00 for root (parent) device" ] ]
        command "deleteAllChildDevices"  
        command "testChildAdd"
        command "readChildData"
        */
        command "joinTest"
    }
    /*
    preferences {
        input(name:"logEnable", type:"bool", title:"Enable debug logging", defaultValue:false)
        input(name:"txtEnable", type:"bool", title:"Enable descriptionText logging", defaultValue:true)
        input(name:"refreshEnable", type:"bool", title:"Refresh Status on Hubitat Startup", defaultValue:true)
        input(name:"useOnOffTimer", type:"bool", title:"Use Timer to Turn off after set time", defaultvalue:false)
		input(name:"offTime", type:"number", title: (( useOnOffTimer) ? "Feature Unavailable" : "Turn Off After This Many Seconds:"), defaultValue:300)
    }
    */
}
void joinTest() {
    String test1 = "This is a test"
    List test2 = [] << "This " << "is " << "another " << "test."
    log.debug  test1
    log.debug test2
    log.debug test2.join()
    
}
void testChildAdd(){
    
    Map thisChildItem = [
            namespace: "hubitat",
            type: "Generic Component Motion Sensor",
            childNetworkId: "${device.deviceNetworkId}-ep001.001",
            childName: "Test device",
            endpointId: "ZZ",
            endpointIndexId: "007",
        ]
        
    /*
    ChildDeviceWrapper newChild = addChildDevice(thisChildItem.namespace, thisChildItem.type, childNetworkId, 
                   [name: thisChildItem.childName, isComponent: false, endpointId:thisChildItem.endpointId, 
                    endpointIndexId:thisChildItem.endpointIndexId
                   ]
                  )
    */

    com.hubitat.app.ChildDeviceWrapper Child007 = addChildDevice("hubitat", "Generic Component Motion Sensor", "TestChild-ep007", 
                   [name: "Child.007", isComponent: false , endpointId:"007", endpointIndexId: "007.1" ]  )
    com.hubitat.app.ChildDeviceWrapper Child008 = addChildDevice("hubitat", "Generic Component Motion Sensor", "TestChild-ep008", 
                   [name: "Child.008", isComponent: true , endpointId:"008", endpointIndexId: "008.1"]  )
 
    // //newChild.endpointId = "ZZ"
    // newChild.endpointIndexId = "007"

}
void readChildData() {
    childDevices.each{
        log.debug "1. Device with endpoint ${device.endpointId} has Child ${it.displayName} has endpoint ${it.endpointId} and index ${it.endpointIndexId}"
        log.debug "2. Device with endpoint ${device.endpointId} has Child ${it.displayName} has endpoint ${it.deviceEndpointId} and index ${it.endpointIndexId}"
        log.debug "3. Device with endpoint ${device.endpointId} has Child ${it.displayName} has endpoint ${it.getDataValue("endpointId")} and index ${it.getDataValue("endpointIndexId")}"
        log.debug "4. Is child a component: ${it.isComponent}"

    }
}

void reverseit() {
 log.debug    reverseParametersZCL( ["AB", "12345678", "CD4", "1357"])
}

void buildString(){
     Integer number = 37
    StringBuilder str = new StringBuilder();
    str.append("Test1")
    str << "Test2"
    str << "Test3 ${number}"
    log.debug str
}

void parse(description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    log.debug descMap
    descMap = parsePlus(descMap)
    log.debug "ParsePlus version: " + descMap
}        
        

Void setTestData1() {
    device.endpointId = "01"
   //  device.updateDataValue("endpointId", "01")
    // device.updateDataValue("application", "02")
}
Void setTestData2() {
    device.endpointId

}
void removeData(){
    //device.properties.data.remove("endpointId")
    //device.properties.data.remove("applicaton")
    device.removeDataValue("application")
        device.removeDataValue("endpointId")
}
Void getTestData() {
    log.debug "Device name is ${device.displayName}"
    log.debug "Data #1 is: " + device.getDataValue("networkId")
    log.debug "Data #2 is: " + device.endpointId
    log.debug "Data #3 is: ${device.deviceNetworkId}"
    log.debug "Data #4 is: " + device.getDataValue("application")
}
void logDeviceData(){
    log.debug device.data
    log.debug device.properties
}


void dropAndTake() {
    List<String> data = [ "00", "01", "02", "03", "04"]
    // log.debug "Data: ${data},  data at 0: ${data[0]}, Drop 1: ${data = data.drop(1)}, data at 0 ${data[0]}"
    log.debug "Data: ${data}"
    log.debug "data at 0: ${data[0]}, remove 1: ${data.remove(0)}, data at 0 ${data[0]}"

    
}

void formatRawTest() {
    formatZCLHeader(isClusterSpecific: false , mfrCode: "0103" , direction: 0 , disableDefaultResponse: false , sequenceNo: 27 , commandId: 0x11)
    
}

void activeEndpointTest(){

        log.debug "Command is of type: " + ("${zigbee.swapOctets(device.deviceNetworkId)}").class
    
    sendZDOAdvanced(destinationNetworkId: device.deviceNetworkId,
                     destinationEndpoint: 0 , 
                      sourceEndpoint: 1, 
                      clusterId: 0x0005,
                       profileId: 0x0000,
                      commandId: 0x00,
                    commandPayload: "${zigbee.swapOctets(device.deviceNetworkId)}"  // ZDO Spec Section 2.4.3.1.6, Fig. 2.25
                   )   
}
void simpleDescriptorTest(){
    
    sendZDOAdvanced(destinationNetworkId: device.deviceNetworkId,
                     destinationEndpoint: 0 , 
                      sourceEndpoint: 1, 
                      clusterId: 0x0004,
                       profileId: 0x0000,
                      commandId: 0x00,
                    commandPayload: "${zigbee.swapOctets(device.deviceNetworkId)} 01"  // ZDO Spec Section 2.4.3.1.5, Fig. 2.24
                   )   
}

void componentOn(com.hubitat.app.DeviceWrapper cd){ on(cd:cd) }
void on(Map inputs = [:]){

    

    sendZCLAdvanced(destinationNetworkId: device.deviceNetworkId,
                     destinationEndpoint: Integer.parseInt(((! device.getEndpointId().is( null )) ? device.getEndpointId() : "01"), 16) , 
                      sourceEndpoint: 1, 
                      clusterId: 0x0006,
                    profileId: 0x0104,
                      isClusterSpecific:true, 
                      direction:0, 
                      disableDefaultResponse: false, 
                      sequenceNo: getNextTransactionSequenceNo(), 
                      commandId: 0x01) 
}

void componentOff(com.hubitat.app.DeviceWrapper cd){ off(cd:cd) }
void off(Map inputs = [:]){
	Map params = [cd: null , duration: null , level: null ] << inputs

	String cmd = "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
    hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 

	sendHubCommand(hubAction)
}



