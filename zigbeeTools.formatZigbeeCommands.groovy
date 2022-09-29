library (
        base: "driver",
        author: "jvm33",
        category: "zigbee",
        description: "Formats Zigbee Commands",
        name: "formatZigbeeCommands",
        namespace: "zigbeeTools",
        documentationLink: "https://github.com/jvmahon/Hubitat-Zigbee",
		version: "0.0.1"
)
import java.util.concurrent.* 
import java.util.concurrent.atomic.*
import groovy.transform.Field

@Field static ConcurrentHashMap deviceCounterStorage = new ConcurrentHashMap(16, 0.75, 1)

Integer getNextTransactionSequenceNo() {
	AtomicInteger counter = deviceCounterStorage.get(device.deviceNetworkId, new AtomicInteger(0))
	
	while(true) {
		int existingValue = counter.get()
		int newValue = ( existingValue + 1 ) % 256
		if (counter.compareAndSet(existingValue, newValue)) { return newValue }
	}
}

String formatZCLCommand( Map inputs = [ isClusterSpecific: false , mfrCode: null , direction: 0 , disableDefaultResponse: false , sequenceNo: null , commandId: null, payload: null ]) 
{
    try {
    assert inputs.isClusterSpecific instanceof Boolean
	assert inputs.mfrCode instanceof Integer || inputs.mfrCode instanceof String || inputs.mfrCode.is( null )
	assert (inputs.direction == 0) || (inputs.direction == 1)
	assert inputs.disableDefaultResponse instanceof Boolean 
	assert inputs.sequenceNo.is( null ) || inputs.sequenceNo instanceof Integer
    assert inputs.commandId instanceof Integer || inputs.commandId instanceof String
    assert inputs.payload instanceof String || inputs.payload instanceof List || inputs.payload.is( null )
    } catch(AssertionError e) {
        log.debug "<pre>${e}"
        throw(e)
    }

    
	if (inputs.sequenceNo.is( null )) inputs.sequenceNo = getNextTransactionSequenceNo()
	
    log.debug "formatZCLCommand Inputs are ${inputs}"
	// Formats the ZCLHeader according to Zigbee Cluster Library Section 2.4.1
	
	List rValue = []
	String mfr
	String cmd
	
	Integer frameControl = 0b00000000
	if (inputs.isClusterSpecific) 		frameControl +=	0b00000001 // Cluster Spec 2.4.1.1.1
	if (! inputs.mfrCode.is( null) ) 	frameControl +=	0b00000100 // Cluster Spec 2.4.1.1.2
	if (inputs.direction) 				frameControl +=	0b00001000 // Cluster Spec 2.4.1.1.3
	if (inputs.disableDefaultResposne)	frameControl +=	0b00010000 // Cluster Spec 2.4.1.1.4
	
	rValue += zigbee.convertToHexString(frameControl, 2)
	
	// Cluster Spec 2.4.1.1.2
	if (inputs.mfrCode instanceof Integer) {
		mfr = zigbee.swapOctets(zigbee.convertToHexString(inputs.mfrCode, 4)) 
		rValue += mfr
	} else if (inputs.mfrCode instanceof String) {
		mfr = zigbee.swapOctets(inputs.mfrCode)
		rValue +=  mfr
	}

    // Cluster Spec 2.4.1.1.3
	rValue += zigbee.convertToHexString(inputs.sequenceNo, 2)
	
	if (inputs.commandId instanceof Integer) {
		assert (0 <= inputs.commandId) && (inputs.commandId <= 255)
		rValue += zigbee.convertToHexString(inputs.commandId, 2)
	} else if (inputs.commandId instanceof String) {
		rValue += inputs.commandId
	}
	
    log.debug "ZCL command payload is ${rValue.join()}"
    return rValue.join()
}



void sendZigbeeCommand(Map inputs = [destinationNetworkId: null , destinationEndpoint: null , sourceEndpoint: 1 , clusterId: null , profileId: 0x0104 , commandPayload: null ] )
{
    try {
    assert inputs.destinationNetworkId instanceof String
    assert inputs.destinationEndpoint instanceof Integer
    assert inputs.sourceEndpoint instanceof Integer
    assert inputs.profileId instanceof Integer
    assert inputs.clusterId instanceof Integer
    assert inputs.commandPayload instanceof String
    } catch(AssertionError e) {
        log.debug "<pre>${e}"
        throw(e)
    }
    
    String cmd
    switch (inputs.profileId) {
        case 0x0000:
            cmd = "he raw ${inputs.destinationNetworkId} ${inputs.sourceEndpoint} ${inputs.destinationEndpoint} ${inputs.clusterId} { ${inputs.commandPayload} } { ${inputs.profileId} }"
            break
        case 0x0104:
            cmd = "he raw ${inputs.destinationNetworkId} ${inputs.sourceEndpoint} ${inputs.destinationEndpoint} ${inputs.clusterId} { ${inputs.commandPayload} }"
            break
    }
    log.debug "sendZigbeeCommand inputs are: ${inputs}. Formatted command is: $cmd"
    hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
 	sendHubCommand(hubAction)
}

