package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.kaiutils.ReuseableHTTPSClient;
import de.theholyexception.livestreamirc.util.kaiutils.SmartHTTPSRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class YtChatReader {
    private static final byte[] POST_TEMPLATE = "{\"context\": {\"client\": {\"visitorData\": \"$VISITOR_DATA$\",\"clientName\": \"WEB\",\"clientVersion\": \"$VERSION$\"}},\"continuation\": \"$CONTINUE$\"}".getBytes();
    private String continuationKey;
    private final String visitorData;
    private final String clientVersion;
    private final Channel channel;
    private final boolean isDebug = Boolean.TRUE.equals(LiveStreamIRC.getCfg().getTable("engine").getBoolean("debug"));
    private static File debugFile;
    private static final List<String> messageQueue = new ArrayList<>();

    public YtChatReader(Channel channel) throws IOException {
        this.channel = channel;
        ReuseableHTTPSClient.Result r = SmartHTTPSRequestManager.request("www.youtube.com", 443, "/live_chat?is_popout=1&v=" + channel.streamURL(), "GET", null, null);
        if (r.getResponseCode() != 200) throw new IOException("r.getResponseCode() = " + r.getResponseCode());
        String page = new String(r.getData());
        this.continuationKey = page.split("continuation\":\"")[1].split("\"")[0];
        this.visitorData     = page.split("visitorData\":\"")[1].split("\"")[0];
        this.clientVersion   = page.split("clientVersion\":\"")[1].split("\"")[0];

        if (isDebug && debugFile == null) {
            createNewDebugFile(true);
            Thread t = createDebugFileWriter();
            t.start();
        }
    }

    public List<Message> read() throws IOException {
        byte[] post = new String(POST_TEMPLATE).replace("$VISITOR_DATA$", visitorData).replace("$CONTINUE$", continuationKey).replace("$VERSION$", clientVersion).getBytes();
        ReuseableHTTPSClient.Result result = SmartHTTPSRequestManager.request("www.youtube.com", 443, "/youtubei/v1/live_chat/get_live_chat?key="+continuationKey+"&prettyPrint=false", "POST", null, post);
        if (result.getResponseCode() != 200) throw new IOException("r.getResponseCode() = " + result.getResponseCode());
        String response = new String(result.getData());
        if (isDebug) {
            synchronized (messageQueue) {
                messageQueue.add(response);
            }
        }
        response = response.substring(response.indexOf('{'));

        try {
            return parseChat(response);
        } catch (ParseException ex) {
            log.error("Failed to parse Json Content!");
        }
        return new ArrayList<>();
    }

    private List<Message> parseChat(String response) throws ParseException {
        JSONObject obj = (JSONObject) new JSONParser().parse(response);

        obj = (JSONObject) obj.get("continuationContents");
        obj = (JSONObject) obj.get("liveChatContinuation");

        //get next key:
        JSONArray continuations = (JSONArray) obj.get("continuations");
        JSONObject continuation = (JSONObject) continuations.get(0);
        JSONObject invalidationContinuationData = (JSONObject) continuation.get("invalidationContinuationData");
        continuationKey = (String) invalidationContinuationData.get("continuation");

        //get chat data:
        JSONArray actions = (JSONArray) obj.get("actions");
        if(actions == null) return new ArrayList<>(1);

        List<Message> messages = new ArrayList<>();
        for (Object action : actions) {
            obj = (JSONObject) action;
            obj = (JSONObject) obj.get("addChatItemAction");
            if (obj == null) continue;

            obj = (JSONObject) obj.get("item");
            JSONObject chatTextMessageRenderer = (JSONObject) obj.get("liveChatTextMessageRenderer");

            JSONObject message = (JSONObject) chatTextMessageRenderer.get("message");
            JSONObject authorName = (JSONObject) chatTextMessageRenderer.get("authorName");
            long timestamp = Long.parseLong(chatTextMessageRenderer.get("timestampUsec").toString());

            JSONArray runs = (JSONArray) message.get("runs");
            String userName = (String) authorName.get("simpleText");

            StringBuilder sb = new StringBuilder();
            for (Object run : runs) {
                obj = (JSONObject) run;
                String text = (String) obj.get("text");
                if (text != null) {
                    sb.append(text);
                }
            }
            sb.append("\r\n");
            messages.add(new Message(channel, userName, sb.toString(), timestamp/1000));
        }
        return messages;
    }

    private static Thread createDebugFileWriter() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    OutputStream os = new FileOutputStream(debugFile, true);
                    for (String s : messageQueue) {
                        os.write((s+"\r\n").getBytes());
                    }
                    os.flush();
                    os.close();
                    if (Files.size(debugFile.toPath()) > 250_000) createNewDebugFile(false);
                    messageQueue.clear();
                } catch (InterruptedException | IOException ex) {
                    Thread.currentThread().interrupt();
                    log.error("Failed to process debug file: " + ex.getMessage());
                }
            }
        });
        t.setName("DEBUG YT-DATA COLLECTOR");
        return t;
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        int l;
        byte[] buffer = new byte[1024*1024];
        while ((l = is.read(buffer)) != -1) {
            os.write(buffer, 0, l);
        }
        is.close();
    }

    private static void createNewDebugFile(boolean init) throws IOException {
        File folder = new File("logs", "debug");
        if (!init) {
            File compressed = new File(folder, System.currentTimeMillis()+".ytdebug.gz");
            try (GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(compressed))) {
                for (File file : Objects.requireNonNull(folder.listFiles())) {
                    if (file.getName().contains(".ytdebug.temp")) {
                        try (InputStream is = new FileInputStream(file)) {
                            copyStream(is, os);
                        }
                        Files.delete(file.toPath());
                    }

                    if (file.getName().contains(".ytdebug.gz") && !file.getName().equalsIgnoreCase(compressed.getName()) && Files.size(file.toPath()) < 100_000_000) {
                        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file))) {
                            copyStream(gis, os);
                        }
                        Files.delete(file.toPath());
                    }
                }
            }
        }

        if (!folder.exists() && !folder.mkdirs()) log.error("Failed to create log directory");
        debugFile = new File(folder, System.currentTimeMillis()+".ytdebug.temp");
        if (!debugFile.createNewFile()) log.error("Failed to create debugFile");
    }
}