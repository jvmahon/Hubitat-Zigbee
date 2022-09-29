library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Child device Support Functions",
        name: "endpointAndChildDeviceTools",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)

Integer getEndpointId(com.hubitat.app.DeviceWrapper cd) {
    log.debug "Endpoint name is ${cd.displayName}"
    log.debug "EndpointId #1 is: " + cd.endpointId
    log.debug "EndpointId #2 is: " + cd.getDataValue("endpointId")
    log.debug "EndpointId #3 is: " + cd.getDataValue("application")
    return  (cd.getDataValue("endpointId") as Integer) ?: 01
}
Integer getChildSubindex(com.hubitat.app.DeviceWrapper cd) {
    return cd.getDataValue("endpointChildId") as Integer
}

// Get List (possibly multiple) child device for a specific endpoint. Child devices can also be of the form '-ep000' 
// Child devices associated with the root device end in -ep000
List<com.hubitat.app.DeviceWrapper> getChildDeviceListByEndpoint(Map inputs = [ep: null ] ) {
	assert inputs.ep instanceof Integer
	childDevices.findAll{ ( getEndpointId(it)  == (inputs.ep) )}
}

void sendEventsToEndpointByParse(Map inputs = [ events: null , ep: null ]) {
	assert inputs.events instanceof List
	assert inputs.ep instanceof Integer

	List<com.hubitat.app.DeviceWrapper> targetDevices = getChildDeviceListByEndpoint(ep:(inputs.ep))
	if (inputs.ep == 1)  { targetDevices += this }
	// events.each{ setHubitatAttributeValue(it.name, it, ep) }
	targetDevices.each { it.parse(inputs.events) }
}

/////////////////////////////////////////////////////////////////////////
//////        Create and Manage Child Devices for Endpoints       ///////
/////////////////////////////////////////////////////////////////////////

void deleteAllChildDevices() {
    childDevices.each{
        deleteChildDevice(it.deviceNetworkId)
    }       
}

void deleteUnwantedChildDevices() {	
	// Delete child devices that don't use the proper network ID form (parent ID, followed by "-ep" followed by endpoint number).
	getChildDevices()?.each {
		List childNetIdComponents = it.deviceNetworkId.split("-ep")
		if (	(childNetIdComponents[0]?.startsWith(device.deviceNetworkId) ) 
			&& getEndpointId(it) && getChildSubindex(it) ){
			return
		} else {
			deleteChildDevice(it.deviceNetworkId)
		}			
	}
}

void createChildDevices() { // This function is not currently used!	
	// The following Map contains listing of child devices that must exist (if any).
	// format of elements is ...  defaultchildDevices = [1:[[type:'Generic Component Motion Sensor', 'namespace':'hubitat', childName:"Motion Sensor"]]]
	
	Map<List> MapOfchildDevicesByEndpoint = [:]

	MapOfchildDevicesByEndpoint.each{ ep, childDeviceList ->		
		childDeviceList.eachWithIndex { thisChildItem, index ->
			String childNetworkId = getChildNetID(ep, index)
			com.hubitat.app.DeviceWrapper cd = getChildDevice(childNetworkId)
			if (cd.is( null )) {
				log.info "Device ${device.displayName}: creating child device: ${childNetworkId} with driver ${thisChildItem.type} and namespace: ${thisChildItem.namespace}."
				
				addChildDevice(thisChildItem.namespace, thisChildItem.type, childNetworkId, [name: thisChildItem.childName ?: device.displayName, isComponent: false, endpointId:("${ep}".padLeft(3,"0")), endpointChildId:("${index}".padLeft(3,"0"))])
			} 
		}
	}
}
//
		command "addNewChildDevice", [[name:"Device Name*", type:"STRING"], 
                                      [name:"componentDriverName*",type:"ENUM", constraints:(getDriverChoices()) ], 
                                      [name:"Endpoint*",type:"NUMBER", description:"Endpoint Number, Use 0 for root (parent) device" ] ]

//

List getDriverChoices() {
	// Returns the name of the generic component drivers with their namespace listed in parenthesis
    // log.debug getInstalledDrivers().findAll{it.name.toLowerCase().startsWith("generic component")}.collect{ "${it.name} (${it.namespace})"}.sort()
    return getInstalledDrivers().findAll{it.name.toLowerCase().startsWith("generic component")}.collect{ "${it.name} (${it.namespace})"}.sort()
}

String formatChildNetID(Integer ep, Integer endpointSubindex){
	// Formats the zigbee Dvevice Network Id for the child device.
	// This library allows each endpoint to have multiple child devices which are themselves identified by an endpointSubIndex
	if ((ep > 240) || (endpointSubindex >> 999) ) {
		log.error "Device ${device.displayName}: attempted to create child device by endpoint or endpointSubindex number is too high. Function getChildNetID"
		return
		}
	return "${device.deviceNetworkId}-ep${"${ep}".padLeft(3, "0")}.${"${endpointSubindex}".padLeft(3, "0") }"
}

Map getNextAvailableChildInfo(Integer ep) {
	// Library allows each endpoint to have multiple child devices, each identified by a subindex number
	// This code determines the next available endpoint sub-index when a child device is added to an endpoint.
	Integer candidateChildDeviceSubindex = 1
	String candidateChildDeviceNetId = formatChildNetID(ep, candidateChildDeviceSubindex)

	// keep checking - does a child device already exist for a candidate ep / index. If so, try the next subindex. Stop when you find a child device does not exist for a candidate ep / index pair.
	while ( getChildDevice(candidateChildDeviceNetId) ) { 
			candidateChildDeviceSubindex ++ 
			candidateChildDeviceNetId = formatChildNetID(ep, candidateChildDeviceSubindex)
		}
	return [networkId:candidateChildDeviceNetId, ep:ep, subindex:candidateChildDeviceSubindex]
}

void addNewChildDevice(String newChildName, String componentDriverName, ep) {
    if (logEnable) { log.debug "Device ${device.displayName}: Ading child device named ${newChildName} using driver component type ${componentDriverName} for endpoint ${ep}" }
	Map thisDriver = getInstalledDrivers().find{ "${it.name} (${it.namespace})" == componentDriverName }
	
	if(!thisDriver) {
	    log.error "Device ${device.displayName}: attempting to add a child device using a non-existing component driver ${componentDriverName}."
	    return
	}

    Map nextChildInfo = getNextAvailableChildInfo((int) (ep ?: 1)) // ep should default to 0 for Zwave and 1 for Zigbee
    String endpointId = "${nextChildInfo.ep}".padLeft(3,"0")
	String endpointChildId = "${nextChildInfo.subindex}".padLeft(3,"0")
	com.hubitat.app.DeviceWrapper cd = addChildDevice(thisDriver.namespace, thisDriver.name, nextChildInfo.networkId, [name: newChildName, isComponent: false, endpointId:endpointId, endpointChildId:endpointChildId ])
}

/////////////////////////////////////////////////////////////////
