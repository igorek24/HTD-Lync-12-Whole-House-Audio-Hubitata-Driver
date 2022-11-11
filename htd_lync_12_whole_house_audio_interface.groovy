/**
 * HTD Lync 12 Whole House Audio
 * Version 1.0.3
 * Download: TODO: Update repo
 * Description:
 * This is a parent device handler designed to manage and control HTD Lync6/12 connected to the same network
 * as via GW-SL1 gateway.  This device handler requires the installation of a child device handler available from
 * the github repo.
 *-------------------------------------------------------------------------------------------------------------------
 * Copyright 2022 Igor Kuznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the 'Software'), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *-------------------------------------------------------------------------------------------------------------------
 **/

metadata {
    definition(
        name:        "HTD Lync 12 Whole House Audio Interface",
        namespace:   "igorkuz",
        author:      "Igor Kuz",
        importUrl: "https://raw.githubusercontent.com/igorek24/HTD-Lync-12-Whole-House-Audio-Hubitata-Driver/main/htd_lync_12_whole_house_audio_interface.groovy"
    ) {
        capability "HealthCheck"
        capability "Switch"
        capability "SwitchLevel"
        capability 'Refresh'
        capability 'Initialize'
        capability "MediaTransport" // transportStatus - ENUM - ["playing", "paused", "stopped"]

        command "getAllZonesStatus"
        command "getId"
        command "getFirmware"
        command "createAllZones"
        command "deleteAllZones"
        command  "A1Dev"
        command  "queryAll"
        command "createZone", [[name:'Select Zone to create', type: 'ENUM', constraints: [
                1,    2,    3,    4,    5,    6,    7,    8,    9,    10,    11,    12 ] ] ]
        command "deleteZone", [[name:'Select Zone to delete', type: 'ENUM', constraints: [
                1,    2,    3,    4,    5,    6,    7,    8,    9,    10,    11,    12 ] ] ]


        attribute "firmware", "string"
        attribute "systemId", "string"
        attribute "zones", "number"
        attribute "sourceNames", "string"
        attribute "sources", "number"



    }
}

    preferences{
        input name: 'ipAddress', type: 'text', title: '<b>Gateway IP address</b>',description: "Enter IP address of the GW-SL1 Gateway", required: true
        input name: 'port', type: 'number', title: '<b>Gateway Port</b>', required: true, defaultValue: 10006, description: 'IP Port for Gateway'
        input "debugOn", "bool", title: "<b>Enable debug logging for 1 hour</b>", description: 'Debug logging will turn off automatically after 1 hour.', defaultValue: true
        input "infoOn", "bool", title: "<b>Enable info logging</b>", description: 'Enable Info logging. You can disable it if you don\'t want to see it in your logs.', defaultValue: true
    }

/**
 *
 * logging Functions
 *
**/

void debugOff(){
    log.warn "${device.getDisplayName()}: Debug logging disabled..."
    device.updateSetting("debugOn",[value:"false",type:"bool"])
}

def debugLog(msg){
    if (debugOn) {
        log.debug " ${device.getDisplayName()}: ${msg}"
    }
}

def infoLog(msg){

    if (infoOn) log.info "${device.getDisplayName()}: ${msg}"
}

def warnLog(msg){
    log.warn "${device.getDisplayName()}: ${msg}"
}

def errLog(msg){
    log.error "${device.getDisplayName()}: ${msg}"
}

void configure() {}

def updated(){
    getId()
	unschedule()
	initialize()
    runIn(2, getFirmware, [overwrite: true])
    runIn(4, createAllZones, [overwrite: true])
    runIn(6, refresh, [overwrite: true])
    runIn(8, queryAll, [overwrite: true])


}

void installed() {
    device.setName("HTD Lync 12 Whole House Audio")
}

def uninstalled() {
    unschedule()
    log.info("${device.getDisplayName()}: Uninstalling, removing zone devices...")

    deleteAllZones()
    log.info "${device.getDisplayName()}: Uninstalled"
}

def initialize() {
    if (debugOn) {
        if (infoOn) infoLog("${linkText} debug logging enabled for 1 hour")
		runIn(3600, debugOff, [overwrite: true])
    }
}

void ping() {
    if (ipAddress){
    hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ipAddress, 3)
    infoLog("Ping requests: ${pingData.packetsTransmitted} Responses received: ${pingData.packetsReceived}, lost packets : ${pingData.packetLoss}")
    } else{ errLog("IP address is empty, ping not possible!")}
}

void refresh()  {
    getId()
    runIn(1, getFirmware, [overwrite: true])
    runIn(3, getAllZonesStatus, [overwrite: true])
}

