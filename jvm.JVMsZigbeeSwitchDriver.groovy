import groovy.transform.Field
#include zigbeeTools.globalDataTools
#include zigbeeTools.endpointAndChildDeviceTools
#include zigbeeTools.groupCluster0x0004
#include zigbeeTools.OnOffCluster0x0006
// #include zigbeeTools.levelCluster0x0008
// #include zigbeeTools.ColorCluster0x0300
#include zigbeeTools.zigbeeZDOProfile0x0000

metadata {
    definition (name: "JVM's Zigbee Switch Driver", namespace: "jvm", author: "James Mahon") {
        capability "Actuator"
        capability "Configuration"		
		capability "Initialize"
		capability "Refresh"
		
        capability "Switch"
        // capability "SwitchLevel"
        // capability "ChangeLevel"
        // capability "Level Preset"

		// capability "Bulb"
        // capability "Color Temperature"
        // capability "ColorControl"
        // capability "ColorMode"
        // capability "Color Preset"

        command "updateFirmware"
        
        attribute "OnOffTransitionTime", "number"
        attribute "OnTransitionTime", "number"
        attribute "OffTransitionTime", "number"
        attribute "colorCapabilities", "string"
        
        command "addNewChildDevice", [[name:"Device Name*", type:"STRING"], 
                                      [name:"componentDriverName*",type:"ENUM", constraints:(getDriverChoices()) ], 
                                      [name:"Endpoint*",type:"NUMBER", description:"Endpoint Number, Use 0 for root (parent) device" ] ]
        command "deleteAllChildDevices"  
        
        // For debugging purposes
        command "sendString", [[name:"Command*", type:"STRING"] ]
        command "showStoredData" // For debugging purposes
        command "unbindAll" // For Debugging Purposes
    }
    preferences {
        input(name:"logEnable", type:"bool", title:"Enable debug logging", defaultValue:false)
        input(name:"txtEnable", type:"bool", title:"Enable descriptionText logging", defaultValue:true)
        input(name:"refreshEnable", type:"bool", title:"Refresh Status on Hubitat Startup", defaultValue:true)
    }
}

void sendString(command, sequenceNum) {
    log.debug "Function sendString sending string:\n${command} with sequenceNum ${sequenceNum as Integer}"
    List<String> cmds = []
	cmds += command
    log.debug cmds
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void showStoredData(){
    log.debug getDataRecordByNetworkId().inspect()
}

void unbindAll(){
    if (unbind0x0104_0004) unbind0x0104_0004() // Groups - currently no binding, but call it anyway for consistency
	if (unbind0x0104_0006) unbind0x0104_0006() // OnOff
	if (unbind0x0104_0008) unbind0x0104_0008() // Level
	if (unbind0x0104_0300) unbind0x0104_0300() // Color Control   
}

// This parser handles Hubitat event messages (not raw strings from the device). This parser operates  like the "parse" routine commonly found in subcomponent drivers.
void parse(List<Map> events) {
    events.findAll{device.hasAttribute(it.name)}?.each {
            if (txtEnable && it.descriptionText) log.info it.descriptionText
            sendEvent(it)
    }
}

// This parser handles the Zigbee events.
void parse(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "In main parse routine, received message: ${descMap}"
    
    if (descMap.profileId == "0000") { //zdo Profile 0x0000.
        processClusterResponse0x0000_xxxx(descMap) // For any of the ZDO clusters
        return
    } else {   // Assume everything else is for the home automation profile 0x0104
		// if (processClusterResponse0x0104_0000) processClusterResponse0x0104_0000(descMap) // Basic
		// if (processClusterResponse0x0104_0001) processClusterResponse0x0104_0001(descMap) // Power Configuration	
		// if (processClusterResponse0x0104_0002) processClusterResponse0x0104_0002(descMap) // Device Temperature	
		// if (processClusterResponse0x0104_0003) processClusterResponse0x0104_0003(descMap) // Identify
		if (processClusterResponse0x0104_0004) processClusterResponse0x0104_0004(descMap) // Groups
		// if (processClusterResponse0x0104_0005) processClusterResponse0x0104_0005(descMap) // Scenes
		if (processClusterResponse0x0104_0006) processClusterResponse0x0104_0006(descMap) // OnOff
		// if (processClusterResponse0x0104_0007) processClusterResponse0x0104_0007(descMap) // OnOff Configuration
		if (processClusterResponse0x0104_0008) processClusterResponse0x0104_0008(descMap) // Level
		if (processClusterResponse0x0104_0300) processClusterResponse0x0104_0300(descMap) // Color Control
    }
}

void updated(){
    if (txtEnable) log.info "Processing Preference changes for ${device.displayName}..."
    if (logEnable) {
		log.info "For device ${device.displayName}: Debug logging enabled for 30 minutes"
		runIn(1800,logsOff)
	}
}

void logsOff(){
    if (txtEnable) "For device ${device.displayName}: Turning off Debug logging."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void updateFirmware() {
    List<String> cmds = []
    cmds += (getDataValue("manufacturer") == "Philips") ? zigbee.updateFirmware(manufacturer:0x100B) : zigbee.updateFirmware()
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void configure() {
    if (txtEnable) log.info "Configuring device ${device.displayName}..."
    state.clear()
	if (configure0x0104_0004) configure0x0104_0004() // Groups
	if (configure0x0104_0006) configure0x0104_0006() // OnOff
	if (configure0x0104_0008) configure0x0104_0008() // Level
	if (configure0x0104_0300) configure0x0104_0300() // Color Control
}

void initialize() {
    if (txtEnable) log.info "Initializing device ${device.displayName}... "
    if (refreshEnable == false) return
	if (initialize0x0104_0004) initialize0x0104_0004() // Groups
	if (initialize0x0104_0006) initialize0x0104_0006() // OnOff
	if (initialize0x0104_0008) initialize0x0104_0008() // Level
	if (initialize0x0104_0300) initialize0x0104_0300() // Color Control
}

void componentRefresh(cd) { refresh(getEndpointId(cd)) }
void refresh(Integer ep = getEndpointId(device)) {
    if (txtEnable) log.info "Refreshing device ${device.displayName}..."
	if (refresh0x0104_0004) refresh0x0104_0004(ep) // Groups
	if (refresh0x0104_0006) refresh0x0104_0006(ep) // OnOff
	if (refresh0x0104_0008) refresh0x0104_0008(ep) // Level
	if (refresh0x0104_0300) refresh0x0104_0300(ep) // Color Control
}
