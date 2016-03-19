/**
 *  Hue and Improved
 *
 *  Copyright 2016 Alan Penner
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
definition(
        name: "Hue & Improved",
        namespace: "penner42",
        author: "Alan Penner",
        description: "Hue ",
        category: "My Apps",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/hue.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/hue@2x.png",
        singleInstance: true
)

preferences {
    page(name:"Bridges", title:"Hue Bridges", content: "bridges")
    page(name:"linkButton", content: "linkButton")
    page(name:"linkBridge", content: "linkBridge")
    page(name:"manageBridge", content: "manageBridge")
}

def missingDevices(mac) {
    /* check to see if we need to add bulb, group, or scene devices */
    def selectedBulbs = settings."${mac}-selectedBulbs" ?: []
    def selectedScenes = settings."${mac}-selectedScenes" ?: []
    def selectedGroups = settings."${mac}-selectedGroups" ?: []
    def bridge = getBridge(mac)

    def devicesToCreate = ["bulbs":[:], "scenes":[:], "groups":[:]]
    selectedBulbs.each {
        def devId = "${mac}/BULB${it}"
        def dev = getChildDevice(devId)
        if (!dev) {
            def label = bridge.value.bulbs[it].name
            devicesToCreate.bulbs << ["${devId}": label]
        }
    }
    selectedScenes.each {
        def devId = "${mac}/SCENE${it}"
        def dev = getChildDevice(devId)
        if (!dev) {
            def label = bridge.value.scenes[it].name
            devicesToCreate.scenes << ["${devId}": label]
        }
    }
    selectedGroups.each {
        def devId = "${mac}/GROUP${it}"
        def dev = getChildDevice(devId)
        if (!dev) {
            def label = bridge.value.groups[it].name
            devicesToCreate.groups << ["${devId}": label]
        }
    }
    return devicesToCreate
}

def createDevices(mac) {
    def devicesToCreate = missingDevices(mac)
    def bridge = getBridge(mac)

    if (devicesToCreate.bulbs.size() > 0 || devicesToCreate.scenes.size() > 0 || devicesToCreate.groups.size() > 0) {
        devicesToCreate.bulbs.each {
            def d = getChildDevice(it.key)
            if (!d) {
                try {
                    debug("creating ${it.key} - ${it.value}")
                    def bulbId = it.key.split("/")[1] - "BULB"
                    def type = getBridge(mac).value.bulbs[bulbId].type
                    if (type.equalsIgnoreCase("Dimmable light")) {
                        addChildDevice("penner42", "Hue Lux Bulb", it.key, hub, ["label": it.value])
                    } else {
                        addChildDevice("penner42", "Hue Bulb", it.key, hub, ["label": it.value])
                    }
                } catch (e) {
                    debug ("Exception ${e}")
                }
            }
        }
        devicesToCreate.scenes.each {
            def d = getChildDevice(it.key)
            if (!d) {
                try {
                    debug("creating ${it.key} - ${it.value}")
                    addChildDevice("penner42", "Hue Scene", it.key, hub, ["label": it.value])
                } catch (e) {
                    debug ("Exception ${e}")
                }
            }
        }
        devicesToCreate.groups.each {
            def d = getChildDevice(it.key)
            if (!d) {
                try {
                    debug("creating ${it.key} - ${it.value}")
                    addChildDevice("penner42", "Hue Group", it.key, hub, ["label": it.value])
                } catch (e) {
                    debug ("Exception ${e}")
                }
            }
        }
    }
}

def removeDevices(mac) {
    def selectedBulbs = settings."${mac}-selectedBulbs" ?: []
    def selectedScenes = settings."${mac}-selectedScenes" ?: []
    def selectedGroups = settings."${mac}-selectedGroups" ?: []

    def devices = getChildDevices()
    devices.each {
        def netId = it.deviceNetworkId
        if (netId.contains(mac) && netId.contains("/")) {
            def whichDevices = selectedBulbs
            if (netId.contains("SCENE")) {
                whichDevices = selectedScenes
            } else if (netId.contains("GROUP")) {
                whichDevices = selectedGroups
            }
            def id = netId.split("/")[1] - "GROUP" - "SCENE" - "BULB"
            if (!(whichDevices.contains(id))) {
                try {
                    debug ("deleting ${it.label}")
                    deleteChildDevice(netId)
                } catch (e) {
                    //already deleted?
                }
            }
        }
    }
}

