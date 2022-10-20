/*
This library implements functions for sending Zigbee Cluster Library "ZCL" and Zigbee Device Object "ZDO" commands.

References:
ZCL :  Zigbee Cluster Library Specification Document 07-5123 Revision 8. Profile 0104 Spec
ZDO :  Zigbee Specification Document 05-3474-21. Profile 0000 Spec
*/
library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Formats Zigbee Commands",
        name: "sendZigbeeAdvancedCommands",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.5.0"
)


import java.util.concurrent.* 
import java.util.concurrent.atomic.*
import groovy.transform.Field

@Field static ConcurrentHashMap deviceCounterStorage = new ConcurrentHashMap(16, 0.75, 1)

// The next function created transaction sequence numbers from 0 - 255, then starts over. Concurrency-safe.
Integer getNextTransactionSequenceNo() {
	AtomicInteger counter = deviceCounterStorage.get(device.deviceNetworkId, new AtomicInteger(0))
	
	while(true) {
		int existingValue = counter.get()
		int newValue = ( existingValue + 1 ) % 256
		if (counter.compareAndSet(existingValue, newValue)) { return newValue }
	}
}

// The ZCL command payload needs parameters of greater than 2 characters to be pair-reversed.
// This function takes a list of parameters and pair-reverses those longer than 2 characters.
// Alternatively, it can take a string and pair-revers that.
// Thus, e.g., ["0123", "456789", "10"] becomes "230189674510" and "123456" becomes "563412"
private String byteReverseParameters(String oneString) { byteReverseParameters([] << oneString) }
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

void sendZCLAdvanced(
		destinationNetworkId: device.deviceNetworkId ,		
		destinationEndpoint:  getEndpointId(device) ,		
		sourceEndpoint: "01" ,		
		isClusterSpecific: false , 		
		mfrCode: null , 		
		direction: 0 , 		
		disableDefaultResponse: false ,		
		sequenceNo: null ,	
		commandId: null ,
		commandPayload: null,
		commandPayloadAutoreverse: true	,
		profileId: 0x0104
	)
		
