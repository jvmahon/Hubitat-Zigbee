library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Formats Zigbee Commands",
        name: "sendZigbeeAdvancedCommands",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)
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

void sendZCLAdvanced(Map inputs = [
							destinationNetworkId: null ,  // specified as a two-octet pair length 4 string.
							destinationEndpoint: null ,  // specified as an Integer. Unsure of order!
							sourceEndpoint: 1 ,         // specified as an Integer
							clusterId: null ,             // spedified as an Integer
							isClusterSpecific: false ,     // specified as a boolean. Determines whether ZCL header sets a global or local command.
							mfrCode: null ,             // specified as an Integer or Hex string (not octet reversed)
							direction: 0 ,                 // Integer. Rarely anything but 0
							disableDefaultResponse: false , 
							sequenceNo: null ,         // Sequence between 0 - 255. Should change for each transaction. Generate using getNextTransactionSequenceNo()
							commandId: null,             // The command ID as an integer
							commandPayload: null     // String representing the payload for the command ID. Can be trick to format! Watch out for octet reversing.
                        ] ) {
    log.debug "inputs are ${inputs}"
	// Check the types of all the input parameters.
	try {
		assert (inputs.destinationNetworkId instanceof String) && (inputs.destinationNetworkId.length() == 4)
		assert inputs.destinationEndpoint instanceof Integer
		assert inputs.sourceEndpoint instanceof Integer
		assert (inputs.profileId instanceof Integer) 
		assert inputs.clusterId instanceof Integer
		assert inputs.commandPayload instanceof String || inputs.commandPayload.is(null)
		assert inputs.isClusterSpecific instanceof Boolean
		assert inputs.mfrCode instanceof Integer || inputs.mfrCode instanceof String || inputs.mfrCode.is( null )
		assert (inputs.direction == 0) || (inputs.direction == 1)
		assert inputs.disableDefaultResponse instanceof Boolean 
		assert inputs.sequenceNo instanceof Integer
		assert inputs.commandId instanceof Integer && (inputs.commandId >= 0) && (inputs.commandId <= 255) 
	} catch(AssertionError e) {
		log.error "Wrong parameter values passed to the function sendZigbeeAdvanced. <pre>${e}"
		log.error "inputs=$inputs"
	throw(e)
	}
    

	// Now set up the command. Start with formatting the ZCLHeader and ZCLpayload according to Zigbee Cluster Library Section 2.4.1
	String ZCLheader = "", ZCLpayload = ""
	 
    // Format the first octet of the frame control string part of the ZCLheader
	Integer frameControlInt = 0b00000000
	if (inputs.isClusterSpecific) 		frameControlInt +=	0b00000001 // Cluster Spec 2.4.1.1.1
	if (! inputs.mfrCode.is( null) ) 	frameControlInt +=	0b00000100 // Cluster Spec 2.4.1.1.2
	if (inputs.direction) 				frameControlInt +=	0b00001000 // Cluster Spec 2.4.1.1.3
	if (inputs.disableDefaultResposne)	frameControlInt +=	0b00010000 // Cluster Spec 2.4.1.1.4

	ZCLheader = zigbee.convertToHexString(frameControlInt, 2) // got the first octet
	
	// Add the manufacturer code if it was specified. Cluster Spec 2.4.1.1.2
	if (inputs.mfrCode instanceof Integer) {
		ZCLheader += zigbee.swapOctets(zigbee.convertToHexString(inputs.mfrCode, 4)) 
	} else if (inputs.mfrCode instanceof String) {
		ZCLheader += zigbee.swapOctets(inputs.mfrCode)
	}

    // Add the sequence number. Cluster Spec 2.4.1.1.3
	if (inputs.sequenceNo.is( null )) inputs.sequenceNo = getNextTransactionSequenceNo()
	ZCLheader += zigbee.convertToHexString(inputs.sequenceNo, 2)
	
	// last item is the command ID, converted to hex
	ZCLheader += zigbee.convertToHexString(inputs.commandId, 2)

    // Now start assembling the ZCLpayload payload. This follows the header. 

	// If the user specified a command payload, add it. Presumes the user properly formatted the payload.
	// This payload is to the "ZCL payload" field shown in Fig 2-2 of Cluster Spec 2.4.1
    if (inputs.commandPayload) ZCLpayload = inputs.commandPayload 
            cmd = "he raw ${inputs.destinationNetworkId} ${inputs.sourceEndpoint} ${inputs.destinationEndpoint} ${inputs.clusterId} { ${ZCLheader} ${ZCLpayload} }"
            log.debug "sendZigbeeCommand inputs are: ${inputs}. Formatted command is: $cmd"
            hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
 	        sendHubCommand(hubAction)  
}
    
void sendZDOAdvanced(Map inputs = [
							destinationNetworkId: null ,  // specified as a two-octet pair length 4 string.
							destinationEndpoint: null ,  // specified as an Integer. Unsure of order!
							sourceEndpoint: 1 ,         // specified as an Integer
							clusterId: null ,             // spedified as an Integer
							profileId: 0x0000 ,         // specified as an Integer. Only 0x0104 is supported
							commandId: null,             // The command ID as an integer
							commandPayload: null     // String representing the payload for the command ID. Can be trick to format! Watch out for octet reversing.
                        ] ) {
	// Check the types of all the input parameters.
	try {
		assert (inputs.destinationNetworkId instanceof String) && (inputs.destinationNetworkId.length() == 4)
		assert inputs.destinationEndpoint instanceof Integer
		assert inputs.sourceEndpoint instanceof Integer
		assert (inputs.profileId instanceof Integer) && (inputs.profileId == 0)
		assert inputs.clusterId instanceof Integer
		assert inputs.commandPayload instanceof String || inputs.commandPayload.is(null)
		assert inputs.commandId instanceof Integer && (inputs.commandId >= 0) && (inputs.commandId <= 255) 
	} catch(AssertionError e) {
		log.error "Wrong parameter values passed to the function sendZDOAdvanced. <pre>${e}"
		log.error "inputs=$inputs"
	    throw(e)
	}
    
        cmd = "he raw ${inputs.destinationNetworkId} ${inputs.sourceEndpoint} ${inputs.destinationEndpoint} ${inputs.clusterId} { ${zigbee.convertToHexString(inputs.commandId, 2)} ${inputs.commandPayload ?: ""} } { 0000 }"

        log.debug "sendZDOAdvanced inputs are: ${inputs}. Formatted command is: $cmd"
        hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
 	    sendHubCommand(hubAction)

}