// Used for dev to test
void A1Dev() {
    //sendEvent(name: "systemId", value: "Lync12", displayed: true)
    //device.updateSetting("source1Name",[value:"Source 1",type:"text"])

    //state.systemId = null
   // state.zones = null
    //state.inputs = null
    //sendEvent(name: "systemId", value: null, displayed: true)
    //sendEvent(name: "zones", value: null, displayed: true)
   // sendEvent(name: "inputs", value: null, displayed: true)
     //sendEvent(name: "inputs", value: 18, displayed: true)
    //device.updateDataValue("test2", "This is a test")
    //device.removeDataValue("test")
    //queryAll()
    //state.systemId = 'Lync6'
    //sendEvent(name: "systemId", value: 'Lync6', displayed: true)
    //getZoneName(1)
    //getSourceName(1)
}

/**
 *
 * Zone creation, deletion
 *
**/
void createZone(zone) {
        if(!zone){
        errorLog("Zone must be between 1 and 12, received ${zone}")
        return
    }
    def zoneNumRange = 1..12
    if ( zoneNumRange.contains(zone as int) ) {
        def zoneDeviceNetworkId = "${device.deviceNetworkId}-zd${zone}" as String
        def zoneDeviceDisplayName = "${device.displayName} (Zone ${zone})"
        def zoneDeviceLabel = "Zone ${zone}"
        def zoneDevice = getChildDevices().contains(zoneDeviceDisplayName as String)

        if(!getChildDevice(zoneDeviceNetworkId)) {
           zoneDevice = addChildDevice("igorkuz", "HTD Lync 12 Whole House Audio Zone", zoneDeviceNetworkId, [name: zoneDeviceDisplayName,label: zoneDeviceLabel, isComponent: false])
          infoLog("Creating zone ${zoneDeviceDisplayName} with network ID: ${zoneDeviceNetworkId}")

            zoneDevice.setZoneNumber(zone)
            zoneDevice.sendEvent(name: "zoneNumber", zone: null, displayed: true)
        } else {

             warnLog("Zone ${zone} child device already exist.")
        }
    } else {
        errorLog("Invalid zone number: ${zone}")
    }
}

void createAllZones() {
    int zones = device.currentValue("zones") as int
    if(zones == 6 || zones == 12){
        infoLog("Creating all ${zones} zones.")
        for (i in 1..zones){

            createZone(i)
        }
    }

}

void deleteZone(zone) {
    if(!zone){
        errorLog("Zone must be between 1 and 12, received ${zone}")
        return
    }
        def zoneNumRange = 1..12
    if(getChildDevice("${device.deviceNetworkId}-zd${zone}")) {
        if ( zoneNumRange.contains(zone as int) ) {
            debugLog("Deleting zone ${zone} (${device.deviceNetworkId}-zd${zone}.)")
            deleteChildDevice("${device.deviceNetworkId}-zd${zone}")
            debugLog("Zone ${zone} (${device.deviceNetworkId}-zd${zone}) deleted.")
        }else {
            errLog("${getLinkText(device)}: Invalid zone number: ${zone}")
        }
    } else {
        infoLog("Zone ${zone} doesn't exist, nothing to delete.")
    }
}

void deleteAllZones() {
    zones = getChildDevices()
    zoneCount = zones.size()

    if(zones){
        debugLog("Zones: ${zones} will be deleted")
        for (i in 1..zoneCount) {
            deleteZone(i)
        }
    }else{
            infoLog("No zones found, nothing to delete.")
        }
}

void updateZoneState(zone,zoneStates) {

    zoneDevice = getChildDevice("${device.deviceNetworkId}-zd${zone}")
    if (zoneDevice) {
    zoneDevice.updateState(zoneStates)
    debugLog("Zone ${zone} state updated.")
    } else {
        debugLog("Zone doesn't exist, skipping zone ${zone} state update")
    }
}

/**
 *
 * Message processing
 *
**/

def bytesToAscii(bytes) {
    //cleanBytes = bytes.replaceAll(0)
    debugLog("Converting byte message to ASCII")
    ascii = new String(bytes as byte[], "UTF-8");

    clean_text = ascii.replaceAll("\\�","")
    return clean_text
}

void setSystemIdState(systemId,zones,inputNumber) {
    state.systemId = systemId as String
    state.zones = zones as int
    state.sources = inputNumber as int
    sendEvent(name: "systemId", value: systemId, displayed: true)
    sendEvent(name: "zones", value: zones, displayed: true)
    sendEvent(name: "sources", value: inputNumber, displayed: true)
}

