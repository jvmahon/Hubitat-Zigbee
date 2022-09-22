library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Handle Bindings in ZDO Profile 0x0000",
        name: "zigbeeBindingsProfile0x0000",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/HubitatDriverTools",
		version: "0.0.1"
)

void processClusterResponse0x0000_xxxx(Map descMap) {
	if (logEnable) log.debug "A zigbee binding message was received: ${descMap}"
   
    // Should processing be rejected?
	if (descMap.profileId && (descMap.profileId != "0000")) return 
	// End of rejection reasons!	
    
	switch (descMap.clusterId) {
        case "0013" : //"device announce"
            configure()        
			break
		case "8005" : //endpoint response
		case "8004" : //simple descriptor response
		case "8034" : //leave response
		case "8021" : //bind response
		case "8022" : //unbind request
		default :
			if (logEnable) log.debug "skipped zdo binding message:${descMap.clusterId}"
	}
}
