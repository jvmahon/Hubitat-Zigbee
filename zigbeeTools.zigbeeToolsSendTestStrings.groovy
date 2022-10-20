import groovy.transform.Field


metadata {
    definition (name: "zigbeeTools Send Test Strings", namespace: "zigbeeTools", author: "James Mahon") {
        capability "Actuator"
        capability "Switch"
        

        
        command "sendString", [[name:"Command*", type:"STRING"] ]
        
    }

}

void sendString(command, sequenceNum) {
    log.debug "Function sendString sending string:\n${command} with sequenceNum ${sequenceNum as Integer}"
        List<String> cmds = []
	cmds += command
    log.debug cmds
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    
}

// This parser handles the Zigbee events.
void parse(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    log.debug "Received message: ${description} producing map ${descMap}"

}

void on(Map inputs = [:]){
	Map params = [cd: null , duration: null , level: null ] << inputs
	
    List<String> cmds = []
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}"
    log.debug zigbee.on(0)
    log.debug cmds
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void off(Map inputs = [:]){
	Map params = [cd: null , duration: null , level: null ] << inputs
    List<String> cmds = []
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE)) 
}
