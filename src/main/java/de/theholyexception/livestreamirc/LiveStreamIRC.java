package de.theholyexception.livestreamirc;

import de.theholyexception.livestreamirc.ircprovider.YoutubeImpl;
import de.theholyexception.livestreamirc.util.*;
import de.theholyexception.livestreamirc.ircprovider.IRC;
import de.theholyexception.livestreamirc.ircprovider.TwitchImpl;
import de.theholyexception.livestreamirc.webchat.WebChatServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class LiveStreamIRC {

    @Getter
    private static ConfigProvider cfg;
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
    private static final LinkedBlockingQueue<Runnable> mainThreadQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        loadConfig();
        messageProvider = new MessageProvider();
        sqlInterface = new MySQLInterface(cfg.getTable("database"));
        sqlInterface.connect();
        ircList.put("Twitch", new TwitchImpl());
        ircList.put("YouTube", new YoutubeImpl());
        new WebChatServer();
        awaitAPIs();
        startDBPoll();

        log.info("---------------------------------------");
        log.info("LiveStream IRC");
        log.info("System Ready!");
        log.info("---------------------------------------");

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
        try {
            TomlParseResult result = Toml.parse(Path.of("./config.toml"));
            result.errors().forEach(Throwable::printStackTrace);
            cfg = new ConfigProvider(result);
        } catch (IOException ex) {
            if (log.isDebugEnabled()) ex.printStackTrace();
            log.error("Failed to load Configuration - " + ex.getMessage());
            System.exit(2);
        }
    }

    /**
     * Starting DB Polling to get the information about events and channels that are currently streaming
     */
    private static void startDBPoll() {
        long pollInterval = Optional.ofNullable(cfg.getTable("database").getLong("poll-interval")).orElse(2000L);
        new Timer("DBPoll").schedule(new TimerTask() {
            @Override
            public void run() {
                try (ResultSet result = getSqlInterface().executeQuery("call getActiveStreams2")) {
                    if (result == null) return;
                    Set<Channel> localChannelCache = new HashSet<>();
                    while (result.next()) {
                        Channel channel = Channel.fromResultSet(result);

                        if (!activeChannels.contains(channel)) {
                            log.debug("Adding channel to " + channel.platform() + " name: " + channel);
                            activeChannels.add(channel);
                            channel.joinChannel();
                        }

                        String c = channel.platform()+"_"+channel.streamURL();
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
        }, 0, pollInterval);
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
