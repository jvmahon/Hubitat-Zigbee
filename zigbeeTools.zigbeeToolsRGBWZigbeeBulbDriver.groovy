import groovy.transform.Field
#include zigbeeTools.parsePlus
#include zigbeeTools.sendZigbeeAdvancedCommands
#include zigbeeTools.globalDataTools
#include zigbeeTools.endpointAndChildDeviceTools

#include zigbeeTools.commonClusterProcessing
#include zigbeeTools.commonDriverMethods
#include zigbeeTools.zigbeeZDOProfile0x0000
// The following libraries are included depending on what the device is.
// You should be able to simply include them all if you wish (the code will be a little longer, but it should not have any material impact on performance.

// #include zigbeeTools.identifyCluster0x0003
#include zigbeeTools.groupCluster0x0004
#include zigbeeTools.OnOffCluster0x0006
// #include zigbeeTools.switchConfiguration0x0007
#include zigbeeTools.levelCluster0x0008
#include zigbeeTools.ColorCluster0x0300

metadata {
    definition (name: "zigbeeTools RGBW Zigbee Bulb Driver", namespace: "zigbeeTools", author: "James Mahon") {
        capability "Actuator"
        capability "Configuration"		
		capability "Initialize"
		capability "Refresh"
		capability "PowerSource"
		
        capability "Switch"
        capability "SwitchLevel"

		capability "Bulb"
        // capability "Color Temperature"
        capability "ColorControl"
        capability "ColorMode"

        command "updateFirmware"
        
        attribute "OnOffTransitionTime", "number"
        attribute "OnTransitionTime", "number"
        attribute "OffTransitionTime", "number"
        attribute "colorCapabilities", "string"
        
        command "addNewChildDevice", [[name:"Device Name*", type:"STRING"], 
                                      [name:"componentDriverName*",type:"ENUM", constraints:(getDriverChoices()) ], 
                                      [name:"Endpoint*",type:"STRING", description:"Endpoint HEX String, Use 00 for root (parent) device" ] ]
									  
        command "setClusterAttribute", [	[name:"clusterId*", type:"STRING"],
									[name:"attributeId*", type:"STRING"], 
									[name:"hexValue*", type:"STRING"] ]
        command "readClusterAttribute", [	[name:"clusterId*", type:"STRING"],
									[name:"attributeId*", type:"STRING"] ]									
    }
    preferences {
        input(name:"logEnable", type:"bool", title:"Enable debug logging", defaultValue:false)
        input(name:"txtEnable", type:"bool", title:"Enable descriptionText logging", defaultValue:true)
        input(name:"refreshEnable", type:"bool", title:"Refresh Status on Hubitat Startup", defaultValue:true)
        if (supportsOffTimer()) input(name:"useOnOffTimer", type:"bool", title: "Use Timer to Turn off after set time", defaultvalue:false)
        if (useOnOffTimer && supportsOffTimer() ) input(name:"offTime", type:"number", title:"Turn Off After This Many Seconds:", defaultValue:300)
    }
}

Boolean supportsOffTimer(){
   return getDataValue("supportsOnOffTimer") == "true"
}