library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Formats Zigbee Commands",
        name: "sendZigbeeAdvancedCommands",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.2"
)
/*
This library implements functions for sending Zigbee Cluster Library (ZCL) and Zigbee Device Object (ZDO) commands.

References:
ZCL :  Zigbee Cluster Library Specification Document 07-5123 Revision 8 (Profile 0104 Spec)
ZDO :  Zigbee Specification Document 05-3474-21 (Profile 0000 Spec)
*/

import java.util.concurrent.* 
import java.util.concurrent.atomic.*
import groovy.transform.Field

@Field static ConcurrentHashMap deviceCounterStorage = new ConcurrentHashMap(16, 0.75, 1)

// The next function created transaction sequence numbers from 0 - 255, then starts over.
// Concurrency-safe.
Integer getNextTransactionSequenceNo() {
	AtomicInteger counter = deviceCounterStorage.get(device.deviceNetworkId, new AtomicInteger(0))
	
	while(true) {
		int existingValue = counter.get()
		int newValue = ( existingValue + 1 ) % 256
		if (counter.compareAndSet(existingValue, newValue)) { return newValue }
	}
}


private String byteReverseParameters(List<String> parameters) {
	StringBuilder rStr = new StringBuilder(128)
	
	for (hexString in parameters) {
		if (hexString.length() % 2) throw new Exception("In method reverseParametersZCL, trying to reverse a hex string that is not an even number of characters in length. Error in Hex String: ${hexString}, All method parameters were ${parameters}.")
		
		for(Integer i = hexString.length() -1 ; i > 0 ; i -= 2) {
			rStr << hexString[i-1..i]
		}	
	}
	return rStr
}


