package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MessageProvider extends Thread {

    private final Map<String, Set<Message>> messageCache = new HashMap<>();

    public MessageProvider() {
        this.start();
        //addMessage(new Message("Twitch", "redstonebroadcastunion", "TheHolyException", "Hello World!", System.currentTimeMillis()));
        //addMessage(new Message("Twitch", "redstonebroadcastunion", "TheHolyException", "Hello World2!", System.currentTimeMillis()));
        //addMessage(new Message("Twitch", "redstonebroadcastunion", "TheHolyException", "Hello World!", System.currentTimeMillis()));
    }

    @Override
    public void run() {
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

    public Set<Message> getMessages(String channel) {
        return messageCache.get(channel);
    }

    public void addMessage(Message message) {
        log.debug("Adding message: " + message);
        messageCache.computeIfAbsent(message.channel(), key -> new HashSet<>()).add(message);
    }

    private void removeMessage(Message message) {
        Set<Message> messages = messageCache.get(message.channel());
        assert messages != null;
        messages.remove(message);
        log.debug("Removed message: " + message);
    }

}