def manageBridge(params) {
    /* with submitOnChange, params don't get sent when the page is refreshed? */
    if (params.mac) {
        state.params = params;
    } else {
        params = state.params;
    }

    def bridge = getBridge(params.mac)
    def ip = convertHexToIP(bridge.value.networkAddress)
    def mac = params.mac
    def bridgeDevice = getChildDevice(mac)
    bridgeDevice.each {
        debug it
    }
    def title = "${bridgeDevice.label} ${ip}"

    if (!bridgeDevice) {
        debug("Bridge device not found?")
        /* Error, bridge device doesn't exist? */
        return
    }

    removeDevices(mac)
    createDevices(mac)

    int itemRefreshCount = !state.itemRefreshCount ? 0 : state.itemRefreshCount as int
    if (!state.itemDiscoveryComplete) {
        state.itemRefreshCount = itemRefreshCount + 1
    }

    /* resend request if we haven't received a response in 4 seconds */
    if ((!state.inItemDiscovery && !state.itemDiscoveryComplete) || (state.itemRefreshCount == 3)) {
        state.itemDiscoveryComplete = false
        state.inItemDiscovery = mac

        if (state.numDiscoveryResponses == 0) {
            bridgeDevice.discoverBulbs();
            state.itemRefreshCount = 0
        } else if (state.numDiscoveryResponses == 1) {
            bridgeDevice.discoverScenes();
            state.itemRefreshCount = 0
        } else if (state.numDiscoveryResponses == 2) {
            bridgeDevice.discoverGroups();
            state.itemRefreshCount = 0
        } else if (state.numDiscoveryResponses == 3) {
            state.itemDiscoveryComplete = true
            state.numDiscoveryResponses = 0
            state.itemRefreshCount = 0
        }
    }

    def bulbList = [:]
    def sceneList = [:]
    def groupList = [:]

    bridge.value.bulbs.each {
        bulbList[it.value.id] = it.value.name
    }
    bridge.value.scenes.each {
        sceneList[it.value.id] = it.value.name
    }
    bridge.value.groups.each {
        groupList[it.value.id] = it.value.name
    }

    def numBulbs = bulbList.size() ?: 0
    def numScenes = sceneList.size() ?: 0
    def numGroups = groupList.size() ?: 0

    def paragraphText = ""
    def refreshInterval = 3
    if (state.itemDiscoveryComplete) {
        refreshInterval = 0
        paragraphText = "Item discovery complete! Bulbs, groups, and scenes listed below. If any items are missing, please tap back and try again.\n\n" +
                "Note: Don't select more than 5 devices to add at a time, or SmartThings will timeout with an error."

    } else {
        refreshInterval = 2
        paragraphText =  "Starting discovery of Bulbs, Scenes, and Groups. This can take some time, results will appear below.\n\n" +
                "Note: Don't select more than 5 devices to add at a time, or SmartThings will timeout with an error."
    }

    dynamicPage(name:"manageBridge", title: "Manage bridge ${ip}", refreshInterval: refreshInterval, install: true) {
        section("${paragraphText}") {
            input "${mac}-selectedBulbs", "enum", required:false, title:"Select Hue Bulbs (${numBulbs} found)", multiple:true, submitOnChange: true, options:bulbList.sort{it.value}
            input "${mac}-selectedScenes", "enum", required:false, title:"Select Hue Scenes (${numScenes} found)", multiple:true, submitOnChange: true, options:sceneList.sort{it.value}
            input "${mac}-selectedGroups", "enum", required:false, title:"Select Hue Groups (${numGroups} found)", multiple:true, submitOnChange: true, options:groupList.sort{it.value}
        }
    }
}

def linkBridge() {
    state.params.done = true
    log.debug "linkBridge"
    dynamicPage(name:"linkBridge") {
        section() {
            getLinkedBridges() << state.params.mac
            paragraph "Linked! Please tap Done."
        }
    }
}

def linkButton(params) {
    /* if the user hit the back button, use saved parameters as the passed ones no longer good
     * also uses state.params to pass these on to the next page
     */
    if (params.mac) {
        state.params = params;
    } else {
        params = state.params;
    }

    int linkRefreshcount = !state.linkRefreshcount ? 0 : state.linkRefreshcount as int
    state.linkRefreshcount = linkRefreshcount + 1
    def refreshInterval = 3

    params.linkingBridge = true
    if (!params.linkDone) {
        if ((linkRefreshcount % 2) == 0) {
            sendDeveloperReq("${params.ip}:80", params.mac)
        }
        log.debug "linkButton ${params}"
        dynamicPage(name: "linkButton", refreshInterval: refreshInterval, nextPage: "linkButton") {
            section("Hue Bridge ${params.ip}") {
                paragraph "Please press the link button on your Hue bridge."
                image "http://www.developers.meethue.com/sites/default/files/smartbridge.jpg"
            }
            section() {
                href(name:"Cancel", page:"Bridges", title: "", description: "Cancel")
            }
        }
    } else {
        /* link success! create bridge device */
        debug "Bridge linked!"
        debug("ssdp ${params.ssdpUSN}")
        def bridge = getUnlinkedBridges().find{it?.key?.contains(params.ssdpUSN)}
        debug("bridge ${bridge}")
        def d = addChildDevice("penner42", "Hue Bridge", bridge.value.mac, bridge.value.hub)

        d.sendEvent(name: "networkAddress", value: params.ip)
        d.sendEvent(name: "serialNumber", value: bridge.value.serialNumber)
        d.sendEvent(name: "username", value: params.username)

        subscribe(d, "itemDiscovery", itemDiscoveryHandler)

        params.linkDone = false
        params.linkingBridge = false

        bridge.value << ["bulbs" : [:], "groups" : [:], "scenes" : [:]]
        getLinkedBridges() << bridge
        debug "Bridge added to linked list."
        getUnlinkedBridges().remove(params.ssdpUSN)
        debug "Removed bridge from unlinked list."

        dynamicPage(name: "linkButton", nextPage: "Bridges") {
            section("Hue Bridge ${params.ip}") {
                paragraph "Successfully linked Hue Bridge! Please tap Next."
            }
        }
    }
}

