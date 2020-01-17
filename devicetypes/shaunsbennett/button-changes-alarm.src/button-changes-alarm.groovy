/**
 *  Copyright 2015 SmartThings
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
    definition (name: "Button Changes Alarm", namespace: "shaunsbennett", author: "Shaun S Bennett", ocfDeviceType: 'x.com.st.button') {
        capability "Actuator"
        capability "Button"
        capability "Configuration"
        capability "Sensor"
        capability "Health Check"

        command "away"
        command "stay"
        command "disarm"
    }

    simulator {
        status "away pushed"	:  "command: 2001, payload: 01"
        status "stay pushed"	:  "command: 2001, payload: 29"
        status "disarm pushed"	:  "command: 2001, payload: 51"
        status "wakeup"			:  "command: 8407, payload: "
    }
    tiles {
        standardTile("button", "device.button") {
            state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
        }
        standardTile("away", "device.button", width: 1, height: 1, decoration: "flat") {
            state "default", label: "Away", backgroundColor: "#ffffff", action: "away"
        }
        standardTile("stay", "device.button", width: 1, height: 1, decoration: "flat") {
            state "default", label: "Stay", backgroundColor: "#ffffff", action: "stay"
        }
        standardTile("disarm", "device.button", width: 1, height: 1, decoration: "flat") {
            state "default", label: "Disarm", backgroundColor: "#ffffff", action: "disarm"
        }
        main "button"
        details(["button","away","stay","disarm"])
    }
}

def installed() {
	log.debug "installed with settings: ${settings}"
	initialize() 
}

def updated() {
	log.debug "updated with settings: ${settings}"
	//unsubscribe()
	initialize()
}

def initialize() {
	log.debug "initialize with settings: ${settings}"
    
    sendEvent(name: "numberOfButtons", value: 3)

    sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
    sendEvent(name: "healthStatus", value: "online")
    sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
        
}

def parse(String description) {
    log.debug "parse: {$description}"
}

def away() {
    push(1)
}

def stay() {
    push(2)
}

def disarm() {
    push(3)
}

private push(button) {
	def buttonDescription
	switch(button) {            
		case 1: 
			buttonDescription = "away"
			break 
		case 2: 
			buttonDescription = "stay"
			break 
		case 3: 
			buttonDescription = "disarm"
			break 
		default: 
			buttonDescription = "unknown"
			break 
	}
    def buttonDescriptionText = "$device.displayName button $buttonDescription"
    log.debug "$buttonDescriptionText"
    sendEvent(name: "button", value: "$buttonDescription", data: [buttonNumber: button], descriptionText: "$buttonDescriptionText", isStateChange: true)   
}


