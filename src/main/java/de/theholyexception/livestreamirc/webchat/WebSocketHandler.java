package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.Channel;
import de.theholyexception.livestreamirc.util.Message;
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
        optionalWSClient.ifPresent(wsClient -> wsClients.remove(wsClient));
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        Optional<WSClient> optionalWSClient = wsClients.stream().filter(ws -> ws.getWebSocket().equals(webSocket)).findFirst();
        optionalWSClient.ifPresent(wsClient -> {
            if (wsClient.getStreamer() != null) log.warn("WebSocket subscribed to new streamer");
            String streamer = s.toLowerCase();
            wsClient.setStreamer(streamer);

            Optional<Channel> optionalChannel = LiveStreamIRC.getActiveChannels().stream().filter(channel -> channel.streamer().equals(streamer)).findFirst();
            if (optionalChannel.isEmpty()) {
                log.error("Failed to get channel for streamer {}", streamer);
                return;
            }

            LiveStreamIRC.getMessageProvider().addSubscriber(webSocket, wsClient.getStreamer());
            Set<Message> messages = LiveStreamIRC.getMessageProvider().getMessages(wsClient.getStreamer());
            StringBuilder builder = new StringBuilder();
            for (Message message : messages) {
                builder.append(String.format("%s,%s,%s,%s,%s\n", message.timestamp(), message.platform(), message.channel(), message.b64Username(), message.b64Message()));
            }
            webSocket.send(builder.toString());
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
