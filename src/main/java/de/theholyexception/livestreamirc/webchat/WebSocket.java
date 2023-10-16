package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.util.kaiutils.WebSocketUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WebSocket {
	public static interface WebSocketPacket{
		void onBinaryPacketReceived(byte[] data);
		void onTextPacketReceived(String data);
		void notifyConnectionClose();
		void notifyConnectionFailure(String message);
	}
	
	public static WebSocket open(String url, WebSocketPacket callback) throws IOException{
		return open(url, callback, url.startsWith("wss://"), null);
	}
	
	public static WebSocket open(String url, WebSocketPacket callback, HashMap<String, String> additional_headers) throws IOException{
		return open(url, callback, url.startsWith("wss://"), additional_headers);
	}
	
	public static WebSocket open(String url, WebSocketPacket callback, boolean useSSL, HashMap<String, String> additional_headers) throws IOException {
		if(url.startsWith("wss://")) url = url.substring(6);
		else if(url.startsWith("ws://")) url = url.substring(5);
		int a = url.indexOf('/');
		String serverName;
		String targetPath;
		if(a == -1){
			serverName = url;
			targetPath = "/";
		} else {
			serverName = url.substring(0, a);
			targetPath = url.substring(a);
		}

		String ip;
		int port;
		a = serverName.indexOf(':');
		if(a == -1){
			ip = serverName;
			port = useSSL ? 443 : 80;
		} else {
			ip = serverName.substring(0, a);
			port = Integer.parseInt(serverName.substring(a + 1));
		}
		
		Socket socket = useSSL ? /*SSLSocketFactory.getDefault()*/getSocketFactory().createSocket(ip, port) : new Socket(ip, port);
		return new WebSocket(socket, serverName, targetPath, callback, additional_headers);
	}
	
	//based on https://stackoverflow.com/questions/12060250/ignore-ssl-certificate-errors-with-java
	public static SSLSocketFactory getSocketFactory() throws IOException {
		TrustManager[] certs = new TrustManager[] { new X509TrustManager() {
			@Override public X509Certificate[] getAcceptedIssuers() { return null; }
			@Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
			@Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
		}};
	    
	    SSLContext ctx = null;
	    try {
	        ctx = SSLContext.getInstance("SSL");
	        ctx.init(null, certs, new SecureRandom());
	    } catch (java.security.GeneralSecurityException ex) {
	    	throw new IOException(ex.getMessage());
	    }
	    return ctx.getSocketFactory();
	}
	
	private Socket socket;
	private DataInputStream dis;
	private DataOutputStream dos;
	private WebSocketPacket callback;
	private volatile boolean alive = true;
	
	public WebSocket(Socket socket, String ip, String targetPath, WebSocketPacket callback, HashMap<String, String> additional_headers) throws IOException {
		this.socket = socket;
		this.callback = callback;
		dis = new DataInputStream(socket.getInputStream());
		dos = new DataOutputStream(socket.getOutputStream());
		if(!handleHttpProtocolSwitch(ip, targetPath, additional_headers)) throw new IOException("Can't ethablish websocket connection");
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					receivePackets();
				}catch(Throwable t){
					t.printStackTrace();
					if(alive) callback.notifyConnectionFailure(t.getMessage());
					alive = false;
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}
	
	public void sendPacket(String msg) {
		try{
			send(msg.getBytes(StandardCharsets.UTF_8), 0x1);
		}catch(IOException t){
			t.printStackTrace();
			alive = false;
			callback.notifyConnectionFailure(t.getMessage());
		}
	}
	
	public void sendPacket(byte[] data) {
		try{
			send(data, 0x2);
		}catch(IOException t){
			t.printStackTrace();
			alive = false;
			callback.notifyConnectionFailure(t.getMessage());
		}
	}
	
	public void close(){
		try{
			send(new byte[0], 0x8);
			alive = false;
			socket.close();
		}catch(IOException t){
			t.printStackTrace();
			alive = false;
			callback.notifyConnectionFailure(t.getMessage());
		}
	}
	
	private void receivePackets() throws Exception {
		ByteArrayOutputStream multiPacketBuffer = null;
		while (alive) {
			int chr = dis.read();
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
				} else {
					if(multiPacketBuffer == null) {
						if(opCode == 1){
							callback.onTextPacketReceived(new String(data, StandardCharsets.UTF_8));
						} else if(opCode == 2){
							callback.onBinaryPacketReceived(data);
						}
						continue;
					}
					multiPacketBuffer.write(data);
					
					if(opCode == 1){
						callback.onTextPacketReceived(new String(multiPacketBuffer.toByteArray(), StandardCharsets.UTF_8));
					} else if(opCode == 2){
						callback.onBinaryPacketReceived(multiPacketBuffer.toByteArray());
					}
					
					multiPacketBuffer = null;
				}
			} else if(opCode == 9){
				writePong(data);
			} else if(opCode == 8){
				if(data.length > 0) callback.notifyConnectionFailure(new String(data));
				callback.notifyConnectionClose();
				return;
			}
		}
	}
	
	private void writePong(byte[] data) throws IOException {
		send(data, 0x0A);
	}

	@SuppressWarnings("deprecation")
	private boolean handleHttpProtocolSwitch(String ip, String targetPath, HashMap<String, String> additional_headers) throws IOException {
		String challengeKey = generateRandomChallenge(16);
		String request = new StringBuilder(2048).append("GET ").append(targetPath).append(" HTTP/1.1\r\n")
				.append("Accept: */*\r\n")
				.append("Connection: keep-alive, Upgrade\r\n")
				.append("DNT: 1\r\n")
				.append("Host: ").append(ip).append("\r\n")
				.append("Pragma: no-cache\r\n")
				.append("Sec-Fetch-Dest: empty\r\n")//websocket
				.append("Sec-Fetch-Mode: websocket\r\n")
				.append("Sec-Fetch-Site: same-site\r\n")
				.append("Sec-WebSocket-Key: ").append(challengeKey).append("\r\n")
				.append("Sec-WebSocket-Version: 13\r\n")
				.append("Upgrade: websocket\r\n")
				.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0\r\n\r\n")
				.toString();
		
		if(additional_headers != null){
			for(Entry<String, String> e : additional_headers.entrySet()){
				request += e.getKey() + ": " + e.getValue() + "\r\n";
			}
		}
		
		dos.write(request.getBytes());
		dos.flush();
		
		String l = dis.readLine();
		String[] header = l.split(" ");
		if(!header[1].equals("101")){
			System.err.println(l);
			while((l = dis.readLine()) != null){
				if(l.length() < 4) break;
				System.err.println(l);
			}
			return false;
		}
		
		String keyFilter = "Sec-WebSocket-Accept: ";
		String keyResponse = null;
		while((l = dis.readLine()) != null){
			if(l.length() < 4){
				break;
			} else if (l.startsWith(keyFilter)) {
				keyResponse = l.substring(keyFilter.length());
			}
		}
		
		runAntiReplayAttackCheck(challengeKey, keyResponse);
		
		return true;
	}

	private void runAntiReplayAttackCheck(String challengeKey, String challengeKeyResponse) throws IOException {
		String expectedKey = WebSocketUtils.calculateResponseSecret(challengeKey);
		if(!expectedKey.equals(challengeKeyResponse)) throw new IOException("Sec-WebSocket-Accept does not give back an valid key, you are may run into an repeat attac!");
	}

	private String generateRandomChallenge(int len) {
		byte[] challenge = new byte[len];
		Random r = new Random(System.currentTimeMillis());
		for(int i=0; i<len; i++) challenge[i] = (byte)(r.nextInt() & 0xFF);
		return new String(Base64.getEncoder().encode(challenge));
	}

	private void send(byte[] data, int opCode) throws IOException {
		WebSocketUtils.send(dos, data, opCode, true);
	}


}
