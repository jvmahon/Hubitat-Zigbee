import groovy.transform.Field
#include zigbeeTools.parsePlus
#include zigbeeTools.sendZigbeeAdvancedCommands

#include zigbeeTools.globalDataTools
#include zigbeeTools.endpointAndChildDeviceTools
#include zigbeeTools.commonClusterProcessing
#include zigbeeTools.basicCluster0x0000

#include zigbeeTools.zigbeeZDOProfile0x0000
#include zigbeeTools.commonDriverMethods

metadata {
    definition (name: "zigbeeTools Basic Cluster 0000 Test Driver", namespace: "zigbeeTools", author: "James Mahon") {
        capability "Configuration"		
		capability "Initialize"
		capability "Refresh"
									  
        command "setClusterAttribute", [	[name:"clusterId*", type:"STRING"],
									[name:"attributeId*", type:"STRING"], 
									[name:"hexValue*", type:"STRING"] ]
									
        command "readClusterAttribute", [	[name:"clusterId*", type:"STRING"],
									[name:"attributeId*", type:"STRING"] ] 
    }
    
    preferences {
        input(name:"logEnable", type:"bool", title:"Enable debug logging", defaultValue:false)
        input(name:"txtEnable", type:"bool", title:"Enable descriptionText logging", defaultValue:true)
    }
}