void processIdMsg(byteMsg) {
    String systemId = bytesToAscii(byteMsg)
    String stateSystemId = state.systemId
    String stateFirmware = state.firmware
    Integer stateSources = state.sources

    debugLog("Received ID: ${systemId}")

    if(systemId == "Lync12") {
            zoneNumber = 12
            inputNumber = 18
        } else if(systemId == "Lync6") {
            zoneNumber = 6
            inputNumber = 12
        } else {
            zoneNumber = 0
            inputNumber = 0
        }
    inputNumber = (stateFirmware.equals("v3") && stateSystemId.equals("Lync12"))? 19 : inputNumber

    infoLog("ID message is: ${systemId} with ${zoneNumber} zones and ${inputNumber} sources")
    if(!stateSystemId){
        setSystemIdState(systemId,zoneNumber,inputNumber)
        debugLog("State systemId not set, setting it to ${systemId}")
        debugLog("Detected ${systemId} with ${zoneNumber} zones and ${inputNumber}")
    }else if(!stateSystemId.equals("Lync12") && !stateSystemId.equals("Lync6")) {

        setSystemIdState(systemId,zoneNumber,inputNumber)
        debugLog("State system ID is set but I can't detect if it's Lync 6 or Lync 12, setting it to ${systemId}")

    } else if(!stateSources.equals(inputNumber)) {

        debugLog("Updating sources number for v3 to ${inputNumber}")
        state.sources = inputNumber
         sendEvent(name: "sources", value: inputNumber, displayed: true)
    }
        else {
        debugLog("State system ID already set to ${systemId}, skiping this step.")
    }
}

void processFirmwareMsg(byteMsg) {
    // message 0x33 means firmware v3
    infoLog('Firmware: v3 ')
    firmware = "v3"
    state.firmware = firmware
    sendEvent(name: "firmware", value: firmware, displayed: true)

}


void processZoneStatusMsg(msg){
    byteMsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) as byte[]
    intMsg = hubitat.helper.HexUtils.hexStringToIntArray(msg) as int[]
    if(byteMsg[3] == 5){
    zone = byteMsg[2]
        debugLog("Received zone ${zone} status.")

        if (zone == 0 && byteMsg[4] == 6) { // This comes from All Zones Status Message
            processKeypadMsg(byteMsg)
            return
        }

    // Process power, mute and dnd status
        if(byteMsg[4] == -128 as byte || byteMsg[4] == 0 as byte) {
            powerOn = false
            muteOn = false
            dndOnn = false
        }else if (byteMsg[4] == -127 as byte || byteMsg[4] == 1 as byte) {
           powerOn = true
           muteOn = false
           dndOnn = false

        }else if (byteMsg[4] == -125 as byte || byteMsg[4] == 3 as byte){
           powerOn = true
           muteOn = true
           dndOnn = false

        }else if (byteMsg[4] == -123 as byte || byteMsg[4] == 5 as byte){

           powerOn = true
           muteOn = false
           dndOnn = true

        }else if (byteMsg[4] == -121 as byte || byteMsg[4] == 7 as byte){

           powerOn = true
           muteOn = true
           dndOnn = true
        }
         //Process Doorbell status
        if(intMsg[13] > 240 || intMsg[13] < 16){
            doorbell = "off"
        }else if (intMsg[4] == 100 || intMsg[13] < 240){
            doorbell = "on"
        } else { doorbell=""}
            def power = (powerOn) ? 'on' : 'off'
            def mute = (muteOn) ? 'on' : 'off'
            def dnd = (dndOnn) ? 'on' : 'off'

            def input = byteMsg[8]* 1 + 1  as int
            def dB = byteMsg[9] as int
            def treble = byteMsg[10] as int
            def bass = byteMsg[11] as int
                def balance = byteMsg[12] as int
            mute = (dB == -60) ? "on" : mute
        int volumePercentage = (dB + 60)*100/60
                def keypadVolume = 60 + dB
        def zoneDeviceNetworkId = "${device.deviceNetworkId}-zd${zone}" as String

        if( getChildDevice(zoneDeviceNetworkId) ) {
            def zoneStates = [
                'switch' :    power,
                'mute' :      mute,
                'DND' :       dnd,
                'volume' :    volumePercentage,
                'level' :     volumePercentage,
                'dB' :        dB,
                'keypadVolume' : keypadVolume,
                'bass' :      bass,
                'treble' :    treble,
                'balance' :   balance,
                'source' :    input,
                'zoneNumber': zone,
                'doorbell':   doorbell,
                'sourceName': state."source${input}Name"
            ]
           debugLog("Zone status message: ${zoneStates}")
           updateZoneState(zone,zoneStates)
           debugLog("Zone ${zone} state updated.")
        }else{
            debugLog("Zone ${zone} state not updated, zone child doesn't exist.")
        }
    }else{
        warnLog("Invalid zone status message.")
        debugLog("Invalid zone status message: ${byteMsg}")
    }
}

