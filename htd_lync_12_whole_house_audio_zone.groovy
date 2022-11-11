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
        name:        "HTD Lync 12 Whole House Audio Zone",
        namespace:   "igorkuz",
        author:      "Igor Kuz",
        //importUrl: ""
    ) {
        capability "AudioVolume"
        capability "HealthCheck"
        capability "Switch"
        capability "Actuator"
        capability "SwitchLevel"


        command "updateZoneStatus"
        command "dndOn"
        command "dndOff"
        command "doorbellOn"
        command "doorbellOff"
        command "selectInput", [[name:"inputNum",type:"NUMBER", description:"Input Number", constraints:["NUMBER"]]]
        command "setVolumeDBLevel", [[name:"volumeDBLevel",type:"NUMBER", description:"dB number -60 - 0 (postive number will be converted to negative (5 = -5)", constraints:["NUMBER"]]]
        command "setKeypadVolume", [[name:"volumeLevel",type:"NUMBER", description:"Keypad volume # 0-60", constraints:["NUMBER"]]]

        attribute "switch", "string"
        attribute "mute", "string"
        attribute "DND", "string"
        attribute "doorbell", "string"
        attribute "zoneNumber", "number"
        attribute "zoneName", "string"
        attribute "source", "number"
        attribute "sourceName", "string"
        attribute "dB", "number"
        attribute "bass", "number"
        attribute "treble", "number"
        attribute "balance", "number"
        attribute "volume", "unit:%"
        attribute "lastActivity", "number"
        attribute "keypadVolume", "number"
    }

    //preferences{
       // input name: 'zoneDisplayName', type: 'text', title: 'Zone Display Name', description: 'Display Name for this Zone'
    //}
}

void updated() {

}
def debugLog(msg){
    if (getParent().getSetting("debugOn")) {
        log.debug " ${device.getDisplayName()}: ${msg}"
    }
}

def infoLog(msg){
    if (getParent().getSetting("infoOn")) log.info "${device.getDisplayName()}: ${msg}"
}

def warnLog(msg){
    log.warn "${device.getDisplayName()}: ${msg}"
}

def errLog(msg){
    log.error "${device.getDisplayName()}: ${msg}"
}
void setZoneNumber(zone) {
    state.zoneNumber = zone
}
void on() {
     zone = state.zoneNumber as byte
     def cmd = [2,0,zone,4,0x57] as byte[]
    getParent().sendCmd(cmd)
    sendEvent(name: "switch", value: "on")
}