*/
void sendZCLAdvanced(Map params = [:] ) {
	Map inputs = [
		destinationNetworkId: device.deviceNetworkId ,  // A 4 hex character data string specifying the network address of the node you are sending to. Optional - defaults to device.deviceNetworkId.
		
		destinationEndpoint:  getEndpointId(device) ,  // An Integer or 2 hex character string identifying the destination endpoint. Optional - defaults to getEndpointId(device)
		
		sourceEndpoint: "01" ,         // An Integer or 2 hex character string identifying the source endpoint. Generally, this is the Hubitat Hub's endpoint. Optional - defaults to 1
		clusterId: null ,           // An Integer or 4 hex character string identifying the cluster ID to be operated on. Mandatory.
		
		isClusterSpecific: false ,     // A boolean value that determines whether a "global" or "cluster specific" command is to be executed. This sets the ZCL header bit. Optional. Defaults to "false" -- send a "global" command.
		
		mfrCode: null ,             // An Integer or 4 hex character string identifying the manufacturere code for proprietary extensions. Optional. Not specified unless using a proprietary extension. If specified, then the manufacture bit in the ZCL header will be set to 1.  See ZCL § 2.4.1.2
		
		direction: 0 ,                 // Direction Sub-field bit. 1 indicates the command is being sent from the node. 0 indicates it is being sent from Hubitat. Optional - defaults to 0 (there does not appear to be any use for non-zero values). ZCL § 2.4.1.1.3
		
		disableDefaultResponse: false , // Sets the disable Default Response bit in the ZCL header. Defaults to "false". Generally, you want to leave this as false so you do receive the "default" response. See ZCL § 2.4.1.1.3
		
		sequenceNo: null ,         // Specified as an integer. The transactions sequence number is a number between 0 - 255. A different transaction sequence should be used for each new transaction. Optional. If not specified, this is generated using the getNextTransactionSequenceNo() methd. See ZCL § 2.4.1.3
		
		commandId: null,             // A 2-Character Hex string or an integer between 0 and 255 representing the global or local command being sent. 
		
		commandPayload: null,     // A Hex string or a List of Hex strings representing the payload values (if any) for the specific commandIds. Hex strings greater than two hex values are transformed into pair-reversed form. So, e.g., the value 0x1234 is entered as "3412" uness commandPayloadAutoreverse is set to false. 
		
		commandPayloadAutoreverse: true , // Determines whether to reverse payload elements.
		
		profileId: 0x0104        // An integer or string representing the Profile ID for the cluster being acted on. This is currently ignored as this function currenly only supports cluster "0104"
	]
		
	// This first check is to make sure you got all the parameter names correct. This will flag any mistyped parameter names.
	assert inputs.keySet().containsAll(params.keySet()) // checks that all user-specified parameters use permitted labels.
	
	// The inputs map sets up the defaults. Then override those with whatever the user supplied.
	inputs << params
	
	// Type check every user input!
	assert (inputs.destinationNetworkId instanceof String || inputs.destinationNetworkId instanceof GString) && (inputs.destinationNetworkId.length() == 4)
	
	if (inputs.destinationEndpoint instanceof Integer) { inputs.destinationEndpoint = zigbee.convertToHexString(inputs.destinationEndpoint, 2) }

	assert inputs.destinationEndpoint instanceof Integer || inputs.destinationEndpoint instanceof String || inputs.destinationEndpoint instanceof GString
	if (inputs.sourceEndpoint instanceof Integer) { inputs.sourceEndpoint = zigbee.convertToHexString(inputs.sourceEndpoint, 2) }
	assert inputs.sourceEndpoint instanceof String || inputs.sourceEndpoint instanceof GString
	if (inputs.profileId instanceof Integer) { inputs.profileId = zigbee.convertToHexString(inputs.profileId, 4) }
	assert (inputs.profileId instanceof String || inputs.profileId instanceof GString)// Not octet reversed!
	if (inputs.clusterId instanceof Integer) { inputs.clusterId = zigbee.convertToHexString(inputs.clusterId, 4) }
	assert inputs.clusterId instanceof String || inputs.clusterId instanceof GString
	assert inputs.commandPayload instanceof List || inputs.commandPayload instanceof String || inputs.commandPayload instanceof GString || inputs.commandPayload.is(null)
	assert inputs.commandPayloadAutoreverse instanceof Boolean
	assert inputs.isClusterSpecific instanceof Boolean
	if (inputs.mfrCode instanceof Integer) {
		inputs.mfrCode = zigbee.swapOctets(zigbee.convertToHexString(inputs.mfrCode, 4)) 
	}
	assert inputs.mfrCode instanceof String || inputs.mfrCode instanceof GString || inputs.mfrCode.is( null )
	assert (inputs.direction == 0) || (inputs.direction == 1)
	assert inputs.disableDefaultResponse instanceof Boolean 
	assert inputs.sequenceNo instanceof Integer || inputs.sequenceNo.is( null )
	if (inputs.commandId instanceof Integer) {
		assert (inputs.commandId >= 0) && (inputs.commandId <= 255) 
		inputs.commandId = zigbee.convertToHexString(inputs.commandId, 2)
	}
	assert inputs.commandId instanceof String || inputs.commandId instanceof GString
	assert inputs.commandId.length() == 2 

	// Now set up the command. Start with formatting the ZCLHeader and ZCLpayload according to ZCL § 2.4.1
	StringBuilder ZCLheader = new StringBuilder() // StringBuilder is faster than String for performing appends.
    String ZCLpayload = ""
	 
    // Format the first octet of the frame control string part of the ZCLheader
	Integer frameControlInt = 0b00000000
	if (inputs.isClusterSpecific) 		frameControlInt +=	0b00000001 // ZCL § 2.4.1.1.1
	if (inputs.mfrCode?.length() > 0) 	frameControlInt +=	0b00000100 // ZCL § 2.4.1.1.2
	if (inputs.direction) 				frameControlInt +=	0b00001000 // ZCL § 2.4.1.1.3
	if (inputs.disableDefaultResponse)	frameControlInt +=	0b00010000 // ZCL § 2.4.1.1.4

	ZCLheader << zigbee.convertToHexString(frameControlInt, 2) // got the first octet
	
	// Add the manufacturer code if it was specified. ZCL § 2.4.1.1.2
	if (inputs.mfrCode instanceof String || inputs.mfrCode instanceof GString) {
		assert inputs.mfrCode.length() == 4
		ZCLheader << zigbee.swapOctets(inputs.mfrCode)
	}

    // Add the sequence number. ZCL § 2.4.1.1.3
	inputs.sequenceNo = inputs.sequenceNo ?: getNextTransactionSequenceNo()
	ZCLheader << zigbee.convertToHexString(inputs.sequenceNo, 2)
	
	// last item is the command ID in Hex form
	ZCLheader << inputs.commandId

    // Now start assembling the ZCLpayload payload. This follows the header. 

	// If the user specified a command payload, add it.
	// This payload is to the "ZCL payload" field shown in ZCL Fig 2-2 /  ZCL § 2.4.1
	// Payload can be a list or a single string. The payload Hex pairs are automatically reversed.
	if (inputs.commandPayload) { 
		if (inputs.commandPayloadAutoreverse) { 
			inputs.commandPayload = byteReverseParameters(inputs.commandPayload) 
		} else if (inputs.commandPayload instanceof List) {
			// byteReverseParameters alread converted the List to String for commandPayloadAutoreverse == true, so this is only needed if you have a List and commandPayloadAutoreverse == false
			inputs.commandPayload.each{ // Check that each sub-element is a string of allowed size!
				assert (it instanceof String || it instanceof GString) : "Invalid commandPayload for method sendZCLAdvanced. Each element must be a hex string. Element ${it} is a ${it.class}. Method inputs were ${params.inspect()}"
				assert (it.length() % 2 == 0) : "CommandPayload item ${it} has invalid size for method sendZCLAdvanced. Must be a multiple of 2 characters. Method inputs were ${params.inspect()}"
			}
			// If all the elements are strings with sizes that are a multiple of 2, just join them together
			inputs.commandPayload = inputs.commandPayload.join()
		}
	}
	ZCLpayload = inputs.commandPayload
	
	cmd = "he raw ${inputs.destinationNetworkId} 0x${inputs.sourceEndpoint} 0x${inputs.destinationEndpoint} 0x${inputs.clusterId} { ${ZCLheader} ${ZCLpayload} }"
    if (logEnable) log.debug "sendZCLAdvanced inputs are: ${inputs.inspect()}. Formatted command is: ${cmd}"
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
			
			sequenceNo: null ,         // Specified as an integer. The transactions sequence number is a number between 0 - 255. A different transaction sequence should be used for each new transaction. Optional. If not specified, this is generated using the getNextTransactionSequenceNo() method. See ZDP  § 2.4.2.8
			
			commandPayload: null,     // A Hex string or a List of Hex strings representing the payload values (if any) for the specific commandIds. Hex strings greater than two hex values are transformed into pair-reversed form. So, e.g., the value 0x1234 is entered as "3412" uness commandPayloadAutoreverse is set to false. 
		
			commandPayloadAutoreverse: true , // Determines whether to reverse payload elements.
		]
	// Check the types of all the input parameters.
	
	// This first check is to make sure you got all the parameter names correct. This will flag any mistyped parameter names.
	assert inputs.keySet().containsAll(params.keySet()) // checks that all user-specified parameters use permitted labels.
	
	// The inputs map sets up the defaults. Then override those with whatever the user supplied.
	inputs << params
	
	// Type check every user input!
	assert (inputs.destinationNetworkId instanceof String || inputs.destinationNetworkId instanceof GString) && (inputs.destinationNetworkId.length() == 4)
	if (inputs.clusterId instanceof Integer) { inputs.clusterId = zigbee.convertToHexString(inputs.clusterId, 4) }
	assert (inputs.clusterId instanceof String || inputs.clusterId instanceof GString)
	assert inputs.clusterId.length() == 4
	assert inputs.commandPayload instanceof List || inputs.commandPayload instanceof String || inputs.commandPayload instanceof GString || inputs.commandPayload.is( null )
	assert inputs.commandPayloadAutoreverse instanceof Boolean
	assert inputs.sequenceNo instanceof Integer || inputs.sequenceNo.is( null )

    // Add the sequence number. ZDO § 2.4.2.8
	if (inputs.sequenceNo.is( null )) inputs.sequenceNo = getNextTransactionSequenceNo()

    
	if (inputs.commandPayload) { 
		if (inputs.commandPayloadAutoreverse) { 
			inputs.commandPayload = byteReverseParameters(inputs.commandPayload) 
		} else if (inputs.commandPayload instanceof List) {
			// byteReverseParameters alread converted the List to String for commandPayloadAutoreverse == true, so this is only needed if you have a List and commandPayloadAutoreverse == false
			inputs.commandPayload.each{ // Check that each sub-element is a string of allowed size!
				assert (it instanceof String || it instanceof GString) : "Invalid commandPayload for method sendZCLAdvanced. Each element must be a hex string. Element ${it} is a ${it.class}. Method inputs were ${params.inspect()}"
				assert (it.length() % 2 == 0) : "CommandPayload item ${it} has invalid size for method sendZCLAdvanced. Must be a multiple of 2 characters. Method inputs were ${params.inspect()}"
			}
			// If all the elements are strings with sizes that are a multiple of 2, just join them together
			inputs.commandPayload = inputs.commandPayload.join()
		}
	}
		
	String sourceEP = "01"		// the Source Endpoint (representing Hubitat) is always 1
	String destinationEP = "00" 	// The ZDO endpoint is always 0
	
	cmd = "he raw ${inputs.destinationNetworkId} 0x${sourceEP} 0x${destinationEP} 0x${inputs.clusterId} { ${zigbee.convertToHexString(inputs.sequenceNo, 2)} ${inputs.commandPayload ?: ""} } { 0000 }"

	if (logEnable) log.debug "sendZDOAdvanced inputs are: ${inputs.inspect()}. Formatted command is: $cmd"
	hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
	sendHubCommand(hubAction)
}
