package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;

@Slf4j
public class WebChatServer extends Thread {

    private final Set<Connection> connections = Collections.synchronizedSet(new HashSet<>());
    private ServerSocket serverSocket;
    public WebChatServer() {
        TomlTable ircData = LiveStreamIRC.getCfg().getTable("IRC");
        String host = ircData.getString("host");
        int port = Math.toIntExact(Optional.ofNullable(ircData.getLong("port")).orElse(80L));
        log.info("Starting WebChat server on {} Port: {}", host, port);
        try {
            serverSocket = new ServerSocket(port);//, 50, InetAddress.getByName(host));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.start();
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                new Connection(serverSocket.accept());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
