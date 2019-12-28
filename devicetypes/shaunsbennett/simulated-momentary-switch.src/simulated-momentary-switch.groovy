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

    definition (name: "Simulated Momentary Switch", namespace: "shaunsbennett", author: "Shaun S Bennett") {
        capability "Switch"
        capability "Sensor"
        capability "Actuator"
        capability "Contact Sensor"	    		
		capability "Momentary"
		
    }
    
    simulator {
	}

    tiles {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "off", label: '${currentValue}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            state "on", label: '${currentValue}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
        }
        main "switch"
        details(["switch"])
    }
    preferences {
        input "delaySec", "number", title: "Delay Seconds", description: "Delay seconds before momentary toggle", range: "1..*", displayDuringSetup: true
    }
}

def installed() {
    state.counter = state.counter ? state.counter + 1 : 1
    if (state.counter == 1) {
        if(!delaySec) {
            delaySec=1
        }
        off()
    }
}

def parse(description) {
	log.debug "parse: ${description}"
}

def on() {
	push()
}

def onHandler() {
	log.debug "on executed at ${new Date()}"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "contact", value: "open")
}

def off() {
	log.debug "off executed at ${new Date()}"
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed")
}

def push() {
    log.debug "push executed at ${new Date()}, delay set for ${delaySec}"
	sendEvent(name: "momentary", value: "pushed")
	onHandler()
	runIn(delaySec, off)
}