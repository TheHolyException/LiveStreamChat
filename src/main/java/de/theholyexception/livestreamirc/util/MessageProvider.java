package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.webchat.WSClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MessageProvider extends Thread {

    private final Map<Long, Set<Message>> messageCacheEvent = new HashMap<>();
    private final Map<WSClient, Long> webSocketSubscribers = Collections.synchronizedMap(new HashMap<>());

    public MessageProvider() {
        this.start();
    }

    @Override
    public void run() {
        try {Thread.sleep(4000);}catch(Exception e){e.printStackTrace();}
        long keepMessageTime = Optional.ofNullable(LiveStreamIRC.getCfg().getTable("engine").getLong("keepMessagesMS")).orElse(86400000L);
        while (!isInterrupted()) {
            long current = System.currentTimeMillis();
            try {
                new HashMap<>(messageCacheEvent).forEach((key, value) -> value
                        .stream()
                        .filter(message -> message.timestamp() + keepMessageTime < current)
                        .forEach(this::removeMessage));
                Thread.sleep(10000);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public Set<Message> getMessages(Long eventID) {
        return messageCacheEvent.get(eventID);
    }

    public void addMessage(Message message) {
        log.debug("Adding message: " + message);
        messageCacheEvent.computeIfAbsent(message.channel().event(), key -> new HashSet<>()).add(message);

        webSocketSubscribers.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == message.channel().event())
                .forEach(entry -> sendMessage(entry.getKey(), message));
    }

    private void removeMessage(Message message) {
        Set<Message> messages = messageCacheEvent.get(message.channel().event());
        assert messages != null;
        messages.remove(message);
        log.debug("Removed message: " + message);
    }

    public void addSubscriber(WSClient client) {
        webSocketSubscribers.put(client, client.getEventID());
    }

    public void removeSubscriber(WSClient socket) {
        webSocketSubscribers.remove(socket);
    }

    public void sendCachedMessages(WSClient client, int count) {
        StringBuilder builder = new StringBuilder();
        //messageCacheEvent.get(eventID).stream().reve.limit(count)
        Set<Message> messages = messageCacheEvent.get(client.getEventID());
        messages.stream()
                .skip(Math.max(0, messages.size()-count))
                .forEach(message -> {
                    builder.append(String.format("%s,%s,%s,%s,%s%n", message.b64Username(), message.b64Message(), message.timestamp(), message.channel().platform(), message.channel().streamer()));
                });
        client.getWebSocket().send(builder.toString());
    }

    public void sendMessage(WSClient socket, Message message) {
        socket.getWebSocket().send(String.format("%s,%s,%s,%s,%s%n", message.b64Username(), message.b64Message(), message.timestamp(), message.channel().platform(), message.channel().streamer()));
    }

}
