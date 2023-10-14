package de.theholyexception.livestreamirc.webchat;

import lombok.Getter;
import lombok.Setter;
import org.java_websocket.WebSocket;

public class WSClient {
    @Getter
    private WebSocket webSocket;
    @Getter @Setter
    private Long eventID;

    WSClient(WebSocket socket) {
        this.webSocket = socket;
    }
}