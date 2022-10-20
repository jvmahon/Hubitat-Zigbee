// Routines to create and manage child devices. 
//	Insert the following command into your driver code preference section to allow the creation of child devices.
/*
	command "addNewChildDevice", [[name:"Device Name*", type:"STRING"], 
								  [name:"componentDriverName*",type:"ENUM", constraints:(getDriverChoices()) ], 
								  [name:"Endpoint*",type:"STRING", description:"Endpoint Number as a Hex String" ] ]
*/

library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Child device Support Functions",
        name: "endpointAndChildDeviceTools",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)

// This next function will generally override device.getEndpointId()
String getEndpointId(com.hubitat.app.DeviceWrapper thisDevice) {
	String rValue =  thisDevice?.getDataValue("endpointId") ?:   thisDevice?.endpointId 
    if (rValue.is( null )) { 
            log.error "Device ${thisDevice.displayName} does not have a defined endpointId. Fix this!"
            rValue == "Endpoint Undefined - Reboot or Click Configure" 
    }
	return rValue.padLeft(2, "0")
}

// Driver allows each endpoint to have multiple copies of the child device.
// This could be used where, for example, you want different copies of the device to appear in different rooms
// Useful in Homebridge to have a device that controls multiple rooms be in multiple rooms.
// To allow this, each child device has an endpointId as well as subindexes that differ for the copies.
String getChildSubindex(com.hubitat.app.DeviceWrapper thisDevice) {
	String rValue = thisDevice?.getDataValue("endpointSubindexId")
    if (rValue.is( null )) { 
            log.error "Device ${thisDevice.displayName} does not have a defined endpointSubindexId. Fix this!" 
            rValue == "endpointSubindexId is undefined" 
    }
	return rValue.padLeft(3, "0")
}

// Get all the child devices for a specified endpoint.
List<com.hubitat.app.DeviceWrapper> getChildDeviceListByEndpoint(Map inputs = [ep: null ] ) {
	// assert inputs.ep instanceof String // Assert unnecessary - gets checked in calling function!
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2 : "Endpoint must be specified as a 2 Hex Character String"
	childDevices.findAll{ ( getEndpointId(it) == (inputs.ep) )}
}

// Uses a parse routine to manage sendEvent message distribution
// The passing of a sendEvent event to a parse routine is a technique used in Hubitat's Generic Component drivers, so its adopted here.
void sendEventsToEndpointByParse(Map inputs = [ events: null , ep: null ]) {
	assert inputs.events instanceof List
	assert inputs.ep instanceof String || inputs.ep instanceof GString
	assert inputs.ep.length() == 2 : "Endpoint must be specified as a 2 Hex Character String"
	
	List<com.hubitat.app.DeviceWrapper> targetDevices = getChildDeviceListByEndpoint(ep:(inputs.ep))
	if (inputs.ep == getEndpointId(device) )  { targetDevices += this }

	targetDevices.each { it.parse(inputs.events) }
}

/////////////////////////////////////////////////////////////////////////
//////        Create and Manage Child Devices for Endpoints       ///////
/////////////////////////////////////////////////////////////////////////

// Basically, a debugging routine - if the "child" was created by another driver and doesn't have the proper formatting of endpointId and endpointSubindexId, delete that child.
void deleteUnwantedChildDevices() {	
	// Delete child devices that doesn't use the proper ID form (having both an endpointId and endpointSubindexId ).
	getChildDevices()?.each {
		if ( getEndpointId(it) && getChildSubindex(it) ){
			return
		} else {
			deleteChildDevice(it.deviceNetworkId)
		}			
	}
}

// Get the generic drivers that Hubitat supports.
List getDriverChoices() {
	// Returns the name of the generic component drivers with their namespace listed in parenthesis
	List rValue = getInstalledDrivers()
					.findAll{ it.name.toLowerCase().startsWith("generic component")}
						.collect{ "${it.name} (${it.namespace})"}
							.sort()
	return rValue
}

Integer getNextChildSubindex(String ep) {

	List<Integer> inUseSubindexes = getChildDeviceListByEndpoint(ep:ep)
										.collect{ Integer.parseInt( getChildSubindex(it), 10) }
	Integer nextSubindex = 1
	while ( inUseSubindexes.contains(nextSubindex) ) { nextSubindex ++  }
	return nextSubindex
}

void addNewChildDevice(String newChildName, String componentDriverName, String ep) {
    if (logEnable) { log.debug "Device ${device.displayName}: Ading child device named ${newChildName} using driver component type ${componentDriverName} for endpoint ${ep}" }
	
	assert ep.length() == 2 : "Error adding endpoint. Endpoint must be a 2 character Hex string. You tried to add an endpoint of length: ${ep.length()}."

	assert getAllActiveEndpointsHexList().contains(ep) : "Error adding endpoint. You tried to add an endpoint ${ep} which is not supported by the device. Supported endpoints are limited to ${getAllActiveEndpointsHexList()}."
	
	Map thisDriver = getInstalledDrivers().find{ "${it.name} (${it.namespace})" == componentDriverName }
	
	if(!thisDriver) {
	    log.error "Device ${device.displayName}: attempting to add a child device using a non-existing component driver ${componentDriverName}."
	    return
	}

    Integer newIndex = getNextChildSubindex(ep)
    String childSubindex = "${newIndex}".padLeft(3, "0")
	String childNetworkId = device.deviceNetworkId + "-ep0x${ep}.${childSubindex}"

	com.hubitat.app.DeviceWrapper cd = addChildDevice(thisDriver.namespace, thisDriver.name, childNetworkId, 
												[	name: newChildName, 
													isComponent: false , 
													endpointId:ep, 
													endpointSubindexId: childSubindex
                                                ])
}

/////////////////////////////////////////////////////////////////
