let socket = new WebSocket("###WEBSOCKETURL###");
let streamer = document.getElementById("cfg_streamer").innerHTML;
let messageContainer = document.getElementById("messagecontainer");


socket.onopen = function(event) {
    socket.send(streamer);
}

socket.onmessage = function(event) {
    let messages = event.data.split("\n");
    console.log(event.data);
    
    messages.forEach(message => {
        let segment = message.split(",");
        let timestamp = segment[0];
        let platform = segment[1];
        let channel = segment[2];
        let username = atob(segment[3]);
        let content = atob(segment[4]);

        let msgElement = document.createElement("p");
        //msgElement.appendChild(channel + " - " + content);
        msgElement.innerHTML = channel + " - " + content;
        messageContainer.appendChild(msgElement);

    });
}