/**
A function for sending any of the Zigbee command for commands specfied in the Zigbee Cluster Library Specification Document 07-5123 Revision 8 ("ZCL")
*/
void sendZCLAdvanced(Map params = [:] ) {
	Map inputs = [
		destinationNetworkId: device.deviceNetworkId ,  // A 4 hex character data string specifying the network address of the node you are sending to. Optional - defaults to device.deviceNetworkId.
		
		destinationEndpoint:  Integer.parseInt(device.endpointId,16)  ,  // An Integer or 2 hex character string identifying the destination endpoint. Optional - defaults to device.endpointId
		
		sourceEndpoint: 1 ,         // An Integer or 2 hex character string identifying the source endpoint. Generally, this is the Hubitat Hub's endpoint. Optional - defaults to 1
		clusterId: null ,           // An Integer or 4 hex character string identifying the cluster ID to be operated on. Mandatory.
		
		isClusterSpecific: false ,     // A boolean value that determines whether a "global" or "cluster specific" command is to be executed. This sets the ZCL header bit. Optional. Defaults to "false" -- send a "global" command.
		
		mfrCode: null ,             // An Integer or 4 hex character string identifying the manufacturere code for proprietary extensions. Optional. Not specified unless using a proprietary extension. If specified, then the manufacture bit in the ZCL header will be set to 1.  See ZCL § 2.4.1.2
		
		direction: 0 ,                 // Direction Sub-field bit. 1 indicates the command is being sent from the node. 0 indicates it is being sent from Hubitat. Optional - defaults to 0 (there does not appear to be any use for non-zero values). ZCL § 2.4.1.1.3
		
		disableDefaultResponse: false , // Sets the disable Default Response bit in the ZCL header. Defaults to "false". Generally, you want to leave this as false so you do receive the "default" response. See ZCL § 2.4.1.1.3
		
		sequenceNo: null ,         // Specified as an integer. The transactions equence number is a number between 0 - 255. A different transaction sequence should be used for each new transaction. Optiona. If not specified, this is generated using the getNextTransactionSequenceNo() methd. See ZCL § 2.4.1.3
		commandId: null,             // And integer between 0 and 255 representing the global or local command being sent. 
		
		commandPayload: null,     // A string representing the payload values (if any) for the specific commandIds. Hex strings greater than two hex values must be specified in pair-reversed form. So, e.g., the value 0x1234 is entered as "3412"
		
		profileId: 0x0104        // An integer or string representing the Profile ID for the cluster being acted on. This is currently ignored as this function currenly only supports cluster "0104"
	]
		
	try { // As of Hubitat 2.3.3, assert checking needs to be done in a try block in order to properly format the assertion output by inserting a HTML <pre>. Request made to Hubitat to fix this!
        // This first check is to make sure you got all the parameter names correct. This will flag any mistyped parameter names.
		assert inputs.keySet().containsAll(params.keySet()) // checks that all user-specified parameters use permitted labels.
        
		// The inputs map sets up the defaults. Then override those with whatever the user supplied.
		inputs << params
		
		// Type check every user input!
		assert (inputs.destinationNetworkId instanceof String || inputs.destinationNetworkId instanceof GString) && (inputs.destinationNetworkId.length() == 4)
		assert inputs.destinationEndpoint instanceof Integer || inputs.destinationEndpoint instanceof String || inputs.destinationEndpoint instanceof GString
		assert inputs.sourceEndpoint instanceof Integer || inputs.sourceEndpoint instanceof String || inputs.sourceEndpoint instanceof GString
		assert inputs.profileId instanceof Integer || (inputs.profileId instanceof String || inputs.profileId instanceof GString)// Not octet reversed!
		assert inputs.clusterId instanceof Integer || inputs.clusterId instanceof String || inputs.clusterId instanceof GString
		assert inputs.commandPayload instanceof String || inputs.commandPayload instanceof GString || inputs.commandPayload.is(null)
		assert inputs.isClusterSpecific instanceof Boolean
		assert inputs.mfrCode instanceof Integer || inputs.mfrCode instanceof String || inputs.mfrCode instanceof GString || inputs.mfrCode.is( null )
		assert (inputs.direction == 0) || (inputs.direction == 1)
		assert inputs.disableDefaultResponse instanceof Boolean 
		assert inputs.sequenceNo instanceof Integer || inputs.sequenceNo.is( null )
		assert inputs.commandId instanceof Integer && (inputs.commandId >= 0) && (inputs.commandId <= 255) 
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to the function sendZCLAdvanced. <pre>${e}"
		throw(e)
	}

	// Now set up the command. Start with formatting the ZCLHeader and ZCLpayload according to ZCL § 2.4.1
	StringBuilder ZCLheader = new StringBuilder() // StringBuilder is faster than String for performing appends.
    String ZCLpayload = ""
	 
    // Format the first octet of the frame control string part of the ZCLheader
	Integer frameControlInt = 0b00000000
	if (inputs.isClusterSpecific) 		frameControlInt +=	0b00000001 // ZCL § 2.4.1.1.1
	if (! inputs.mfrCode.is( null) ) 	frameControlInt +=	0b00000100 // ZCL § 2.4.1.1.2
	if (inputs.direction) 				frameControlInt +=	0b00001000 // ZCL § 2.4.1.1.3
	if (inputs.disableDefaultResponse)	frameControlInt +=	0b00010000 // ZCL § 2.4.1.1.4

	ZCLheader << zigbee.convertToHexString(frameControlInt, 2) // got the first octet
	
	// Add the manufacturer code if it was specified. ZCL § 2.4.1.1.2
	if (inputs.mfrCode instanceof Integer) {
		ZCLheader << zigbee.swapOctets(zigbee.convertToHexString(inputs.mfrCode, 4)) 
	} else if (inputs.mfrCode instanceof String || inputs.mfrCode instanceof GString) {
		ZCLheader << zigbee.swapOctets(inputs.mfrCode)
	}

    // Add the sequence number. ZCL § 2.4.1.1.3
	if (inputs.sequenceNo.is( null )) inputs.sequenceNo = getNextTransactionSequenceNo()
	ZCLheader << zigbee.convertToHexString(inputs.sequenceNo, 2)
	
	// last item is the command ID, converted to hex
	ZCLheader << zigbee.convertToHexString(inputs.commandId, 2)

    // Now start assembling the ZCLpayload payload. This follows the header. 

	// If the user specified a command payload, add it. Presumes the user properly formatted the payload.
	// This payload is to the "ZCL payload" field shown in ZCL Fig 2-2 /  ZCL § 2.4.1
    if (inputs.commandPayload) ZCLpayload = inputs.commandPayload 
    
    String profileId
    if (inputs.profileId.class == Integer) {
        profileId = intToHexStr(inputs.profileId).padLeft(4, "0")
    } else { // aleady a string
           profileId = inputs.profileId
    }
	assert profileId == "0104"
	
	Integer sourceEP
	if (inputs.sourceEndpoint instanceof String || inputs.sourceEndpoint instanceof GString) {
		sourceEP = Integer.parseInt(inputs.sourceEndpoint, 16)
	} else { sourceEP = inputs.sourceEndpoint }	
	
	Integer destinationEP
	if (inputs.destinationEndpoint instanceof String || inputs.destinationEndpoint instanceof GString) {
		destinationEP = Integer.parseInt(inputs.destinationEndpoint, 16)
	} else { destinationEP = inputs.destinationEndpoint }
	
	Integer commandedCluster
	if (inputs.clusterId instanceof String || inputs.clusterId instanceof GString) {
		commandedCluster = Integer.parseInt(inputs.clusterId, 16)
	} else {
		commandedCluster = inputs.clusterId
	}
	
	cmd = "he raw ${inputs.destinationNetworkId} ${sourceEP} ${destinationEP} ${commandedCluster} { ${ZCLheader} ${ZCLpayload} }"
	if (logEnable) log.debug "sendZCLAdvanced inputs are: ${inputs}. Formatted command is: $cmd"
	hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
	sendHubCommand(hubAction)  
}

