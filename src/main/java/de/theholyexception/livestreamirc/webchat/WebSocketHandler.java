package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.Channel;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.tomlj.TomlTable;

import java.net.InetSocketAddress;
import java.util.*;

@Slf4j
public class WebSocketHandler extends WebSocketServer {

    Set<WSClient> wsClients = new HashSet<>();



    public WebSocketHandler(TomlTable webSocketConfig) {
        super(new InetSocketAddress(Objects.requireNonNull(webSocketConfig.getString("host")),
                Math.toIntExact(Optional.ofNullable(webSocketConfig.getLong("port")).orElse(8000L))));
        this.start();
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        wsClients.add(new WSClient(webSocket));
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        Optional<WSClient> optionalWSClient = wsClients.stream().filter(ws -> ws.getWebSocket().equals(webSocket)).findFirst();
        optionalWSClient.ifPresent(wsClient -> {
            wsClients.remove(wsClient);
            LiveStreamIRC.getMessageProvider().removeSubscriber(wsClient);
        });
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        Optional<WSClient> optionalWSClient = wsClients.stream().filter(ws -> ws.getWebSocket().equals(webSocket)).findFirst();
        optionalWSClient.ifPresent(wsClient -> {
            if (wsClient.getEventID() != null) log.warn("WebSocket subscribed to new streamer");
            long eventID = Long.parseLong(s.split(",")[0]);
            int maxMessages = Integer.parseInt(s.split(",")[1]);
            wsClient.setEventID(eventID);

            Optional<Channel> optionalChannel = LiveStreamIRC.getActiveChannels().stream().filter(channel -> channel.event() == eventID).findFirst();
            if (optionalChannel.isEmpty()) {
                log.error("Failed to get channel for streamer {}", eventID);
                return;
            }

            LiveStreamIRC.getMessageProvider().addSubscriber(wsClient);
            LiveStreamIRC.getMessageProvider().sendCachedMessages(wsClient, maxMessages);
        });
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        log.error("WebSocket error: " + e.getMessage());
        if (log.isDebugEnabled()) e.printStackTrace();
    }

    @Override
    public void onStart() {
        log.info("WebSocket server started on {}", this.getAddress());
    }

}
