package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.webchat.WSClient;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlTable;

import java.util.*;

@Slf4j
public class MessageProvider {

    private final Map<Long, List<Message>> messageCache = new HashMap<>();
    private final Map<WSClient, Long> webSocketSubscribers = Collections.synchronizedMap(new HashMap<>());
    private final long maxCachedMessages;
    private final boolean debug;

    public MessageProvider() {
        TomlTable engineConfig = LiveStreamIRC.getCfg().getTable("engine");
        maxCachedMessages = Optional.ofNullable(engineConfig.getLong("maxCachedMessages")).orElse(150L);
        debug = Optional.ofNullable(engineConfig.getBoolean("debug")).orElse(false);
    }

    public void addMessage(Message message) {
        if (debug) log.debug("Adding message: " + message + " count: " + (messageCache.get(message.channel().event()) == null ? 0 : messageCache.get(message.channel().event()).size()));
        List<Message> messageList = messageCache.computeIfAbsent(message.channel().event(), key -> new ArrayList<>());
        messageList.add(message);
        if (messageList.size() > maxCachedMessages) messageList.remove(0);
        webSocketSubscribers.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == message.channel().event())
                .forEach(entry -> sendMessage(entry.getKey(), message));
    }

    public void addSubscriber(WSClient client) {
        webSocketSubscribers.put(client, client.getEventID());
    }

    public void removeSubscriber(WSClient socket) {
        webSocketSubscribers.remove(socket);
    }

    public void sendCachedMessages(WSClient client, int count) {
        StringBuilder builder = new StringBuilder();
        List<Message> messages = messageCache.get(client.getEventID());
        if (messages == null || messages.isEmpty()) return;
        messages.stream()
                .skip(Math.max(0, messages.size()-count))
                .forEach(message -> builder.append(
                        String.format("%s,%s,%s,%s,%s%n",
                        message.b64Username(),
                        message.b64Message(),
                        message.timestamp(),
                        message.channel().platform(),
                        message.channel().streamer())));
        client.getWebSocket().send(builder.toString());
    }

    public void sendMessage(WSClient socket, Message message) {
        socket.getWebSocket().send(
                String.format("%s,%s,%s,%s,%s%n",
                        message.b64Username(),
                        message.b64Message(),
                        message.timestamp(),
                        message.channel().platform(),
                        message.channel().streamer()));
    }

}