void processAllZonesStatusMsg(msg) {
    byteMsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) as byte[]
     // All zones status update 14 bites per zone

        debugLog("Received all zones status update ${byteMsg}")
        //byteMsg.properties.each{ log.info it}
       def list = byteMsg.toList()
       def zoneMsgs = list.collate( 14, false )
        for(int i in 0..zoneMsgs.size()-1) {
             zMsg = hubitat.helper.HexUtils.byteArrayToHexString(zoneMsgs[i] as byte[])
            debugLog("Zone ${i} message is: ${zoneMsgs[i]}")
            receiveMessage(zMsg)
        }

}

void processAllZoneNamesMsg(msg) {
    byteMsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) as byte[]
     // All zones Names update 18 bites per zone
    debugLog("Received all zones Names update (Byte msg: ${byteMsg})")
    debugLog("Received all zones Names update (HEX str msg: ${msg})")
       def list = byteMsg.toList()
       def nameMsgs = list.collate( 18, false )
        for(int i in 0..nameMsgs.size()-1) {
             zMsg = hubitat.helper.HexUtils.byteArrayToHexString(nameMsgs[i] as byte[])
            debugLog("Zone ${i} message is: ${nameMsgs[i]}")
            receiveMessage(zMsg)
        }

}

void processAllInputNamesMsg(msg) {
    byteMsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) as byte[]
     // All source Names update 18 bites per zone
    debugLog("Received all source Names update (Byte msg: ${byteMsg})")
    debugLog("Received all source Names update (HEX str msg: ${msg})")
       def list = byteMsg.toList()
       def nameMsgs = list.collate( 18, false )
        for(int i in 0..nameMsgs.size()-1) {
             zMsg = hubitat.helper.HexUtils.byteArrayToHexString(nameMsgs[i] as byte[])
            debugLog("Source ${i} message is: ${nameMsgs[i]}")
            processInputNameMsg(nameMsgs[i])
        }

}

void processZoneNameMsg(byte[] byteMsg) {
    def zone = byteMsg[2]
    debugLog("Received zone ${zone} name message " + byteMsg)
    def zoneName = bytesToAscii(byteMsg[4..13])
    debugLog("Zone ${zone} name is: ${zoneName}")
    updateZoneState(zone,['zoneName' : zoneName])
}

void processAllOnOffMsg(byteMsg) {
     def zone = byteMsg[2]
    debugLog("Received All On/Off response message" + byteMsg)
    def list = byteMsg.toList()
    def zoneMsgs = list.collate( 14, false )
        for(int i in 0..zoneMsgs.size()-1) {
            zMsg = hubitat.helper.HexUtils.byteArrayToHexString(zoneMsgs[i] as byte[])
            debugLog("Zone ${i} message is: ${zoneMsgs[i]}")
            receiveMessage(zMsg)
        }
}

void processKeypadMsg(byteMsg) {
    // TODO: figure out how to deal with it
    infoLog("Received keypad message but it's still in TODO list ;)")
}

void processInputNameMsg(byteMsg){
        def zone = byteMsg[2]
        def inputNumber =  byteMsg[15] + 1

        debugLog("Received source ${inputNumber} name message " + byteMsg)
        def inputName = bytesToAscii(byteMsg[4..13])
        debugLog("Source ${inputNumber} controller name is: ${inputName}")

        currentName = state."source${inputNumber}Name"
        newName = inputName
        if(!currentName.equals( newName )) {
            state."source${inputNumber}Name" = inputName
            infoLog("New source name is ${newName}, old one was ${currentName}")
        }else{
            debugLog("Source ${inputNumber} name is the same, no name change")
        }

}

void processQueryAllResponseMessages(msg) {
    def zoneNumber = state.zones
    // When Query all, we receive 5 large messages
    // 1. Echo All Zone Status.
    // 2. Echo All Zone Names.
    // 3. Echo All Source Name
    // 4. Echo MP3 On/Off
    // 5. Echo MP3 File Name and Artist Name
    if(msg.size() >= 2000) {
        def zoneStatusCharNum = (zoneNumber == 12)? 364 : 196 // All zone status message is in this char range
        def zoneNameCharNum = (zoneNumber == 12)? 796 : 398
        def zoneNameInputCharNum = (zoneNumber == 12)? 1480 : 740

        String status = msg.substring(0, zoneStatusCharNum) // firs 364 chars are all zones status for Lync12 message on firs message
        String names = msg.substring(zoneStatusCharNum, zoneNameCharNum)
        String inputNames = msg.substring(zoneNameCharNum, zoneNameInputCharNum)
        if(status[7] == "6" && status[35] == "5") {
            processAllZonesStatusMsg(status)
        }
        if ((names[5] == "1") && (names[427] == "C")) {
            processAllZoneNamesMsg(names)
        }
        if ((inputNames[7] == "E") && (inputNames[427] == "B")) {
            processAllInputNamesMsg(inputNames)
        }
    }
}

