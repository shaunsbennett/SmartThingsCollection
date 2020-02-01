/**
 *  Copyright 2017 SmartThings
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition (name: "Roku Switch", namespace: "shaunsbennett", author: "Shaun S Bennett") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Refresh"
	}

 // UI tile definitions
    tiles(scale: 2) {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#00A0DC"
            state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff"
            state "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#cccccc"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "refresh"])
    }

    
    preferences {
        input name: "prefIP", type: "text", title: "IP Address", description: "Enter IP address", 
        	required: true, displayDuringSetup: true
        input name: "prefTimeout", type: "number", range: "5..30", defaultValue: 15, title: "Device timeout", description: "enter timeout seconds", 
        	required: false, displayDuringSetup: true
		input name: "prefKeyPress", type: "enum", title: "KeyPress Mode (disables on/off)", options: ["No KeyPress Mode", "VolumeUp", "VolumeDown", "VolumeMute"], description: "ECP KeyPress", 
        	required: true, displayDuringSetup: true
        input name: "prefAppId", type: "number", range: "0..99999", defaultValue: 0, title: "Start App Automatically", description: "AppId, 0 to disable", 
        	required: false, displayDuringSetup: true
		}
    
}

// *****     *****

def installed() {
    initialize()
}

def updated() {
	unschedule()
    initialize()
}

def initialize() {
    log.trace "initialize: ${settings}"
    
    setState("ip",prefIP)
    setState("port",8060)
    setState("timeout",(prefTimeout) ?: 15)
    //
    String keyPress = prefKeyPress ?: ""
    setState("keypress", (keyPress.equals("No KeyPress Mode")) ? "" : keyPress )
    //
    setState("appid",(prefAppId) ?: 0)
    
	if(getState("keypress","")) {
    	sendKeyPressEventOff()
    }
    else {
    	refresh()
    }
    
}

// *****     *****

def parse(String description) {
    log.error "parse: should never be called -> ${description}"
}

// *****  commands   *****

def on() {
    sendKeyPressOverride("PowerOn")
}

def off() {
    sendKeyPressOverride("PowerOff")
}

def refresh() {
	sendQuery("device-info")
}

// ***** cmd  *****

def sendKeyPressOverride(key){
    def keyPress = getState("keypress","")
	if(keyPress) {
        key=keyPress
		sendKeyPressEventOn()
        runIn(5, sendKeyPressEventOff)
	}
    sendKeyPress(key)   
}

def sendKeyPressEventOn() {
    sendEvent(name: "switch", value: "on", descriptionText: "The device is in keypress mode")
}

def sendKeyPressEventOff() {
    sendEvent(name: "switch", value: "off", descriptionText: "The device is in keypress mode")
}

def sendAppIdLive() {
    def appId = getState("appid",0)          	
    if(appId) {
    	sendLaunch("${appId}?MediaType=live")
    }
}

// ***** roku parsers  *****

private parseDeviceInfo(xml) {
    def item = xml.'power-mode'
    def val = item=="PowerOn" ? "on" : "off"
    
    if(device.currentValue("switch") != val) {
		log.trace "parseDeviceInfo: ${item}"
    	sendEvent(name: "switch", value: val, isStateChange: true)
        if( val == "on" && getState("lastkeypress","") == "PowerOn" ) {
            runIn(2, sendAppIdLive)
        }
    }
    
    setState("lastkeypress","")
}

// ***** roku requests  *****

def sendQuery(String query) {
	sendRequest([flag: "query", request: query])	
}

def sendKeyPress(String key) {
	setState("lastkeypress",key)
	sendRequest([flag: "keypress", request: key])	
}

def sendLaunch(String data) {
	sendRequest([flag: "launch", request: data])	
}

private sendRequest(data) {
    if(data.flag && data.request){
        def path
        def method
    	if(data.flag == 'query') {
        	method = "GET"
        }
        else if(data.flag == 'keypress' || data.flag == 'launch') {
        	method = "POST"     
        }   
        path = "${data.flag}/${data.request}"
        if(path && method) {
            httpRequest(path,method)
            if(data.flag == 'keypress' && path.contains("Power") && ! getState("keypress","")) {
            	refresh()
            }
        }    
    }
}

// *****  http  *****

def httpRequest(String path, String method = "GET", String contentType = "xml", data = null) {
	path = (path.indexOf("/") == 0) ? path.substring(1) : path
    method = method.toUpperCase()
	contentType = contentType.toLowerCase()
    
    def host = getHostAddress()
    def dni = getHostAddress(true)

    def action
    def result = null
    
    if(method == "GET") {
    	action = """${method} /${path} HTTP/1.1\r\nHost: ${host}\r\n\r\n"""
	    result = new physicalgraph.device.HubAction(action, physicalgraph.device.Protocol.LAN, dni, [callback: httpRequestCallback])
    }
    else if(method == "POST") {
        def params = [ 
            'path' 		: "/${path}",
            'method' 	: method,
            'headers' 	: [
                'Host' 			: host,
                'Content-Type'	: (contentType=="xml") ? 'text/xml; charset="utf-8"' : 'application/json'
            ],
            'body' 		: body                    
        ]
    	action = params.toMapString()
        result = new physicalgraph.device.HubAction(params, dni, [callback: httpRequestCallback])
	}
    if(result) {
        action = "httpRequest: ${action.replaceAll("\r\n"," ")}"
    	log.trace "${action}"
        httpRequestTimeout(["command": "set", "action": action])
    	sendHubCommand(result)
    }
}

void httpRequestCallback(physicalgraph.device.HubResponse hubResponse) {
    log.trace "httpRequestCallback: ${hubResponse.headers}"
    
    httpRequestTimeout(["command": "unset", "action": hubResponse.headers])

	if(hubResponse.status == 200) {
    	if(hubResponse.body) {
            if(hubResponse.body.contains("<device-info>")){
                parseDeviceInfo(hubResponse.xml)
            }
            else {
            	log.warn "httpRequestCallback: ${hubResponse.body}"
            }
        }
    }
    else {
    	log.warn "httpRequestCallback: ${hubResponse.headers}"
    }
}

void httpRequestTimeout(data) {  
    if(data instanceof Map) {
     	def action = (data?.containsKey("action")) ? data.action : ""
    	if(data?.containsKey("command")) {
            switch(data.command) {            
                case "set": 
                	def params = getState("timeout",15).toInteger()
                	runIn(params, "httpRequestTimeout", [overwrite: true, data: ["command": "timeout", "action": action, "params": params]])
                	break; 
                case "unset": 
                	unschedule('httpRequestTimeout')
                	break; 
                case "timeout": 
                    sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
                	break; 
                default: 
                	log.warn "httpRequestTimeout: command not found -> ${data}"
                	break; 
            }		
      	}
        else {
            log.warn "httpRequestTimeout: command not defined -> ${data}"
        }
    }
    else {
    	log.error "httpRequestTimeout: params not a map object -> ${data}"
    }
}

// *****  IP helpers  *****

private getHostAddress(inHex = false) {
    def ip = getState("ip",null)
    def port = getState("port",0)

    if (!ip || !port) {
	    log.error "getHostAddress, can't figure out ip and port for device: ${device.id}"
        return
    }
    
    return (inHex) ? "${convertIPtoHex(ip)}:${convertIntToHex(port)}" : "${ip}:${port}"
}

// gets the address of the Hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

private Integer convertHexToInt(String hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(String hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(String ip) { 
   String hex = ip.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
   return hex
}

private String convertIntToHex(Integer i) {
   String hex = i.toString().format( '%04x', i.toInteger() )
   return hex
}

// *****  state helpers  *****

def setState (_variable, _value){
  state."$_variable" = _value
}

def getState (_variable, defaultValue = ""){
  return (state."$_variable") ? state."$_variable" : defaultValue
}

def clearState(_variable) {
	if(_variable) {
    	state.remove("$_variable")
    }
    else {
    	state.clear() 
    }
}

// *****  health  *****

private setDeviceHealth(healthState) {      
    if(healthState == "initialize") {
        sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
        sendEvent(name: "healthStatus", value: "online")    
        sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
        return
    }
    else if(healthState == "online" || healthState == "offline"){
        def deviceStatus = device.currentValue('DeviceWatch-DeviceStatus')
        def healthStatus = device.currentValue('healthStatus')
 
        log.trace "setDeviceHealth: ${healthStatus}; DeviceWatch-DeviceStatus: ${deviceStatus}" 
		
        if(deviceStatus != healthState || healthStatus != healthState) {
            sendEvent(name: "DeviceWatch-DeviceStatus", value: healthState)       
        	sendEvent(name: "healthStatus", value: healthState)           
        }
    }
    else {
        log.warn "setDeviceHealth: ${healthState} is unknown"    
    }
}