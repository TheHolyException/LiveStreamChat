package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.util.kaiutils.WebSocketServer;
import lombok.Getter;
import lombok.Setter;

public class WSClient {
    @Getter
    private WebSocketServer webSocket;
    @Getter @Setter
    private Long eventID;

    WSClient(WebSocketServer socket) {
        this.webSocket = socket;
    }
}