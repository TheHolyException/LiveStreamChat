package de.theholyexception.livestreamirc.webchat;


import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class WebSocketServer implements Runnable {
    public static interface PacketReceivedEvent{
        void onReceived(byte[] data, WebSocketServer server);
        void onClosed(byte[] data, WebSocketServer server);
    }

    //private Socket socket;
    private PacketReceivedEvent event;
    private DataInputStream dis;
    private DataOutputStream dos;

    private volatile boolean alive = true;

    public WebSocketServer(InputStream is, OutputStream os, PacketReceivedEvent event) throws IOException {
        //this.socket = socket;
        this.event = event;
        dis = is instanceof DataInputStream ? (DataInputStream)is :  new DataInputStream(is);
        dos = os instanceof DataOutputStream ? (DataOutputStream)os :  new DataOutputStream(os);
        InitConnection(dis, dos);
        new Thread(this).start();
    }

    public WebSocketServer(Socket socket, PacketReceivedEvent event) throws IOException {
        //this.socket = socket;
        this.event = event;
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        InitConnection(dis, dos);
        new Thread(this).start();
    }

    public void send(String data) {
        try {
            writeFrameImpl(dos, data.getBytes(), 0x01);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    @SuppressWarnings("deprecation")
    private void InitConnection(DataInputStream dis, DataOutputStream dos) throws IOException {
        String r;
        String key_filter = "Sec-WebSocket-Key: ";
        String key = null;
        while ((r = dis.readLine()) != null) {
            if (r.length() < 4) {
                break;
            } else if (r.startsWith(key_filter)) {
                key = r.substring(key_filter.length());
            }
            // System.out.println(r);
        }

        key = WebSocketUtils.calculateResponseSecret(key);

        System.out.println(key);
        dos.write(("HTTP/1.1 101 Switching Protocols\n" + "Upgrade: websocket\r\n" + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + key + "\r\n" +
                // "Sec-WebSocket-Protocol: echo\r\n" +
                "\r\n").getBytes());
    }

    @Override
    public void run() {
        try {
            packetListener(dis, dos);
        } catch (Exception e) {
            e.printStackTrace();
            alive = false;
            event.onClosed(new byte[0], this);
        }
    }

    private void packetListener(DataInputStream dis, DataOutputStream dos) throws IOException {
        ByteArrayOutputStream multiPacketBuffer = null;
        while (alive) {
            int chr = dis.read();
            //System.out.println("chr-input: " + chr);
            if(chr == -1) return;
            //boolean FIN = (chr & 0x80) != 0;
            //int opCode = chr & 0x0F;
            boolean FIN = (chr & 128) != 0;
            int opCode = chr & 0x0F;
            //System.out.println("OPCode = " + opCode);
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
                    continue;
                }
            } else if(opCode == 9){
                writePong(dos, data);
            } else if(opCode == 8){
                event.onClosed(data, this);
                return;
            }
			/*
			byte[] in = readFrame(dis);
			System.out.println("event.onReceived(in); call");
			event.onReceived(in);
			System.out.println("event.onReceived(in); done");
			*/
        }
    }

    private void writePong(DataOutputStream dos, byte[] data) throws IOException {
        writeFrameImpl(dos, data, 0x0A);
    }

    private void writeFrameImpl(DataOutputStream dos, byte[] data, int opCode/*, boolean enableMasking*/) throws IOException {
        synchronized (dos) {
            WebSocketUtils.send(dos, data, opCode, false);
        }
    }

    public void close() {
        alive = false;
        event.onClosed(new byte[0], this);
    }

}
