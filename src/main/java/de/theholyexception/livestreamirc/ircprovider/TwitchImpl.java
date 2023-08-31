package de.theholyexception.livestreamirc.ircprovider;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

 @Slf4j
public class TwitchImpl implements IRC {

    private final Pattern usernamePattern = Pattern.compile(":(\\w+)!");
    private final Pattern channelPattern = Pattern.compile("PRIVMSG #(\\w+) :(.*)");
    private final Pattern isMessagePattern = Pattern.compile("PRIVMSG #");
    private WebSocketClient client;
    @Getter
    private boolean connected = false;


    public TwitchImpl() {
        URI twitchAPI;
        try {
            twitchAPI = new URI(LiveStreamIRC.getProperties().getValue("TwitchAPI"));
            LiveStreamIRC.getProperties().saveConfig();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        client = new WebSocketClient(twitchAPI) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                log.info("Connected to TwitchIRC");
                client.send("PASS " + LiveStreamIRC.getProperties().getValue("TwitchToken"));
                client.send("NICK rbu_irc");
                connected = true;
            }

            @Override
            public void onMessage(String s) {
                if (s.startsWith("PING")) {
                    client.send(s.replace("PING", "PONG"));
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

                    Message message = new Message("Twitch", other.group(1), username.group(1), other.group(2), System.currentTimeMillis());
                    LiveStreamIRC.getMessageProvider().addMessage(message);
                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                log.warn("Connection to TwitchIRC Lost");
                connected = false;
                LiveStreamIRC.executeInMainThread(this::reconnect);
            }

            @Override
            public void onError(Exception e) {
                if (log.isDebugEnabled()) e.printStackTrace();
                log.error("TwitchIRC Error: " + e.getMessage());
            }
        };

        reconnect();

    }

    @Override
    public void joinChannel(String channel) {
        client.send("JOIN #"+channel);
    }

    @Override
    public void leaveChannel(String channel) {
        client.send("PART #"+channel);
    }

    private void reconnect() {
        while (!connected) {
            try {
                if (!client.connectBlocking()) client.connect();
                log.info("Connecting ...");
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

 }
