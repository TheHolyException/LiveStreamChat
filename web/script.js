let socket = new WebSocket("###WEBSOCKETURL###");
let messageContainer = document.getElementById("messagecontainer");

var matcherEventid = /event-id=([^&#=]*)/.exec(window.location.search);
var eventid = matcherEventid[1];

var matcherMaxMessages = /max-messages=([^&#=]*)/.exec(window.location.search);
var maxMessages = matcherMaxMessages == null ? 10 : matcherMaxMessages[1];


let youtubeLogo = "https://i.pinimg.com/originals/de/1c/91/de1c91788be0d791135736995109272a.png"
let twitchLogo = "https://pngimg.com/uploads/twitch/twitch_PNG48.png"


socket.onopen = function(event) {
    socket.send(eventid + "," + maxMessages);
}

socket.onmessage = function(event) {
    let messages = event.data.split("\n");

    messages.forEach(message => {
        let segment = message.split(",");
        if (segment[1]) {
            let username = b64DecodeUnicode(segment[0]);
            let content = b64DecodeUnicode(segment[1]);

            let timestamp = segment[2];
            let platform = segment[3];
            let streamer = segment[4];

            let msgPackage = document.createElement("div");
            let msgElement = document.createElement("p");
            //msgElement.appendChild(channel + " - " + content);
            msgElement.innerHTML = username + " - " + content + " - " + platform;
            let icon = document.createElement("img");
            icon.setAttribute("src", platform == "Twitch" ? twitchLogo : youtubeLogo);
            msgPackage.appendChild(icon);
            msgPackage.appendChild(msgElement);
            msgPackage.classList.add("msgPackage");
            messageContainer.appendChild(msgPackage);
        }
    });
    let len = messageContainer.childNodes.length;

    if (len > maxMessages) { 
        for (let i = 0; i < len-maxMessages; i ++) {
            let node = messageContainer.childNodes[i];
            messageContainer.removeChild(node);
        }
    }
    
}

function b64DecodeUnicode(str) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(str).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
}