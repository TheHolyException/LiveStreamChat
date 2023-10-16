package de.theholyexception.livestreamirc.ircprovider;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.Channel;
import de.theholyexception.livestreamirc.util.Message;
import de.theholyexception.livestreamirc.webchat.WebSocket;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

 @Slf4j
public class TwitchImpl implements IRC {

    private final Pattern usernamePattern = Pattern.compile(":(\\w+)!");
    private final Pattern channelPattern = Pattern.compile("PRIVMSG #(\\w+) :(.*)");
    private final Pattern isMessagePattern = Pattern.compile("PRIVMSG #");
    private WebSocket client;
    @Getter
    private boolean connected = false;

    private final TomlTable twitchConfig;
    private final Map<String, Channel> channelMap = Collections.synchronizedMap(new HashMap<>());


    public TwitchImpl() {
        twitchConfig = LiveStreamIRC.getCfg().getTable("twitch");
        String twitchAPI = Objects.requireNonNull(twitchConfig.getString("api"));

        try {
            client = WebSocket.open(twitchAPI, new WebSocket.WebSocketPacket() {
                @Override
                public void onBinaryPacketReceived(byte[] data) {
                }

                @Override
                public void onTextPacketReceived(String s) {
                    if (s.startsWith("PING")) {
                        client.sendPacket(s.replace("PING", "PONG"));
                        return;
                    }

                    if (isMessagePattern.matcher(s).find()) {
                        Matcher username = usernamePattern.matcher(s);
                        Matcher other = channelPattern.matcher(s);

                        if (!username.find() || !other.find()) {
                            log.error("Failed to parse messagestring!!!");
                            log.debug("Matchers: username:{} other:{}", username.find(), other.find());
                            log.debug("Message: {}", s.trim());
                            return;
                        }

                        Channel channel = channelMap.get(other.group(1));

                        Message message = new Message(channel, username.group(1), other.group(2), System.currentTimeMillis());
                        LiveStreamIRC.getMessageProvider().addMessage(message);
                    }
                }

                @Override
                public void notifyConnectionClose() {
                    log.warn("Connection to TwitchIRC Lost");
                    connected = false;
                    LiveStreamIRC.executeInMainThread(() -> reconnect());
                }

                @Override
                public void notifyConnectionFailure(String message) {
                    log.error("TwitchIRC Error: " + message);
                }
            });
            log.info("Connected to TwitchIRC");
            client.sendPacket("PASS " + twitchConfig.getString("token"));
            client.sendPacket("NICK rbu_irc");
            connected = true;
        } catch (IOException e) {
            connected = false;
            throw new RuntimeException(e);
        }
        reconnect();

    }

    @Override
    public void joinChannel(Channel channel) {
        client.sendPacket("JOIN #"+channel.streamURL());
        channelMap.put(channel.streamURL(), channel);
    }

    @Override
    public void leaveChannel(Channel channel) {
        channelMap.remove(channel.streamURL());
        client.sendPacket("PART #"+channel.streamURL());
    }

    private void reconnect() {
        while (!connected) {
            try {
                log.info("Connecting ...");
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

 }
