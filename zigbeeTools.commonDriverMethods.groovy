library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Methods Common to Zigbee Drivers",
        name: "commonDriverMethods",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

void setClusterAttribute(clusterId, attributeId, hexValue) { setClusterAttribute(clusterId:clusterId , attributeId:attributeId, hexValue:hexValue ) }
void setClusterAttribute(Map params = [:]) {
	Map inputs = [clusterId: null , attributeId: null , hexValue: null ] << params
	String attributeDataType = getClusterAttributeDataType( *:inputs)
	assert attributeDataType : "Cannot get attribute data. May occur if the driver has not been configured or initialized. Manually click on configure or initialize, or reboot Hubitat"
	
	sendZCLAdvanced(
		clusterId: inputs.clusterId ,
		destinationNetworkId: device.deviceNetworkId ,		
		destinationEndpoint:  getEndpointId(device) ,
		commandId: 	0x02 ,		// 0x02 = Write Attributes, ZCL TAble 2-3
		commandPayload: [inputs.attributeId, attributeDataType, inputs.hexValue]
	)
}

void readClusterAttribute(clusterId, attributeId) {readClusterAttribute(clusterId:clusterId, attributeId:attributeId)}
void readClusterAttribute(Map params = [:]) {
	Map inputs = [clusterId: null , attributeId: null ] << params
	
	sendZCLAdvanced(
		clusterId: inputs.clusterId ,
		destinationNetworkId: device.deviceNetworkId ,		
		destinationEndpoint:  getEndpointId(device) ,	
		commandId: 	0x00 ,		// Read Attributres, ZCL Table 2-3
		commandPayload: [inputs.attributeId] 
	)
}
// End of Debugging Functions


// This parser handles Hubitat event messages (not raw strings from the device). This parser operates  like the "parse" routine commonly found in subcomponent drivers. Use parse to distribute Hubitat events instead of sendEvent.
// Accepts a list of events -- i.e., may be more than one, and only sends those events which there is an attribute in the driver.
void parse(List<Map> events) {
    events.findAll{device.hasAttribute(it.name)}?.each {
            if (txtEnable && it.descriptionText) log.info it.descriptionText
            sendEvent(it)
    }
}

// This parser handles the Zigbee event message originating from Hubitat.
// It has a simple strucure of trying to call a routine in each profile / cluster supported by this driver model
// The routines are there if the associated library has been included.
void parse(String description) {
    if (logEnable) log.debug "common method parse received description string ${description}"
	Map descMap = parsePlus(zigbee.parseDescriptionAsMap(description))
	if (logEnable) log.debug "For input ${description}, produced parse descMap value of ${descMap.inspect()}"
	if (descMap.is( null ) ) return
	
    try { // As of Hubitat 2.3.3, assert checking needs to be done in a try block in order to properly format the assertion output by inserting a HTML <pre>. Request made to Hubitat to fix this!
		if (descMap.profileId == "0000") { //zdo Profile 0x0000.
			processClusterResponse0x0000_xxxx(descMap) // For any of the ZDO clusters
			return
		} else {   // Assume everything else is for the home automation profile 0x0104
			
			if (descMap.profileId && descMap.profileId != "0104") {
				log.warn "Received a response with a profile ID of ${descMap.profileId}. Was expecting a profileId of "0104". Message was ${descMap.inspect()}.  Attempting to process anyway."
			}
			
			processCommonClusterResponses0x0104(descMap)
			
			// If the relevant cluster library has been included, then call it!
			if (processClusterResponse0x0104_0000) processClusterResponse0x0104_0000(descMap) // Basic
			if (processClusterResponse0x0104_0003) processClusterResponse0x0104_0003(descMap) // Identify
			if (processClusterResponse0x0104_0004) processClusterResponse0x0104_0004(descMap) // Groups
			if (processClusterResponse0x0104_0005) processClusterResponse0x0104_0005(descMap) // Scenes
			if (processClusterResponse0x0104_0006) processClusterResponse0x0104_0006(descMap) // OnOff
			if (processClusterResponse0x0104_0007) processClusterResponse0x0104_0007(descMap) // OnOff Configuration
			if (processClusterResponse0x0104_0008) processClusterResponse0x0104_0008(descMap) // Level
			if (processClusterResponse0x0104_0300) processClusterResponse0x0104_0300(descMap) // Color Control
		}
    } catch (AssertionError e) {
        log.error "<pre>${e}"
		throw(e)
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
    String cmd = (getDataValue("manufacturer") == "Philips") ? zigbee.updateFirmware(manufacturer:0x100B) : zigbee.updateFirmware()
	sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE))
}

