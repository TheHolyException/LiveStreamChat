package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.util.kaiutils.ReuseableHTTPSClient;
import de.theholyexception.livestreamirc.util.kaiutils.SmartHTTPSRequestManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class YtChatReaderBackup {
    private static final int silentSwapTimer_startvalue = 5;
    private static final int MAX_CHAT_STACK_SIZE = 10;

    private String id;
    private byte[] post_template;
    private ReaderInstance currentReader;
    private ArrayList<ChatMessage> lastMessages = new ArrayList<ChatMessage>(1);
    private int silentSwapTimer = silentSwapTimer_startvalue;

    private String postTemplate = "{\"context\":{\"client\":{\"hl\":\"de\",\"gl\":\"DE\",\"remoteHost\":\"91.52.30.38\",\"deviceMake\":\"\",\"deviceModel\":\"\",\"visitorData\":\"$VISITOR_DATA$\",\"userAgent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/116.0,gzip(gfe)\",\"clientName\":\"WEB\",\"clientVersion\":\"2.20230829.01.02\",\"osName\":\"Windows\",\"osVersion\":\"10.0\",\"originalUrl\":\"https://www.youtube.com/live_chat?is_popout=1&v=$VIDEOID$\",\"platform\":\"DESKTOP\",\"clientFormFactor\":\"UNKNOWN_FORM_FACTOR\",\"configInfo\":{\"appInstallData\":\"CLKDxKcGELTJrwUQ6-j-EhC1pq8FEOuTrgUQ4-avBRDd6P4SEMfmrwUQpcL-EhCj3q8FELzM_hIQteavBRCMy68FEMTdrwUQ1uqvBRDqw68FELiLrgUQwt7-EhDZya8FEMzfrgUQ0-GvBRCI468FEKy3rwUQl8-vBRD65P4SEN7o_hIQieiuBRDV5a8FEJfn_hIQ5LP-EhDyqK8FEO6irwUQzK7-EhDs2K8FENShrwUQ57qvBRCG2f4SEN3jrwUQ26-vBRCBpa8FEL22rgUQ-r6vBRCI2K8FEOLUrgUQ1emvBRCM6v4SEKfirwU%3D\"},\"userInterfaceTheme\":\"USER_INTERFACE_THEME_DARK\",\"timeZone\":\"Europe/Berlin\",\"browserName\":\"Firefox\",\"browserVersion\":\"116.0\",\"acceptHeader\":\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\",\"deviceExperimentId\":\"ChxOekkzTXpVNU5qY3pPRFV4TURrd01UVXdNQT09ELKDxKcGGLKDxKcG\",\"screenWidthPoints\":1920,\"screenHeightPoints\":370,\"screenPixelDensity\":1,\"screenDensityFloat\":1,\"utcOffsetMinutes\":120,\"mainAppWebInfo\":{\"graftUrl\":\"https://www.youtube.com/live_chat?is_popout=1&v=$VIDEOID$\",\"webDisplayMode\":\"WEB_DISPLAY_MODE_BROWSER\",\"isWebNativeShareAvailable\":false}},\"user\":{\"lockedSafetyMode\":false},\"request\":{\"useSsl\":true,\"internalExperimentFlags\":[{\"key\":\"force_enter_once_in_webview\",\"value\":\"true\"}],\"consistencyTokenJars\":[]},\"clickTracking\":{\"clickTrackingParams\":\"CAEQl98BIhMIv-yjweeHgQMVKGF6BR0jIQ49\"},\"adSignalsInfo\":{\"params\":[]}},\"continuation\":\"$CONTINUE$\",\"webClientInfo\":{\"isDocumentHidden\":false}}";

    public YtChatReaderBackup(String id) throws IOException {
        this.id = id;
        post_template = postTemplate.getBytes();
        currentReader = new ReaderInstance(id, post_template);
    }


    public synchronized ArrayList<ChatMessage> readNext() throws IOException {
        ArrayList<ChatMessage> next = currentReader.read();
        int n = next.size() - lastMessages.size();
        ArrayList<ChatMessage> out = new ArrayList<ChatMessage>(n+1);
        for(int i=lastMessages.size(); i<next.size(); i++) out.add(next.get(i));
        lastMessages = next;

        if(out.size() == 0){
            silentSwapTimer--;
        } else {
            silentSwapTimer = silentSwapTimer_startvalue;
        }

        boolean capacityRelease = lastMessages.size() > 0 && silentSwapTimer < 0;

        if(next.size() >= MAX_CHAT_STACK_SIZE || capacityRelease){
            try{Thread.sleep(5000);}catch(Exception e){}
            ReaderInstance newReader = new ReaderInstance(id, post_template);
            ArrayList<ChatMessage> async_next = newReader.read();
            ArrayList<ChatMessage> sync_next = currentReader.read();

            ArrayList<ChatMessage> missedSync = new ArrayList<ChatMessage>();
            for(int i=lastMessages.size(); i<sync_next.size(); i++) missedSync.add(sync_next.get(i));
            out.addAll(missedSync);

            lastMessages = async_next;
            currentReader = newReader;

            //System.err.println(capacityRelease ? "<SILENT SWAP>" : "<HOT SWAP>");

            silentSwapTimer = silentSwapTimer_startvalue;
        }

        return out;
    }

    private ArrayList<ChatMessage> parseChat_v2(String[] videoKey, byte[] post) throws IOException {

        String responce = readChatFrame(videoKey[0], post, videoKey[2]);
        responce = responce.substring(responce.indexOf('{'));

        //StringBuilder chatRecord = new StringBuilder();
        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(responce);

            obj = (JSONObject) obj.get("continuationContents");
            obj = (JSONObject) obj.get("liveChatContinuation");


            //get next key:
            JSONArray continuations = (JSONArray) obj.get("continuations");
            JSONObject continuation = (JSONObject) continuations.get(0);
            JSONObject invalidationContinuationData = (JSONObject) continuation.get("invalidationContinuationData");

            String newKey = (String) invalidationContinuationData.get("continuation");

            //if(!videoKey[0].equals(newKey)) println("current key: " + newKey);
            videoKey[0] = newKey;

            //get chat data:
            JSONArray actions = (JSONArray) obj.get("actions");
            if(actions == null) return new ArrayList<ChatMessage>(1);
            ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
            for(int i=0; i<actions.size(); i++){
                obj = (JSONObject) actions.get(i);
                obj = (JSONObject) obj.get("addChatItemAction");
                if(obj != null){
                    obj = (JSONObject) obj.get("item");
                    obj = (JSONObject) obj.get("liveChatTextMessageRenderer");

                    JSONObject message = (JSONObject) obj.get("message");
                    JSONArray runs = (JSONArray) message.get("runs");

                    JSONObject authorName = (JSONObject) obj.get("authorName");
                    String userName = (String) authorName.get("simpleText");
                    StringBuilder sb = new StringBuilder();
                    for(int ii=0; ii<runs.size(); ii++){
                        obj = (JSONObject) runs.get(ii);
                        String text = (String) obj.get("text");
                        if(text != null){
                            sb.append(text);
                        }
                    }
                    sb.append("\r\n");
                    //chatRecord.append(sb.toString());
                    messages.add(new ChatMessage(userName, sb.toString()));
                }
            }
            return messages;//chatRecord.toString();
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private String parseChat(String[] videoKey, byte[] post) throws IOException {

        String responce = readChatFrame(videoKey[0], post, videoKey[2]);
        responce = responce.substring(responce.indexOf('{'));

        StringBuilder chatRecord = new StringBuilder();
        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(responce);

            obj = (JSONObject) obj.get("continuationContents");
            obj = (JSONObject) obj.get("liveChatContinuation");

            JSONArray continuations = (JSONArray) obj.get("continuations");
            JSONObject continuation = (JSONObject) continuations.get(0);
            JSONObject invalidationContinuationData = (JSONObject) continuation.get("invalidationContinuationData");

            //get next key:
            String newKey = (String) invalidationContinuationData.get("continuation");

            if(!videoKey[0].equals(newKey)) System.out.println("current key: " + newKey);
            videoKey[0] = newKey;

            //get chat data:
            JSONArray actions = (JSONArray) obj.get("actions");

            if(actions == null) return "";
            for(int i=0; i<actions.size(); i++){
                obj = (JSONObject) actions.get(i);
                obj = (JSONObject) obj.get("addChatItemAction");
                if(obj != null){
                    obj = (JSONObject) obj.get("item");
                    obj = (JSONObject) obj.get("liveChatTextMessageRenderer");

                    JSONObject message = (JSONObject) obj.get("message");
                    JSONArray runs = (JSONArray) message.get("runs");

                    JSONObject authorName = (JSONObject) obj.get("authorName");
                    String userName = (String) authorName.get("simpleText");

                    StringBuilder sb = new StringBuilder(userName).append(": ");
                    for(int ii=0; ii<runs.size(); ii++){
                        obj = (JSONObject) runs.get(ii);
                        String text = (String) obj.get("text");
                        if(text != null){
                            sb.append(text);
                        } else {
                            //println("DUMP: " + obj);
                        }
                    }
                    sb.append("\r\n");
                    chatRecord.append(sb.toString());
                }
            }
            //saveBytes("H:/dump.json", obj.toString().getBytes());
            //println(chatRecord);
            return chatRecord.toString();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("responce was:");
            System.out.println(responce);
            return null;
        }
    }

    public String[] getVideoKey(String id) throws IOException {// https://www.youtube.com/live_chat?is_popout=1&v=jfKfPfyJRdk
        ReuseableHTTPSClient.Result r = SmartHTTPSRequestManager.request("www.youtube.com", 443, "/live_chat?is_popout=1&v=" + id, "GET", null, null);
        //filter: '"INNERTUBE_API_KEY":"'
        if(r.getResponseCode() != 200) throw new IOException("r.getResponseCode() = " + r.getResponseCode());
        String page = new String(r.getData());
        String[] out = new String[10];
        out[0] = page.split("INNERTUBE_API_KEY")[1].split("\"\\:\"")[1].split("\"")[0];
        out[1] = page.split("continuation\"\\:\"")[1].split("\"")[0];
        out[2] = page.split("visitorData\"\\:\"")[1].split("\"")[0];
        return out;
    }

    private String readChatFrame(String videoKey, byte[] postData, String k2) throws IOException {// https://www.youtube.com/live_chat?is_popout=1&v=jfKfPfyJRdk
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Cookie", "YSC=If3dhUX8Q8s; VISITOR_PRIVACY_METADATA=CgJERRIA; CONSENT=PENDING+025; __Secure-YEC="+k2+"; PREF=tz=Europe.Berlin&f6=40000000&f7=140&f5=30000; SOCS=CAESEwgDEgk1NjQ1NzM3ODMaAmRlIAEaBgiAmomoBg");
        ReuseableHTTPSClient.Result r = SmartHTTPSRequestManager.request("www.youtube.com", 443, "/youtubei/v1/live_chat/get_live_chat?key="+videoKey+"&prettyPrint=false", "POST", headers, postData);
        //filter: '"INNERTUBE_API_KEY":"'
        if(r.getResponseCode() != 200) throw new IOException("r.getResponseCode() = " + r.getResponseCode());
        String page = new String(r.getData());
        //println(r.getHeaders().toString().replace(", ", ",\n"));
        return page;
    }



    class ReaderInstance{
        private String id;
        private byte[] post_template;
        private String[] videoKey;

        public ReaderInstance(String id, byte[] post_template) throws IOException {
            this.id = id;
            this.post_template = post_template;
            this.videoKey = getVideoKey(id);
        }

        public ArrayList<ChatMessage> read() throws IOException {
            byte[] post = new String(post_template).replace("$VISITOR_DATA$", videoKey[2]).replace("$CONTINUE$", videoKey[1]).replace("$VIDEOID$", id).getBytes();
            ArrayList<ChatMessage> chatCollection = parseChat_v2(videoKey, post);
            return chatCollection;
        }
    }

    public class ChatMessage{
        public final String author;
        public final String message;

        public ChatMessage(String author, String message){
            this.author = author;
            this.message = message;
        }

        public int hashCode(){
            return toString().hashCode();//author.hashCode() ^ message.hashCode();
        }

        public boolean equals(Object o){
            if(!(o instanceof ChatMessage)) return false;
            ChatMessage c = (ChatMessage)o;
            return c.author.equals(author) && c.message.equals(message);
        }

        public String toString(){
            return author + ": "  + message;
        }
    }
}