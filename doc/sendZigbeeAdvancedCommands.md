## Methods to send Zigbee Cluster Library Commands

## sendZCLAdvanced

void sendZCLAdvanced(Map params = [

destinationNetworkId: device.deviceNetworkId ,

destinationEndpoint: device.endpointId ,

sourceEndpoint: 1 ,

clusterId: null ,

isClusterSpecific: false ,

mfrCode: null ,

direction: 0 ,

disableDefaultResponse: false ,

sequenceNo: null ,

commandId: null ,

commandPayload: null ,

profileId: 0x0104

])

| **Parameter Name**     | **Type**           | **Mandatory or Optional** | **Description**                                                                                                                                                                                                                                                                                 | **Reference**   |
|------------------------|--------------------|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|
| destinationNetworkId   | 4 Hex Str.         | O                         | Network address of the sent-to device. Default: device.deviceNetworkId                                                                                                                                                                                                                          |                 |
| destinationEndpoint    | Integer 2 Hex Str. | O                         | Endpoint of the sent-to device. Default: device.endpointId                                                                                                                                                                                                                                      |                 |
| sourceEndpoint         | Integer 2 Hex Str. | O                         | Source endpoint. Generally, this is the Hubitat Hub's endpoint. Default: 1 (included for completeness; no other values are currently used).                                                                                                                                                     |                 |
| clusterId              | Integer 4 Hex Str. | M                         | Zigbee cluster to be commanded.                                                                                                                                                                                                                                                                 |                 |
| isClusterSpecific      | Boolean            | O                         | Determines whether a "global" or "cluster specific" command is being set. Sets the Frame Type header bits. Default: false (send global command)                                                                                                                                                 | ZCL § 2.4.1.1.1 |
| mfrCode                | Integer 4 Hex Str. | O                         | Manufacturer code for proprietary extensions. Only specify if using a proprietary command. If specified, then the manufacture bit in the ZCL header will be set to 1.                                                                                                                           | ZCL § 2.4.1.2   |
| direction              | Integer 0 or 1     | O                         | Direction Sub-field bit.  0 = indicates it is being sent from Hubitat. Default: 0 (0 indicates being sent from Hubitat. This is included for completeness; no other values are currently used).                                                                                                 | ZCL § 2.4.1.1.3 |
| disableDefaultResponse | Boolean            | O                         | Sets the disable Default Response bit in the ZCL header. Default: false (enable Default Response).                                                                                                                                                                                              | ZCL § 2.4.1.1.3 |
| sequenceNo             | Integer 0-255      | O                         | The transactions sequence number. Default: Automatically generated using getNextTransactionSequenceNo() method.                                                                                                                                                                                 | ZCL § 2.4.1.3   |
| commandId              | Integer 0-255      | M                         | Integer representing the global or local command being sent.                                                                                                                                                                                                                                    |                 |
| commandPayload         | Hex String         | O                         | Command payload values (if any) for the specific commandId. Hex strings greater than two hex characters must be specified in pair-reversed form. So, e.g., the value 0x1234 is entered as "3412". You can use the byteReverseParametersZCL() function to automate this reversing. Default: null |                 |
| profileId              | Integer 4 Hex Str. | O                         | Currently ignored as this function currently only supports g, cluster "0104" representing the Profile ID for the cluster being acted on. Default: “0104”                                                                                                                                        |                 |

## byteReverseParametersZCL

String byteReverseParametersZCL( List\<String\> payload)

A helper function to format ZCL payload parameters. This function will perform the necessary byte reversing.

Example usage:

byteReverseParametersZCL( [“12”, “3456”, “78”, “9ABCDEFG”]) returns “12563478FGDEBC9A”

sendZCLAdvanced(

clusterId: 0x0300 , // specified as an Integer

destinationEndpoint: ep,

commandId: 0x00, // 00 = Read attributes, ZCL Table 2-3.

commandPayload: byteReverseParametersZCL(["0000", "0001", "0007", "0008"]) // List of attributes of interest [0x0000, 0x0001, 0x0007, 0x0008] in reversed octet form

)

Use this in a call to

## getNextTransactionSequenceNo()

Concurrency-safe method to produce ZCL header sequence numbers. getNextTransactionSequenceNo() returns an integer between 0 and 255.

## Methods to send Zigbee Device Object Commands

This method sends commands on Profile 0000 to the Zigbee Device Object (Destination endpoint 0 on a Zigbee device).

Example usage:
