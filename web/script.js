// Getting all relevent query parameters
var eventid = getParamOrDefault("event-id", 0);
var maxMessages = getParamOrDefault("max-messages", 10);
var allowColors = parseInt(getParamOrDefault("colors", 0));
var showStreamer = parseInt(getParamOrDefault("show-streamer", 0));

let youtubeLogo = "YouTube.png"
let twitchLogo = "Twitch.png"
let socket = undefined;

// Root element for all messages
let messageContainer = document.getElementById("messagecontainer");

// Colors for the username
let colors = ["rgb(80,0,201)"
            , "rgb(15,195,219)"
            , "rgb(23,234,41)"
            , "rgb(192,23,234)"
            , "rgb(234,23,136)"
            , "rgb(234,23,23)"
            ];

// initial connect (or reconnect xD)
reconnect();

// Sending messages to the websocketserver to check if the connection is still alive
setInterval(function(){
    send('keepAlive');
}, 5000);

function onMessage(event) {
    let messages = event.data.split("\n");
    
    messages.forEach(message => {
        let segment = message.split(",");
        if (segment[1]) {

            // Username and Content are base64 encrypted therefore we decrypt it here
            let username = b64DecodeUnicode(segment[0]);
            let content = b64DecodeUnicode(segment[1]);

            let timestamp = segment[2];
            let platform = segment[3];
            let streamer = segment[4];

            let msgPackage = document.createElement("div");
            msgPackage.classList.add("msgPackage");

            { // Platform Logo
                let icon = document.createElement("img");
                icon.setAttribute("src", platform == "Twitch" ? twitchLogo : youtubeLogo);
                msgPackage.appendChild(icon);
            }

            if (showStreamer) { // Streamer
                let msgStreamer = document.createElement("span");
                msgStreamer.classList.add("streamer");
                msgStreamer.innerText = streamer;
                msgPackage.appendChild(msgStreamer);
            }

            { // Timestamp
                let msgTimestamp = document.createElement("span");
                msgTimestamp.classList.add("timestamp");
                let date = new Date(parseInt(timestamp));
                let hour = convertToDualDigit(date.getHours().toString());
                let minute = convertToDualDigit(date.getMinutes().toString());
                msgTimestamp.innerText = hour+':'+minute;
                msgPackage.appendChild(msgTimestamp);
            }

            { // Username
                let msgUsername = document.createElement("p");
                msgUsername.classList.add("username");
                msgUsername.innerHTML = username;
                // Getting a color based on the hascode of the username
                if (allowColors) {
                    let usernameColor = colors[Math.abs(username.hashCode() % colors.length)];
                    msgUsername.style.setProperty("color", usernameColor);
                }
                msgPackage.appendChild(msgUsername);
            }

            { // Message
                let msgElement = document.createElement("p");
                msgElement.classList.add("messageContent");
                msgElement.innerHTML = ": " + content;
                msgPackage.appendChild(msgElement);
            }
            
            messageContainer.appendChild(msgPackage);

            // Scroll down to the bottom of the window
            window.scrollTo(0, document.body.scrollHeight); 
        }
    });
    let len = messageContainer.childNodes.length;

    // Checkinf if more then the maximum allowed messages are displayed
    // if so, delete the oldest
    if (len > maxMessages) { 
        for (let i = 0; i < len-maxMessages; i ++) {
            let node = messageContainer.childNodes[i];
            messageContainer.removeChild(node);
        }
    }    
}

/**
 * Reconnecting the WebSocket client
 */
function reconnect() {
    console.log("[WS] reconnecting");
    socket = new WebSocket((document.location.protocol === "https:" ? "wss://" : "ws://") + window.location.hostname + "/ws", []);

    socket.onopen = function(event) {
        console.log("[WS] opend");
        send(eventid + "," + maxMessages);
    }

    socket.onmessage = onMessage;

    socket.onclosed = function(event) {
        console.log("[WS] closed");
        socket = null;
        setTimeout(function() { reconnect(); }, 1000);
        socket.onclosed = null;
    }

    socket.onerror = function(event) {
        console.log("[WS] failed " + event);
        socket = null;
        setTimeout(function() { reconnect(); }, 1000);
        socket.onclosed = null;
    }
}

function send(content) {
    if (socket.readyState == 1) socket.send(content);
    else setTimeout(function() { reconnect(); }, 1000);
}

/**
 * UTF-8 Compatible base64 decoding
 * @param {string} str encrypted base64 String
 * @returns decrypted string
 */
function b64DecodeUnicode(str) {
    return decodeURIComponent(atob(str).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
}

/**
 * Gets the value of the given parameter of the query parameters
 * @param {string} param requested param
 * @param {any} defaultVal returns if the param was not found
 * @returns Value of the given query parameter
 */
function getParamOrDefault(param, defaultVal) {
    let regex = RegExp(param+'=([^&#=]*)');
    var matcher = regex.exec(window.location.search);
    var result = matcher == null ? defaultVal : matcher[1]
    return result;
}

/**
 * Converts single digit numbers to a dual digit
 * @param {number} input 
 * @returns dual digit number
 */
function convertToDualDigit(input) {
    return input < 10 ? '0'+input : input;
}

/**
 * Adding HashCode function for Strings
 * @returns 
 */
String.prototype.hashCode = function() {
    var hash = 0,
      i, chr;
    if (this.length === 0) return hash;
    for (i = 0; i < this.length; i++) {
      chr = this.charCodeAt(i);
      hash = ((hash << 5) - hash) + chr;
      hash |= 0; // Convert to 32bit integer
    }
    return hash;
}

