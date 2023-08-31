package de.theholyexception.livestreamirc;

import com.google.api.services.youtube.YouTube;
import de.theholyexception.livestreamirc.ircprovider.YoutubeImpl;
import de.theholyexception.livestreamirc.util.*;
import de.theholyexception.livestreamirc.ircprovider.IRC;
import de.theholyexception.livestreamirc.ircprovider.TwitchImpl;
import de.theholyexception.livestreamirc.webchat.WebChatServer;
import de.theholyexception.livestreamirc.webchat.WebSocketHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class LiveStreamIRC {

    @Getter
    private static ConfigProperty properties;
    @Getter
    private static MySQLInterface sqlInterface;
    @Getter
    private static MessageProvider messageProvider;
    @Getter
    private static final Map<String, IRC> ircList = new HashMap<>(4);
    @Getter
    private static final Set<Channel> activeChannels = Collections.synchronizedSet(new HashSet<>());
    @Getter
    private static final Map<String, String> channelStreamerMap = new HashMap<>();
    private static LinkedBlockingQueue<Runnable> mainThreadQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        loadConfig();
        messageProvider = new MessageProvider();
        sqlInterface = new MySQLInterface(properties.getValue("DBHost")
                                        , Integer.parseInt(properties.getValue("DBPort"))
                                        , properties.getValue("DBUsername")
                                        , properties.getValue("DBPassword")
                                        , properties.getValue("DBDatabase"));
        sqlInterface.connect();
        ircList.put("Twitch", new TwitchImpl());
        ircList.put("Youtube", new YoutubeImpl());
        new WebChatServer();
        new WebSocketHandler();
        awaitAPIs();
        startDBPoll();
        // MUST BE THE LAST!!
        startMainThreadQueue();
    }

    private static void awaitAPIs() {
        try {
            while (!ircList.values().stream().allMatch(IRC::isConnected)) {
                Thread.sleep(50);
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Loads the configuration and sets default values if they are missing in the file
     */
    private static void loadConfig() {
        properties = new ConfigProperty(new File("./config.properties"), "");
        properties.createNewIfNotExists();
        properties.loadConfig();
        properties.setDefault("TwitchAPI", "wss://irc-ws.chat.twitch.tv:443");
        properties.setDefault("DBPollInterval", "10000");
        properties.setDefault("DBHost", "localhost");
        properties.setDefault("DBPort", "3306");
        properties.setDefault("DBUsername", "irc");
        properties.setDefault("DBPassword", "1234");
        properties.setDefault("DBDatabase", "livestreamirc");
        properties.setDefault("WebChatHost", "localhost");
        properties.setDefault("WebChatPort", "80");
        properties.setDefault("KeepMessagesMS", "86400000");
        properties.setDefault("TwitchToken", "oauth:");
        properties.setDefault("WebSocketHost", "localhost");
        properties.setDefault("WebSocketPort", "8080");
        properties.setDefault("WebSocketRequestURL", "localhost");
        properties.saveConfig();
    }

    /**
     * Starting DB Polling to get the information about events and channels that are currently streaming
     */
    private static void startDBPoll() {
        new Timer("DBPoll").schedule(new TimerTask() {
            @Override
            public void run() {

                try (ResultSet result = getSqlInterface().executeQuery("call getActiveStreams")) {
                    Set<Channel> localChannelCache = new HashSet<>();
                    while (result.next()) {
                        Channel channel = Channel.fromResultSet(result);
                        long timestamp = result.getLong("Timestamp");
                        long duration = result.getLong("Duration");
                        long current = System.currentTimeMillis()/1000;

                        if (!(current > timestamp && current < timestamp+duration)) {
                            log.debug("Out of time-range");
                            continue;
                        }

                        if (!activeChannels.contains(channel)) {
                            log.debug("Adding channel to " + channel.platform() + " name: " + channel);
                            activeChannels.add(channel);
                            channel.joinChannel();
                        }

                        String c = channel.platform()+"_"+channel.channelName();
                        if (!(channelStreamerMap.containsKey(c)) || channelStreamerMap.containsValue(channel.streamer())) {
                            channelStreamerMap.put(c, channel.streamer());
                        }
                        localChannelCache.add(channel);
                    }

                    // Iterates over all channels that are no longer registered for an active event
                    new HashSet<>(activeChannels).stream().filter(e -> !localChannelCache.contains(e)).forEach(channel -> {
                        activeChannels.remove(channel);
                        log.debug("We lost " + channel);
                        channel.leaveChannel();
                    });

                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }, 0, Integer.parseInt(properties.getValue("DBPollInterval")));
    }

    public static void executeInMainThread(Runnable runnable) {
        mainThreadQueue.add(runnable);
    }

    private static void startMainThreadQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (mainThreadQueue.iterator().hasNext()) mainThreadQueue.iterator().next().run();
                if (mainThreadQueue.isEmpty()) Thread.sleep(50);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
