package de.theholyexception.livestreamirc;

import de.theholyexception.livestreamirc.util.Channel;
import de.theholyexception.livestreamirc.util.ConfigProperty;
import de.theholyexception.livestreamirc.util.MySQLInterface;
import de.theholyexception.livestreamirc.ircprovider.IRC;
import de.theholyexception.livestreamirc.ircprovider.TwitchImpl;
import de.theholyexception.livestreamirc.util.MessageProvider;
import de.theholyexception.livestreamirc.webchat.WebChatServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
        startDBPoll();
        new WebChatServer();
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
        properties.saveConfig();
    }

    /**
     * Starting DB Polling to get the information about events and channels that are currently streaming
     */
    private static void startDBPoll() {
        Set<Channel> channelCache = Collections.synchronizedSet(new HashSet<>());
        new Timer().schedule(new TimerTask() {
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

                        if (!channelCache.contains(channel)) {
                            log.debug("Adding channel to " + channel.platform() + " name: " + channel);
                            channelCache.add(channel);
                            channel.joinChannel();
                        }
                        localChannelCache.add(channel);
                    }

                    // Iterates over all channels that are no longer registered for an active event
                    new HashSet<>(channelCache).stream().filter(e -> !localChannelCache.contains(e)).forEach(channel -> {
                        channelCache.remove(channel);
                        log.debug("We lost " + channel);
                        channel.leaveChannel();
                    });

                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                sqlInterface.getExecutorHandler().awaitGroup(1);
            }
        }, 2000, Integer.parseInt(properties.getValue("DBPollInterval")));
    }
}
