package de.theholyexception.livestreamirc.util.kaiutils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

public class SmartHTTPSRequestManager {
	private static final Map<String, ObjectStream<ReuseableHTTPSClient>> clientInstances = new HashMap<>();
	private static final Map<String, Long> clientInstancesLastUse = new HashMap<>();
	private static final Map<String, Integer> clientInstancesInUse = new HashMap<>();
	private static long connectionTimeoutMS = 60 * 1000;
	private static volatile CleanupThread cleanupThread;

	public static ReuseableHTTPSClient.Result request(String server, int port, String page, String requestMethod, Map<String, String> headerFields, byte[] postData) throws IOException {
		return request(server, port, page, requestMethod, headerFields, postData, 10, false);
	}
	
	public static ReuseableHTTPSClient.Result request(String server, int port, String page, String requestMethod, Map<String, String> headerFields, byte[] postData, boolean useAlternativeEOFDetector) throws IOException {
		return request(server, port, page, requestMethod, headerFields, postData, 10, useAlternativeEOFDetector);
	}
	
	@SuppressWarnings("resource")
	public static ReuseableHTTPSClient.Result request(String server, int port, String page, String requestMethod, Map<String, String> headerFields, byte[] postData, int maxSocketCount, boolean useAlternativeEOFDetector) throws IOException {
		String k = server + ':' + port;
		
		ObjectStream<ReuseableHTTPSClient> a;
		synchronized(clientInstances){
			if((a = clientInstances.get(k)) == null) clientInstances.put(k, a = new ObjectStream<>());
		}
		
		ReuseableHTTPSClient c;
		{
			int ms = 50;
			while(true){
				synchronized(a){
					c = a.hasNext() ? a.getNext() : null;
				}
				if(c != null && c.isClosed()){
					synchronized (clientInstancesInUse) {
						clientInstancesInUse.put(k, clientInstancesInUse.getOrDefault(k, 0) - 1);
					}
					//c.close();
					c = null;
				}
				int count;
				synchronized (clientInstancesInUse) {
					count = clientInstancesInUse.getOrDefault(k, 0);
				}
				if(count < maxSocketCount){
					break;
				} else {
					try {
						Thread.sleep(ms);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if(ms < 1000) ms <<= 1;
				}
			}
		}
		if(c == null) {
			c = new ReuseableHTTPSClient(server, port);
			synchronized (clientInstancesInUse) {
				clientInstancesInUse.put(k, clientInstancesInUse.getOrDefault(k, 0) + 1);
			}
		}
		ReuseableHTTPSClient.Result result = c.request(page, requestMethod, headerFields, postData, useAlternativeEOFDetector);
		
		synchronized(a){
			a.put(c);
		}
		
		synchronized (clientInstancesLastUse) {
			clientInstancesLastUse.put(k, System.currentTimeMillis());
		}
		
		cleanup();
		
		return result;
	}
	
	public static void setConnectionTimeout(long ms){
		connectionTimeoutMS = ms;
	}
	
	public static void closeAllUnusedConnections(){
		synchronized (clientInstancesLastUse) {
			ArrayList<String> keys = new ArrayList<String>();
			keys.addAll(clientInstancesLastUse.keySet());
			for(String k : keys){
				clientInstancesLastUse.put(k, 0L);
			}
			
		}
	}
	
	private static void cleanup(){
		synchronized (clientInstancesLastUse) {
			if(cleanupThread == null){
				cleanupThread = new CleanupThread();
			}
		}
	}
	
	public static class CleanupThread implements Runnable {
		public CleanupThread(){
			Thread t = new Thread(this, "CleanupThread");
			t.setDaemon(true);
			t.start();
		}

		@Override
		public void run() {
			while(true){
				synchronized (clientInstances) {
					if(clientInstances.size() == 0) break;
				}
				long refTime = System.currentTimeMillis() - connectionTimeoutMS;
				HashSet<String> delList = new HashSet<String>();
				synchronized (clientInstancesLastUse) {
					for(Entry<String, Long> e : clientInstancesLastUse.entrySet()){
						if(refTime > e.getValue()){
							delList.add(e.getKey());
							synchronized (clientInstancesInUse) {
								clientInstancesInUse.remove(e.getKey());
							}
						}
					}
					if(delList.size() != 0) for(String k : delList) clientInstancesLastUse.remove(k);
				}
				if(delList.size() != 0) {
					synchronized (clientInstances) {
						for(String k : delList) {
							ObjectStream<ReuseableHTTPSClient> list = clientInstances.remove(k);
							while(list.hasNext()) {
								try {
									list.getNext().close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
				try {
					Thread.sleep(connectionTimeoutMS / 10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			cleanupThread = null;
		}
		
	}
}
