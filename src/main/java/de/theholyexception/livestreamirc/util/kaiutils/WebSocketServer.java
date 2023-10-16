package de.theholyexception.livestreamirc.util.kaiutils;


import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class WebSocketServer {
    public static interface PacketReceivedEvent{
        void onOpen(WebSocketServer server);
        void onReceived(byte[] data, WebSocketServer server);
        void onClosed(byte[] data, WebSocketServer server);
    }

    private final PacketReceivedEvent event;
    private final DataInputStream dis;
    private final DataOutputStream dos;

    private volatile boolean alive = true;

    public WebSocketServer(InputStream is, OutputStream os, PacketReceivedEvent event) throws IOException {
        this.event = event;
        dis = is instanceof DataInputStream ? (DataInputStream)is :  new DataInputStream(is);
        dos = os instanceof DataOutputStream ? (DataOutputStream)os :  new DataOutputStream(os);
        initConnection(dis, dos);
        try {
            event.onOpen(this);
            packetListener(dis, dos);
        } catch (Exception e) {
            e.printStackTrace();
            alive = false;
            event.onClosed(new byte[0], this);
        }
    }

    public WebSocketServer(Socket socket, PacketReceivedEvent event) throws IOException {
        this.event = event;
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        initConnection(dis, dos);
        try {
            event.onOpen(this);
            packetListener(dis, dos);
        } catch (Exception e) {
            e.printStackTrace();
            alive = false;
            event.onClosed(new byte[0], this);
        }
    }

    public void send(String data) {
        try {
            writeFrameImpl(data.getBytes(StandardCharsets.UTF_8), 0x01);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void send(byte[] data) {
        try {
            writeFrameImpl(data, 0x02);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    @SuppressWarnings("deprecation")
    private void initConnection(DataInputStream dis, DataOutputStream dos) throws IOException {
        String r;
        String keyFilter = "Sec-WebSocket-Key: ";
        String key = null;
        while ((r = dis.readLine()) != null) {
            if (r.length() < 4) {
                break;
            } else if (r.startsWith(keyFilter)) {
                key = r.substring(keyFilter.length());
            }
        }

        key = WebSocketUtils.calculateResponseSecret(key);

        dos.write(("HTTP/1.1 101 Switching Protocols\n" + "Upgrade: websocket\r\n" + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + key + "\r\n" +
                "\r\n").getBytes());
        dos.flush();
    }


    private void packetListener(DataInputStream dis, DataOutputStream dos) throws IOException {
        ByteArrayOutputStream multiPacketBuffer = null;
        while (alive) {
            int chr = dis.read();
            if(chr == -1) return;
            boolean FIN = (chr & 128) != 0;
            int opCode = chr & 0x0F;
            byte[] data = WebSocketUtils.readNextFrame(dis);
            if(opCode >= 0 && opCode <= 2){
                if(!FIN){
                    if(multiPacketBuffer == null) multiPacketBuffer = new ByteArrayOutputStream();
                    multiPacketBuffer.write(data);
                    continue;
                } else {
                    if(multiPacketBuffer == null) {
                        event.onReceived(data, this);
                        continue;
                    }
                    multiPacketBuffer.write(data);
                    event.onReceived(multiPacketBuffer.toByteArray(), this);
                    multiPacketBuffer = null;
                }
            } else if(opCode == 9){
                writeFrameImpl(data, 0x0A); // write pong
            } else if(opCode == 8){
                event.onClosed(data, this);
                return;
            }
        }
    }

    private void writeFrameImpl(byte[] data, int opCode/*, boolean enableMasking*/) throws IOException {
        synchronized (dos) {
            WebSocketUtils.send(dos, data, opCode, false);
        }
    }

    public void close() {
        alive = false;
        event.onClosed(new byte[0], this);
    }

}