/**
A function for sending any of the Zigbee Device Object (ZDO) command for commands specfied in Zigbee Specification Document 05-3474-21 (Profile 0000 Spec)
*/    
void sendZDOAdvanced(Map params = [:] ) {
        Map inputs = [
				destinationNetworkId:  device.deviceNetworkId ,  // specified as a two-octet pair length 4 string.
				
				clusterId: null ,             // An Integer or 4 hex character string identifying the cluster ID to be operated on. Mandatory.
				
				commandId: null,             // And integer between 0 and 255 representing the ZDO command being sent. 
				
				commandPayload: null     // A string representing the payload values (if any) for the specific commandIds. Hex strings greater than two hex values must be specified in pair-reversed form. So, e.g., the value 0x1234 is entered as "3412"
			]
	// Check the types of all the input parameters.
	try {
	
        // This first check is to make sure you got all the parameter names correct. This will flag any mistyped parameter names.
        assert inputs.keySet().containsAll(params.keySet()) // checks that all user-specified parameters use permitted labels.
		
		// The inputs map sets up the defaults. Then override those with whatever the user supplied.
		inputs << params
		
		// Type check every user input!
		assert (inputs.destinationNetworkId instanceof String || inputs.destinationNetworkId instanceof GString) && (inputs.destinationNetworkId.length() == 4)
		assert inputs.clusterId instanceof Integer || inputs.clusterId instanceof String || inputs.clusterId instanceof GString
		assert inputs.commandPayload instanceof String || inputs.commandPayload instanceof GString || inputs.commandPayload.is(null)
		assert inputs.commandId instanceof Integer && (inputs.commandId >= 0) && (inputs.commandId <= 255) 
	} catch(AssertionError e) {
        log.error "Wrong parameter values ${params} passed to the function sendZDOAdvanced. <pre>${e}"
	    throw(e)
	}
    
	Integer commandedCluster
	if (inputs.clusterId instanceof String || inputs.clusterId instanceof GString) {
		commandedCluster = Integer.parseInt(inputs.clusterId, 16)
	} else {
		commandedCluster = inputs.clusterId
	}
		
	Integer sourceEP = 1		// the Source Endpoint (representing Hubitat) is always 1
	Integer destinationEP = 0 	// The ZDO endpoint is always 0
	
	cmd = "he raw ${inputs.destinationNetworkId} ${sourceEP} ${destinationEP} ${commandedCluster} { ${zigbee.convertToHexString(inputs.commandId, 2)} ${inputs.commandPayload ?: ""} } { 0000 }"

	if (logEnable) log.debug "sendZDOAdvanced inputs are: ${inputs}. Formatted command is: $cmd"
	hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
	sendHubCommand(hubAction)
}
