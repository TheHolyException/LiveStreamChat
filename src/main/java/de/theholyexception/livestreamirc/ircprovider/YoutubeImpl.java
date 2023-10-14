package de.theholyexception.livestreamirc.ircprovider;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.Channel;
import de.theholyexception.livestreamirc.util.Message;
import de.theholyexception.livestreamirc.util.YtChatReader;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlTable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class YoutubeImpl extends Thread implements IRC {

    Map<Channel, YtChatReader> chatReader = new HashMap<>();
    private final TomlTable youtubeConfig;

    public YoutubeImpl() {
        youtubeConfig = LiveStreamIRC.getCfg().getTable("youtube");
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    for (Map.Entry<Channel, YtChatReader> entry : chatReader.entrySet()) {
                        List<Message> messages = entry.getValue().read();
                        for (Message message : messages) {
                            LiveStreamIRC.getMessageProvider().addMessage(message);
                        }

                    }

                    Thread.sleep(Objects.requireNonNull(youtubeConfig.getLong("requestInterval")));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        t.setName("Youtube ChatResolver");
        t.start();
    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void joinChannel(Channel channel) {
        if (chatReader.containsKey(channel)) log.error("Failed to join YouTube channel {} - already connected", channel);
        try {
            chatReader.put(channel, new YtChatReader(channel));
        } catch (Exception ex) {
            log.error("Failed to connect to channel {} - error: {}", channel, ex.getMessage());
        }
    }

    @Override
    public void leaveChannel(Channel channel) {
        chatReader.remove(channel);
    }
}