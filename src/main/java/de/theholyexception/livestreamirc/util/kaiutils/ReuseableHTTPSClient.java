package de.theholyexception.livestreamirc.util.kaiutils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ReuseableHTTPSClient implements Closeable {
	private SSLSocket socket;
	private InputStream is;
	private OutputStream os;
	private String host;
	
	private static final HashMap<String, String> defaultHeader;
	static{
		defaultHeader = new HashMap<String, String>(11);
		defaultHeader.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0");
		defaultHeader.put("Connection", "keep-alive");
		defaultHeader.put("DNT", "1");
	}
	
	public ReuseableHTTPSClient(String targetIp, int targetPort) throws IOException {
		host = targetIp;
		socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(targetIp, targetPort);
		socket.setSoTimeout(10_000);
		is = socket.getInputStream();
		os = socket.getOutputStream();
	}
	
	public Result request(String page, String requestMethod, HashMap<String, String> headerFields, byte[] postData, boolean useAlternativeEOFDetector) throws IOException {
		if(page == null || page.length() == 0 || page.charAt(0) != '/') page = "/" + (page == null ? "" : page);
		if(requestMethod == null) requestMethod = "GET";
		if(headerFields == null) headerFields = new HashMap<String, String>();
		//headerFields.putAll(defaultHeader);
		for(Entry<String, String> e : defaultHeader.entrySet()){
			if(headerFields.containsKey(e.getKey())) continue;
			headerFields.put(e.getKey(), e.getValue());
		}
		headerFields.put("Host", host);
		if(postData != null){
			headerFields.put("Content-Length", String.valueOf(postData.length));
		}
		StringBuilder sb = new StringBuilder(requestMethod).append(' ').append(page).append(" HTTP/1.1\r\n");
		for(Entry<String, String> e : headerFields.entrySet()){
			sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
		}
		
		String s = sb.append("\r\n").toString();
		
		os.write(s.getBytes());
		if(postData != null){
			os.write(postData);
		}
		os.flush();
		
		//s = "GET /" + page + " HTTP/1.1\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0\r\nContent-Type: application/x-www-form-urlencoded;charset=utf-8\r\n\r\n";
		//s = "GET /" + page + " HTTP/1.1\r\nUser-Agent: " + URLEncoder.encode("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0") + "\r\nAccept-Language: " + URLEncoder.encode("de,en-US;q=0.7,en;q=0.3") + "\r\nConnection: keep-alive\r\nDNT: 1\r\n\r\n";
		//s = "GET /" + page + " HTTP/1.1\r\n\r\n";
		//s = "GET /" + page + " HTTP/1.1\r\n: " + host + "\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0\r\nAccept-Language: de,en-US;q=0.7,en;q=0.3\r\nConnection: keep-alive\r\nDNT: 1\r\n\r\n";
		//System.out.println("GET " + page + " HTTP/1.1");
		
		String len = null;
		int responseCode;
		HashMap<String, String> resultHeader = new HashMap<String, String>();
		boolean isChunkedEncoding = false;
		{
			String line = readLine(is);
			responseCode = Integer.parseInt(line.split(" ")[1]);
			//if(!line.endsWith("200 OK")) return null;
			while((line = readLine(is)).length() > 2){
				int p = line.indexOf(':');
				if(p == -1) break;
				String k = line.substring(0, p);
				String v = line.substring(p+1).trim();
				//System.out.println("k=" + k);
				//System.out.println("v=" + v);
				if(k.toLowerCase().equals("content-length")) len = v;
				if(k.toLowerCase().equals("transfer-encoding") && v.equals("chunked")) isChunkedEncoding = true;
				resultHeader.put(k, v);
			}
		}
		if(isChunkedEncoding){
			ByteArrayOutputStream baos = new  ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int chunkSize = 1;
			while(chunkSize != 0){
				chunkSize = Integer.parseInt(readLine(is).trim(), 16);
				copyBytes(is, baos, buf, chunkSize);
				readLine(is);//must be done here say specifications on wikipedia. https://en.wikipedia.org/wiki/Chunked_transfer_encoding#Example
			}
			return new ReuseableHTTPSClient.Result(baos.toByteArray(), resultHeader, responseCode);
		}
		
		if(len == null){
			ByteArrayOutputStream baos = new  ByteArrayOutputStream();
			int chr = -1;
			byte[] buffer = new byte[1024*32];
			try {
				BufferedInputStream bis = new BufferedInputStream(is, buffer.length * 4);
				while ((chr = bis.read(buffer)) != -1) {
					baos.write(buffer, 0, chr);
					//if(chr < buffer.length) break;
				}
			} catch(Exception e) {
			  if(!(e instanceof SocketTimeoutException)) e.printStackTrace();
			}
			return new ReuseableHTTPSClient.Result(baos.toByteArray(), resultHeader, responseCode);
		} else {
			byte[] arr = new byte[Integer.parseInt(len)];
			new DataInputStream(is).readFully(arr);
			return new ReuseableHTTPSClient.Result(arr, resultHeader, responseCode);
		}
	}
	
	private void copyBytes(InputStream is, ByteArrayOutputStream baos, byte[] bufPtr, int len) throws IOException {
		while(len > 0){
			int l = is.read(bufPtr, 0, Math.min(len, bufPtr.length));
			if(l == -1) throw new IOException("EOF");
			baos.write(bufPtr, 0, l);
			len -= l;
		}
		
	}

	private static String readLine(InputStream is) throws IOException {
		StringBuilder sb = new StringBuilder();
		int chr;
		while((chr = is.read()) != -1 && chr != '\n'){
			sb.append((char)chr);
		}
		String s = sb.toString();
		if(s.charAt(s.length() - 1) == '\r') return s.substring(0, s.length() - 1);
		return s;
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}
	
	public boolean isClosed(){
		return socket.isClosed();
	}

	
	public static class Result {
		private final byte[] content;
		private final HashMap<String, String> header;
		private final int responseCode;
		
		public Result(byte[] content, HashMap<String, String> header, int responseCode){
			this.content = content;
			this.header = header;
			this.responseCode = responseCode;
		}
		
		public int getResponseCode(){
			return responseCode;
		}
		
		public byte[] getData(){
			return content;
		}
		
		public HashMap<String, String> getHeaders(){
			return header;
		}
		
		public String toString(){
			return "{ResponseCode: " + responseCode + ", Content-Length: " + content.length + ", Header: " + header + "}";
		}
	}
}