void clearStoredData() {
    state.clear()
}
void configure() {
    try { 
		if (txtEnable) log.info "Configuring device ${device.displayName}..."
		clearStoredData() // Configure resets and re-reads the stored devicce data.
		getActiveEndpointsZDO()
		if (configure0x0104_0000) configure0x0104_0000() // Basic
		if (configure0x0104_0003) configure0x0104_0003() // Identify
		if (configure0x0104_0004) configure0x0104_0004() // Groups
		if (configure0x0104_0005) configure0x0104_0005() // Scenes
		if (configure0x0104_0006) configure0x0104_0006() // OnOff
		if (configure0x0104_0007) configure0x0104_0007() // Switch Configuration
		if (configure0x0104_0008) configure0x0104_0008() // Level
		if (configure0x0104_0300) configure0x0104_0300() // Color Control
    } catch (AssertionError e) {
        log.error "<pre>${e}"
		throw(e)
    }
}

void initializeStage2(){
        try { 
		if (txtEnable) log.info "Initializing device ${device.displayName}... "
		if (refreshEnable == false) return
		if (initialize0x0104_0000) initialize0x0104_0000() // Basic
		if (initialize0x0104_0003) initialize0x0104_0003() // Identify
		if (initialize0x0104_0004) initialize0x0104_0004() // Groups
		if (initialize0x0104_0005) initialize0x0104_0005() // Scenes
		if (initialize0x0104_0006) initialize0x0104_0006() // OnOff
		if (initialize0x0104_0007) initialize0x0104_0007() // Switch Configuration
		if (initialize0x0104_0008) initialize0x0104_0008() // Level
		if (initialize0x0104_0300) initialize0x0104_0300() // Color Control
    } catch (AssertionError e) {
        log.error "<pre>${e}"
		throw(e)
    }
}

void initialize() {
    if (state.deviceStaticData) {
        // If data about the device was previously gathered, reload it and then initialize.
        reloadStoredStaticData()
        initializeStage2()
    } else {
        // Else, have to gather the data! Give 5 seconds for that to complete (should take much less time!), the initialize.
        configure()
        runIn(5, initializeStage2)
    }
}

void componentRefresh(cd) { refresh(getEndpointId(cd)) }
void refresh(String ep = getEndpointId(device)) {
    try { 
		if (txtEnable) log.info "Refreshing device ${device.displayName}..."
		if (refresh0x0104_0000) refresh0x0104_0000(ep) // Basic
		if (refresh0x0104_0003) refresh0x0104_0003(ep) // Identify
		if (refresh0x0104_0004) refresh0x0104_0004(ep) // Groups
		if (refresh0x0104_0005) refresh0x0104_0005(ep) // Scenes
		if (refresh0x0104_0006) refresh0x0104_0006(ep) // OnOff
		if (refresh0x0104_0007) refresh0x0104_0007(ep) // Switch Configuration
		if (refresh0x0104_0008) refresh0x0104_0008(ep) // Level
		if (refresh0x0104_0300) refresh0x0104_0300(ep) // Color Control
    } catch (AssertionError e) {
        log.error "<pre>${e}"
		throw(e)
    }
}

void unbindAll(){
    try { 
		if (unbind0x0104_0000) unbind0x0104_0000() // Basic
		if (unbind0x0104_0003) unbind0x0104_0003() // Identify
		if (unbind0x0104_0004) unbind0x0104_0004() // Groups - currently no binding, but call it anyway for consistency
		if (unbind0x0104_0005) unbind0x0104_0005() // Scenes
		if (unbind0x0104_0006) unbind0x0104_0006() // OnOff
		if (unbind0x0104_0007) unbind0x0104_0007() // Switch Configuraiton
		if (unbind0x0104_0008) unbind0x0104_0008() // Level
		if (unbind0x0104_0300) unbind0x0104_0300() // Color Control   
    } catch (AssertionError e) {
        log.error "<pre>${e}"
		throw(e)
    }
}

// debugging Functions
void showStoredData(){
    showFullGlobalDataRecord() // From the zigbee.globalDataTools.groovy library
}

