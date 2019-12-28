/**
 *  Roku Device
 *
 *  Copyright 2016 SmartThings
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
 *	--------------------------------------------------
 *	Disable Network Ping - Menu - System Operatin Menu
 *	--------------------------------------------------
 *  Press the Home button 5 times
 *  Press the Fast Forward button 1 time
 *  Press the Play button 1 time
 *  Press the Rewind button 1 time
 *  Press the Play button 1 time
 *  Press the Fast Forward button 1 time
 *	--------------------------------------------------
 *
*/
metadata {
	definition (name: "Roku Device", namespace: "shaunsbennett", author: "Shaun S Bennett") {
		capability "Switch"
		capability "Refresh"

		attribute "friendlyName", "String"
		attribute "appId", "Number"
		attribute "appName", "String"
		attribute "screensaverId", "String"
		attribute "screensaverName", "String"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
		}
		standardTile("refresh", "device.status",  width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh", backgroundColor:"#ffffff"
         
		}
        
        main("switch")
		details(["switch","refresh"])
    }

    preferences {
        input name: "prefIP", type: "text", title: "IP Address", description: "Enter IP address", 
        	required: true, displayDuringSetup: true
        input name: "prefLog", type: "enum", title: "Log Level", options: ["error", "warn", "info", "debug", "trace", "none"], description: "Enter minimum log level", 
        	required: true, displayDuringSetup: true, defaultValue: "debug"
    }

}

// **********************************************************************************************************************

def installed() {
    logMessage("installed: ${settings}", "trace")
    initialize()
}

def updated() {
    logMessage("updated: ${settings}", "trace")
	unschedule()
    initialize()
}

def uninstalled() {
	unschedule()
}

def initialize() {
	clearState()
	setState("ip",prefIP)
	setState("port",8060)

    logMessage("initialize: ${state}", "trace")
}

// parse events into attributes
def parse(String description) {
    logMessage("parse: '${description}'", "warn")
}

// **********************************************************************************************************************

def on() {
    sendEvent(name: "switch", value: "on")
    sendKeyPress("PowerOn")
}    

def off() {
    sendEvent(name: "switch", value: "off")
    sendKeyPress("PowerOff")
}

def refresh() {
	sendQuery("device-info")
	sendQuery("active-app")
}

// **********************************************************************************************************************

def sendQuery(String query) {
	sendRequest([flag: "query", request: query])	
}

def sendKeyPress(String key) {
	sendRequest([flag: "keypress", request: key])	
}

private sendRequest(data) {
    if(data.flag && data.request){
        def path
        def method
    	if(data.flag == 'query') {
        	method = "GET"
        }
        else if(data.flag == 'keypress') {
        	method = "POST"     
        }   
        path = "${data.flag}/${data.request}"
        if(path && method) {
            httpRequest(path,method)
            if(data.flag == 'keypress' && path.contains("Power")) {
            	sendQuery("device-info")
            }
        }    
    }
}

// **********************************************************************************************************************

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
    	logMessage(action, "trace")
        httpRequestTimeout(["command": "set", "action": action])
    	sendHubCommand(result)
    }
}

void httpRequestCallback(physicalgraph.device.HubResponse hubResponse) {
    logMessage("httpRequestCallback: ${hubResponse.headers}", "trace")
    httpRequestTimeout(["command": "unset", "action": hubResponse.headers])

	if(hubResponse.status == 200) {
    	if(hubResponse.body) {
            if(hubResponse.body.contains("<device-info>")){
                parseDeviceInfo(hubResponse.xml)
            }
            else if(hubResponse.body.contains("<apps>")){
                parseApps(hubResponse.xml)
            }
            else if(hubResponse.body.contains("<active-app>")){
                parseActiveApp(hubResponse.xml)
            }               
        }
    }
    else {
    	logMessage("httpRequestCallback: ${hubResponse.headers}", "warn")
    }
}

void httpRequestTimeout(data) {  
    //logMessage("httpRequestTimeout: ${data}", "trace")
    if(data instanceof Map) {
     	def action = (data?.containsKey("action")) ? data.action : ""
    	if(data?.containsKey("command")) {
            switch(data.command) {            
                case "set": 
                	def params = 15
                	runIn(params, "httpRequestTimeout", [overwrite: true, data: ["command": "timeout", "action": action, "params": params]])
                	break; 
                case "unset": 
                	unschedule('httpRequestTimeout')
                	break; 
                default: 
                	logMessage("httpRequestTimeout: ${data}", "warn")
                	break; 
            }		
      	}
    }
}

// **********************************************************************************************************************

private parseDeviceInfo(xml) {
    def item
    def val
    
    item = xml.'power-mode'
    val = item=="PowerOn" ? "on" : "off"
    if(device.currentValue("switch") != val) {
		logMessage("parseDeviceInfo: ${item}", "info")
    	sendEvent(name: "switch", value: val, displayed: true)  
    }
    
    item = xml.'friendly-device-name'.text()
    if(device.currentValue("friendlyName") != item) {   
        logMessage("parseDeviceInfo: ${item}", "info")
        sendEvent(name: "friendlyName", value: item, displayed: true)      
    }

}

private parseApps(xml) {
/*
   <apps>
     <app id="11">Roku Channel Store</app>
     <app id="12">Netflix</app>
     <app id="13">Amazon Video on Demand</app>
     <app id="14">MLB.TV®</app>
     <app id="26">Free FrameChannel Service</app>
     <app id="27">Mediafly</app>
     <app id="28">Pandora</app>
    </apps>
*/

	def appName 
    def appId
    xml.app.each { thing ->
        appName = thing
        appId = thing.@id
    }
    
}

private parseActiveApp(xml) {
	def elem = ["app","screensaver"]
    def appName 
    def appId
	elem.each {
        appName = xml."${it}".text()
        appId = xml."${it}".@id.text()

        if(appId.isEmpty()){ appId = "0" }
        
        if(device.currentValue("${it}Id").toString() != appId) {   
            logMessage("parseActiveApp: ${it}: ${appName}::${appId}", "info")
            sendEvent(name: "${it}Name", value: appName, displayed: true)  
            sendEvent(name: "${it}Id", value: appId, displayed: false)  
        }    
    }
}

// **********************************************************************************************************************

def logMessage(message, level = "debug") {
	def logLevelMin = logMessageToInt(prefLog)
    if(logLevelMin > 0) {
        def logLevel = logMessageToInt(level)
    	if(logLevel >= logLevelMin) {
        	log."${level}"(message)
        }
    }    
}

def logMessageToInt(level) {
	// ["error", "warn", "info", "debug", "trace", "none"]
	def i
    switch(level) {            
        case "error": 
            i=5
            break
        case "warn": 
            i=4
            break
        case "info": 
            i=3
            break
        case "debug": 
            i=2
            break
        case "trace": 
            i=1
            break
        case "none": 
            i=0
            break
        default: 
            i=-1
        	break
    }
    return i
}

// **********************************************************************************************************************

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

// **********************************************************************************************************************

// gets the address of the Hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddress(inHex = false) {
    def ip = getState("ip")
    def port = getState("port")

    if (!ip || !port) {
	    logMessage("getHostAddress, can't figure out ip and port for device: ${device.id}", "error")
        return
    }
    
    return (inHex) ? "${convertIPtoHex(ip)}:${convertIntToHex(port)}" : "${ip}:${port}"
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