def getLinkedBridges() {
    state.linked_bridges = state.linked_bridges ?: [:]
}

def getUnlinkedBridges() {
    state.unlinked_bridges = state.unlinked_bridges ?: [:]
}

def getVerifiedBridges() {
    getUnlinkedBridges().findAll{it?.value?.verified == true}
}

def getBridgeBySerialNumber(serialNumber) {
    def b = getUnlinkedBridges().find{it?.value?.serialNumber == serialNumber}
    if (!b) {
        return getLinkedBridges().find{it?.value?.serialNumber == serialNumber}
    } else {
        return b
    }
}

def getBridge(mac) {
    def b = getUnlinkedBridges().find{it?.value?.mac == mac}
    if (!b) {
        return getLinkedBridges().find{it?.value?.mac == mac}
    } else {
        return b
    }
}

def bridges() {
    /* Prevent "Unexpected Error has occurred" if the user hits the back button before actually finishing an install.
     * Weird SmartThings bug
     */
    if (!state.installed) {
        return dynamicPage(name:"Bridges", title: "Initial installation", install:true, uninstall:true) {
            section() {
                paragraph "For initial installation, please tap Done, then proceed to Menu -> SmartApps -> Hue & Improved."
            }
        }
    }

    state.debug_on = true
    /* clear temporary stuff from other pages */
    state.params = [:]
    state.inItemDiscovery = null
    state.itemDiscoveryComplete = false
    state.numDiscoveryResponses = 0
    state.createRefreshCount = 0
    state.waitingForCreation = ""

    int bridgeRefreshCount = !state.bridgeRefreshCount ? 0 : state.bridgeRefreshCount as int
    state.bridgeRefreshCount = bridgeRefreshCount + 1
    def refreshInterval = 3

    if (!state.subscribed) {
        subscribe(location, null, locationHandler, [filterEvents:false])
        state.subscribed = true
    }

    // Send bridge discovery request every 15 seconds
    if ((state.bridgeRefreshCount % 5) == 1) {
        discoverHueBridges()
        debug "Bridge discovery sent."
    } else {
        // if we're not sending bridge discovery, verify bridges instead
        verifyHueBridges()
    }

    dynamicPage(name:"Bridges", refreshInterval: refreshInterval, install: true, uninstall: true) {
        section("Linked Bridges") {
            getLinkedBridges().sort { it.value.name }.each {
                def ip = convertHexToIP(it.value.networkAddress)
                def mac = "${it.value.mac}"
                def title = "Hue Bridge ${ip}"
                href(name:"manageBridge ${mac}", page:"manageBridge", title: title, description: "", params: [mac: mac])
            }
        }
        section("Unlinked Bridges") {
            paragraph "Searching for Hue bridges. They will appear here when found. Please wait."
            getVerifiedBridges().sort { it.value.name }.each {
                def ip = convertHexToIP(it.value.networkAddress)
                def mac = "${it.value.mac}"
                def title = "Hue Bridge ${ip}"
                href(name:"linkBridge ${mac}", page:"linkButton", title: title, description: "", params: [mac: mac, ip: ip, ssdpUSN: it.value.ssdpUSN])
            }
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def uninstalled() {
    log.debug "uninstalling"
    state.installed = false
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    initialize()
}

def initialize() {
    debug "initialize"
    unsubscribe()
    state.subscribed = false
    state.unlinked_bridges = [:]
    state.installed = true

    state.linked_bridges.each {
        def d = getChildDevice(it.value.mac)
        subscribe(d, "itemDiscovery", itemDiscoveryHandler)
    }
}

def itemDiscoveryHandler(evt) {
    def bulbs = evt.jsonData[0]
    def scenes = evt.jsonData[1]
    def groups = evt.jsonData[2]
    def mac = evt.jsonData[3]
    def bridge = getBridge(mac)

    bridge.value.bulbs = bulbs
    bridge.value.groups = groups
    bridge.value.scenes = scenes

    /* item discovery is done when numDiscoveryResponses == 3
     * to prevent race conditions, we don't start searching for the next item type until we finish the current one
     */
    state.numDiscoveryResponses = state.numDiscoveryResponses + 1
    state.inItemDiscovery = false
}

def locationHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId
    def parsedEvent = parseLanMessage(description)

    parsedEvent << ["hub":hub]
    if (parsedEvent?.ssdpTerm?.contains("urn:schemas-upnp-org:device:basic:1")) {
        /* SSDP response */
        processDiscoveryResponse(parsedEvent)
    } else if (parsedEvent.headers && parsedEvent.body) {
        /* Hue bridge HTTP reply */
        def headerString = parsedEvent.headers.toString()
        if (headerString.contains("xml")) {
            /* description.xml reply, verifying bridge */
            processVerifyResponse(parsedEvent.body)
        } else if (headerString?.contains("json")) {
            def body = new groovy.json.JsonSlurper().parseText(parsedEvent.body)
            if (body.success != null && body.success[0] != null && body.success[0].username) {
                /* got username from bridge */
                state.params.linkDone = true
                state.params.username = body.success[0].username
            } else if (body.error && body.error[0] && body.error[0].description) {
                log.debug "error: ${body.error[0].description}"
            } else {
                log.debug "unknown response: ${headerString}"
                log.debug "unknown response: ${body}"
            }
        }
    }
}

/**
 * HUE BRIDGE COMMANDS
 **/
private discoverHueBridges() {
    debug("Sending bridge discovery.")
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:basic:1", physicalgraph.device.Protocol.LAN))
}

private verifyHueBridges() {
    def devices = getUnlinkedBridges().findAll { it?.value?.verified != true }
    devices.each {
        def ip = convertHexToIP(it.value.networkAddress)
        def port = convertHexToInt(it.value.deviceAddress)
        verifyHueBridge("${it.value.mac}", (ip + ":" + port))
    }
}

private verifyHueBridge(String deviceNetworkId, String host) {
    debug("Sending verify request for ${deviceNetworkId} (${host})")
    sendHubCommand(new physicalgraph.device.HubAction([
            method: "GET",
            path: "/description.xml",
            headers: [
                    HOST: host
            ]]))
}

/**
 * HUE BRIDGE RESPONSES
 **/
private processDiscoveryResponse(parsedEvent) {
    debug("Discovered bridge ${parsedEvent.mac} (${convertHexToIP(parsedEvent.networkAddress)})")

    def bridge = getUnlinkedBridges().find{it?.key?.contains(parsedEvent.ssdpUSN)} ||
            getLinkedBridges().find{it?.key?.contains(parsedEvent.ssdpUSN)}
    if (bridge) {
        /* have already discovered this bridge */
        debug("Previously found bridge discovered")
    } else {
        debug("Found new bridge.")
        state.unlinked_bridges << ["${parsedEvent.ssdpUSN}":parsedEvent]
    }
}

private processVerifyResponse(eventBody) {
    debug("Processing verify response.")
    def body = new XmlSlurper().parseText(eventBody)
    if (body?.device?.modelName?.text().startsWith("Philips hue bridge")) {
        debug(body?.device?.UDN?.text())
        def bridge = getUnlinkedBridges().find({it?.key?.contains(body?.device?.UDN?.text())})
        if (bridge) {
            debug("found bridge!")
            bridge.value << [name:body?.device?.friendlyName?.text(), serialNumber:body?.device?.serialNumber?.text(), verified: true]
        } else {
            log.error "/description.xml returned a bridge that didn't exist"
        }
    }
}

private sendDeveloperReq(ip, mac) {
    debug("Sending developer request to ${ip} (${mac})")
    def token = app.id
    sendHubCommand(new physicalgraph.device.HubAction([
            method: "POST",
            path: "/api",
            headers: [
                    HOST: ip
            ],
            body: [devicetype: "$token-0", username: "$token-0"]]))
}

/**
 * UTILITY FUNCTIONS
 **/
def getCommandData(id) {
    def ids = id.split("/")
    def bridge = getBridge(ids[0])
    def bridgeDev = getChildDevice(ids[0])

    def result = [ip: "${bridgeDev.currentValue("networkAddress")}:80",
                  username: "${bridgeDev.currentValue("username")}",
                  deviceId: "${ids[1] - "BULB" - "GROUP" - "SCENE"}",
    ]
    return result
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

def scaleLevel(level, fromST = false, max = 254) {
    /* scale level from 0-254 to 0-100 */
    if (fromST) {
        return Math.round( level * max / 100 )
    } else {
        return Math.round( level * 100 / max )
    }
}

def parse(desc) {
    debug("parse")
}