void off() {
     zone = state.zoneNumber as byte
     def cmd = [2,0,zone,4,0x58] as byte[]
    getParent().sendCmd(cmd)
}
void ping() {
 getParent().ping()
}
void mute() {
    def zone = state.zoneNumber as byte
     def cmd = [2,0,zone,4,0x1E] as byte[]
     getParent().sendCmd(cmd)
}
void unmute() {
    def zone = state.zoneNumber as byte
     def cmd = [2,0,zone,4,0x1F] as byte[]
     getParent().sendCmd(cmd)
}
void dndOn() {
    def zone = state.zoneNumber as byte
    def cmd = [2,0,zone,4,0x59] as byte[]
    getParent().sendCmd(cmd)
}
void dndOff() {
    def zone = state.zoneNumber as byte
    def cmd = [2,0,zone,4,0x5A] as byte[]
    getParent().sendCmd(cmd)
}
void doorbellOn() {
    def zone = state.zoneNumber as byte
    def cmd = [2,1,zone,4,0xA1] as byte[]
    getParent().sendCmd(cmd)
}
void doorbellOff() {
        def zone = state.zoneNumber as byte
    def cmd = [2,0,zone,4,0xA0] as byte[]
    getParent().sendCmd(cmd)
}
def selectInput(inputNum) {
    def zone = state.zoneNumber as byte
    def inputNumRange = 1..18
    if ( inputNumRange.contains(inputNum as int) )
    {
        def inputMap = [0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,0x19,0x1A,0x1B,0x63,0x64,0x65,0x66,0x67,0x68]
        def cmd = [2, 0, zone, 4, inputMap[(inputNum-1) as int]] as byte[]
        getParent().sendCmd(cmd)
        return true
    }
    else {

        log.error "Invalid input number: ${inputNum}"
        return false
    }
}
def lookupVolumeHex(int volume) {
    msg = [
        0xC4,0xC5,0xC6,0xC7,0xC8,0xC9,
        0xCA,0xCB,0xCC,0xCD,0xCE,0xCF,
        0xD0,0xD1,0xD2,0xD3,0xD4,0xD5,
        0xD6,0xD7,0xD8,0xD9,0xDA,0xDB,
        0xDC,0xDD,0xDE,0xDF,0xE0,0xE1,
        0xE2,0xE3,0xE4,0xE5,0xE6,0xE7,
        0xE8,0xE9,0xEA,0xEB,0xEC,0xED,
        0xEE,0xEF,0xF0,0xF1,0xF2,0xF3,
        0xF4,0xF5,0xF6,0xF7,0xF8,0xF9,
        0xFA,0xFB,0xFC,0xFD,0xFE,0xFF,
        0x00
    ]

    return msg[volume]
}
void setVolume(volumeLevel) {
    def zone = state.zoneNumber as byte
    if (device.currentValue('switch') == 'off') {
        debugLog("Device off, no volume control")
        return
    }
    // TODO: improve volume percentage calculation
    def currentVolume = device.currentValue('volume') as int
    def currentDB = device.currentValue('dB') as int
    def desiredDB = volumeLevel*60/100 as int
        (volumeLevel == 1 )? desiredDB = 1 : null
        (volumeLevel == 2 || volumeLevel == 3) ? desiredDB = 4: null
    debugLog("Desired Volume : ${volumeLevel}, Current Volume: ${currentVolume}")
    debugLog("Desired dB : -${desiredDB}, Current dB: ${currentDB}")
    def cmd = [2, 0, zone, 0x15, lookupVolumeHex(desiredDB)] as byte[]
    getParent().sendCmd(cmd)
    updateZoneStatus()

}
void setVolumeDBLevel(volumeDBLevel) {

    if (volumeDBLevel<-60 || volumeDBLevel>60) {
    errorLog("Invalid dB value. must be between -60 and 0")
        return
    }
    def zone = state.zoneNumber as byte
    Integer volumeDBLevelInt = ( volumeDBLevel > 0 ) ? volumeDBLevel - volumeDBLevel*2 : volumeDBLevel
    def currentDB = device.currentValue('dB') as int
    debugLog("Current dB: ${currentDB}, Desired dB : ${volumeDBLevel}")
    def cmd = [2, 0, zone, 0x15, volumeDBLevelInt] as byte[]
    getParent().sendCmd(cmd)
    updateZoneStatus()

}
void volumeUp() {
    def zone = state.zoneNumber as byte
    def currentDBValue = state.dB
    def volUpDownVal = state.volUpDown
        

    if (!currentDBValue.equals(volUpDownVal)) {
        newVolVal = volUpDownVal+1
        state.volUpDown = newVolVal+1
    } else{
        newVolVal = currentDBValue+1
        state.volUpDown = newVolVal+1
    }
    


    def cmd = [2, 0, zone, 0x15, newVolVal] as byte[]
    getParent().sendCmd(cmd)

}
void volumeDown() {
       def zone = state.zoneNumber as byte
    def currentDBValue = state.dB
    def volUpDownVal = state.volUpDown
        

    if (!currentDBValue.equals(volUpDownVal)) {
        newVolVal = volUpDownVal-1
        state.volUpDown = newVolVal-1
    } else{
        newVolVal = currentDBValue-1
        state.volUpDown = newVolVal-1
    }

    def cmd = [2, 0, zone, 0x15, newVolVal -1] as byte[]
    getParent().sendCmd(cmd)
    //updateZoneStatus()

}

void setKeypadVolume(volumeLevel) {
    def volumeLevelNumRange = 1..60
    if (volumeLevel && volumeLevelNumRange.contains(volumeLevel as int) ) {
    def keypadVolume = 60 - volumeLevel
    keypadVolume = "-${keypadVolume}"
    setVolumeDBLevel(keypadVolume as int)
    }else {
        warnLog("To set Keypad Volume level, value must be between 0 and 60")
    }
}
void setLevel(newLevel,duration=null) {
    infoLog("${device.label?device.label:device.name}: setLevel(${newLevel}" + (duration==null?")":", ${duration}s)"))
    setVolume(newLevel)
    // TODO: Add duration (Volume Up/Down?)
}
void updateZoneStatus() {
    // looks like, best way to force zone status update is
    // to send input change command with current input value/
    // Not very efficient but it works (That's how official HTD app does it)
    if(state.switch != "on") {
        debugLog("Zone is off, no refresh possible")
        return
    }
            def zone = state.zoneNumber as byte
            def input = state.source as int
            input = ( input == 19)? zone : input
            getParent().debugLog("Updating zone ${zone} state by setting the input to current input ${input} for zone ${zone}")
            debugLog("Updating zone state by setting the input to current input ${input}")
            selectInput(input as int)
}
void updateState(statesMap) {
    statesMap.each{entry -> sendEvent(name: entry.key, value: entry.value, displayed: true)

        def now
        if(location.timeZone) {
            now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
        } else {
                now = new Date().format("yyyy MMM dd EEE h:mm:ss a")
            }
        sendEvent(name: "lastActivity", value: now, displayed:false)
        // This is needed for Volume Up/Down
        if(entry.key.equals("dB")){
                state.volUpDown = entry.value
            }

        def newStateValue = entry.value as String
        def curStateString = state."${entry.key}" as String

        if(!curStateString.equals( newStateValue )) {
            infoLog("${entry.key} is ${newStateValue}")
            getParent().infoLog("Zone ${state.zoneNumber} ${entry.key} is ${entry.value}")
            // changes the zone name that was pulled from Lync6/12
            if(entry.key.equals("zoneName")){
            device.setName(entry.value)
            }

            
        }

        state."${entry.key}" = entry.value

    }
}
