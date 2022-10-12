metadata {
    definition (name: "Child Test", namespace: "ABC", author: "ABCDE") {
        command "deleteAllChildDevices"  
        command "ChildTestAdd"
        command "readChildData"
    }
}

void ChildTestAdd(){
    // Usually would check if the child already exists, but this is only testing code
    com.hubitat.app.ChildDeviceWrapper child007 = addChildDevice("hubitat", "Generic Component Motion Sensor", "TestChild-ep007", 
                   [name: "Child.007", isComponent: false , endpointId:"007", endpointIndexId: "007.1" ]  )
    com.hubitat.app.ChildDeviceWrapper child008 = addChildDevice("hubitat", "Generic Component Motion Sensor", "TestChild-ep008", 
                   [name: "Child.008", isComponent: true]  )
    
    child008.updateDataValue("endpointId", "008" )
    child008.updateDataValue("endpointIndexId", "008.1" )
}

void readChildData() {
    childDevices.each{
        log.debug "1. Device with endpoint ${device.endpointId} has Child ${it.displayName} with endpointId ${it.endpointId} and endpointIndexId ${it.endpointIndexId}, where isComponent is: ${it.isComponent}"
        log.debug "2. Device with endpoint ${device.endpointId} has Child ${it.displayName} with endpointId ${it.getDataValue("endpointId")} and endpointIndexId ${it.getDataValue("endpointIndexId")}, where isComponent is ${it.getDataValue("isComponent")}"

    }
}

void deleteAllChildDevices() {
    childDevices.each{
        deleteChildDevice(it.deviceNetworkId)
    }       
}