/**
 *
 * Send command and receive message
 *
**/

void receiveMessage(msg){

    byteMsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) as byte[]
    // process ID Message (determine if it's Lync6 or Lync12)
    if(byteMsg[0] == 0x4C){ // ID message
         processIdMsg(byteMsg)
    } else if(byteMsg[3] == 0x06 && byteMsg.size() > 14 && byteMsg.size() < 500){ // All zones status message
        processAllZonesStatusMsg(msg)
    } else if(byteMsg[3] == 0x06 && byteMsg.size() <= 14){ // Audio and Keypad Exist Channel
        processKeypadMsg(byteMsg)
    } else if(byteMsg[3] == 0x33){ // Firmware message
         processFirmwareMsg(byteMsg)
    } else if(byteMsg[3] == 5){ // Single Zone status message
         processZoneStatusMsg(msg)
    } else if(byteMsg[3] == 0x0D ) { // Zone Name message
        processZoneNameMsg(byteMsg)
    } else if(byteMsg[3] == 0x0E && byteMsg.size() <= 18 ) { // Source name message
        processInputNameMsg(byteMsg)
    } else if(byteMsg[4] == 0x09 ) { // MP3 Play End Stop
        // Process MP3 Play End Stop
    } else if(byteMsg[4] == 0x11 ) { // MP3 File Name
       // TODO: Process mp3 file name
    }

    else if(byteMsg.size() >= 500 ) { // Query all response messages
        processQueryAllResponseMessages(msg)
    }
}

/**
 *
 * Send commands
 *
**/

void getId() {
    def cmd = [2,0,0,8,0,0x0A] as byte[]
    sendCmd(cmd)
}

void getAllZonesStatus() {
    def cmd = [2, 0, 0, 5, 0] as byte[]
     sendCmd(cmd)
}

void getAllZonesNames() {
    def cmd = [2, 0, 0, 6, 0, 7] as byte[]
    sendCmd(cmd)
}

void queryAll() {
    def cmd = [2, 0, 1, 0x0C, 0] as byte[]
    sendCmd(cmd)
}

void getFirmware() {

    def cmd = [2,0,0,0x0F,0,0x11] as byte[]
    sendCmd(cmd)
}

void on() {
    def cmd = [0x02,0x00,0x00,0x04,0x55] as byte[]
    sendCmd(cmd)
}

void off(){
    def cmd = [0x02,0x00,0x00,0x04,0x56] as byte[]
    sendCmd(cmd)
}

void getZoneName(zone) {
    def cmd = [2,0,zone,0x0D,0] as byte[]
    sendCmd(cmd)
}

void getSourceName(input) {
    input == --input
    def cmd = [2,0,1,0x0E,input] as byte[]
    sendCmd(cmd)
}

void changeZoneName(name) {
    // TODO: make it work
}

void changeSourceName(name) {
    // TODO: make it work
}

/**
 *
 * Send mp3 player commands
 *
**/

void play(){
    playPause()
}

void playPause(){

    def cmd = [2,0,0,4,0x0B] as byte[]
    sendCmd(cmd)
}

void pause(){
    playPause()
}

void stop(){
   def cmd = [2,0,0,4,0x0D] as byte[]
    //sendCmd(cmd)
}

void sendCmd(byte[] byteMsg) {
    def cksum = [0] as byte[]
    for (byte i : byteMsg)
    {
        cksum[0] += i
    }
    debugLog("Cheksum computed as: ${cksum}")

    def msgCksum = [byteMsg, cksum].flatten() as byte[]

    def strMsg = hubitat.helper.HexUtils.byteArrayToHexString(msgCksum)

    debugLog("Sending Message: ${strMsg} to ${ipAddress}:${port}")

    interfaces.rawSocket.connect(ipAddress as String, port as int, 'byteInterface':true)
    interfaces.rawSocket.sendMessage(strMsg)

    //interfaces.rawSocket.close()
}

/**
 *
 * Asynchronous receive function
 *
**/
void parse(String msg) {
    debugLog("New message received: ${msg}")
    receiveMessage(msg)
}
