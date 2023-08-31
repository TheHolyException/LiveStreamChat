package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;

import java.util.*;

@Slf4j
public class MessageProvider extends Thread {

    private final Map<String, Set<Message>> messageCache = new HashMap<>();
    private final Map<WebSocket, String> webSocketSubscribers = Collections.synchronizedMap(new HashMap<>());

    public MessageProvider() {
        this.start();
    }

    @Override
    public void run() {
        try {Thread.sleep(4000);}catch(Exception e){}
        addMessage(new Message("Twitch", "redstonebroadcastunion", "TheHolyException", "Hello World!", System.currentTimeMillis()));
        addMessage(new Message("Twitch", "redstonebroadcastunion", "TheHolyException", "Hello World2!", System.currentTimeMillis()));
        addMessage(new Message("Twitch", "redstonebroadcastunion", "TheHolyException", "Hello World!", System.currentTimeMillis()));
        long keepMessageTime = Long.parseLong(LiveStreamIRC.getProperties().getValue("KeepMessagesMS"));
        while (!isInterrupted()) {
            long current = System.currentTimeMillis();
            try {
                new HashMap<>(messageCache).forEach((key, value) -> value
                        .stream()
                        .filter(message -> message.timestamp() + keepMessageTime < current)
                        .forEach(this::removeMessage));
                Thread.sleep(10000);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public Set<Message> getMessages(String streamer) {
        return messageCache.get(streamer);
    }

    public void addMessage(Message message) {
        String streamer = LiveStreamIRC.getChannelStreamerMap().get(message.platform()+"_"+message.channel());
        log.debug("Adding message: " + message);
        messageCache.computeIfAbsent(streamer, key -> new HashSet<>()).add(message);

        webSocketSubscribers.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(streamer)).forEach(entry -> {
            sendMessage(entry.getKey(), message);
        });
    }

    private void removeMessage(Message message) {
        Set<Message> messages = messageCache.get(message.channel());
        assert messages != null;
        messages.remove(message);
        log.debug("Removed message: " + message);
    }

    public void addSubscriber(WebSocket socket, String streamer) {
        webSocketSubscribers.put(socket, streamer);
    }

    public void removeSubscriber(WebSocket socket) {
        webSocketSubscribers.remove(socket);
    }

    public void sendMessage(WebSocket socket, Message... messages) {
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            builder.append(String.format("%s,%s,%s,%s,%s\n", message.timestamp(), message.platform(), message.channel(), message.b64Username(), message.b64Message()));
        }
        socket.send(builder.toString());
    }

}
