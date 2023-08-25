package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class WebChatServer extends Thread {

    private final Set<Connection> connections = Collections.synchronizedSet(new HashSet<>());
    private ServerSocket serverSocket;
    public WebChatServer() {
        String host = LiveStreamIRC.getProperties().getValue("WebChatHost");
        int port = Integer.parseInt(LiveStreamIRC.getProperties().getValue("WebChatPort"));
        log.info("Starting WebChat server on {} Port: {}", host, port);
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.start();
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                connections.add(new Connection(serverSocket.accept()));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
