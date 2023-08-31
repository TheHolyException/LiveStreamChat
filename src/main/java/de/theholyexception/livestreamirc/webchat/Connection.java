package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import de.theholyexception.livestreamirc.util.ConfigProperty;
import de.theholyexception.livestreamirc.util.Message;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Slf4j
public class Connection extends Thread {

    private final Socket socket;
    private final BufferedInputStream bis;
    private final BufferedOutputStream bos;


    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.bis = new BufferedInputStream(socket.getInputStream());
        this.bos = new BufferedOutputStream(socket.getOutputStream());
        this.start();
    }

    @Override
    public void run() {
        try {
            String[] args = readLine().split(" ");
            String methode = args[0];
            log.debug(methode);

            if (methode.equals("GET")) {
                log.debug(args[1]);
                String[] params = args[1].split("\\?");

                if (args[1].endsWith(".css"))
                    writeFile("web/"+args[1].substring(1), "text/css");

                else if (args[1].endsWith(".js")) {
                    ConfigProperty p = LiveStreamIRC.getProperties();
                    writeFile("web/"+args[1].substring(1), "text/javascript"
                            ,"###WEBSOCKETURL###", "ws://"+p.getValue("WebSocketHost") + ":" + p.getValue("WebSocketPort"));
                }

                else if (params.length > 1)
                    writeFile("web/webchat.html", "text/html", "###streamer###", readHTTPParams(params[1]).get("streamer"));

            }

        } catch (Exception ex) {
            try {
                bos.write(("""
                        HTTP/1.1 400 Bad Request
                        Content-Type: text/html
                        
                        The Server was unable to parse or handle your http request.
                        """).getBytes());
            } catch (IOException ex1) {
                ex.printStackTrace();
            }
            ex.printStackTrace();
        } finally {
            try {
                bos.flush();
                bos.close();
                bis.close();
                socket.close();
            } catch (IOException ex1) {
                ex1.printStackTrace();
            }
        }
    }

    private void onGet(Map<String, String> params) throws IOException {
        log.debug("onGet");
        /*StringBuilder builder = new StringBuilder("<div class=\"messagecontainer\">");
        Set<Message> messages = LiveStreamIRC.getMessageProvider().getMessages(params.get("channel"));
        if (messages != null)
            messages.forEach(message -> builder.append(String.format("<p>[%s] %s > %s</p>", message.platform(), message.username(), message.message())));
        builder.append("</div>");*/

        writeFile("web/webchat.html", "text/html", "###streamer###", params.get("streamer"));
    }

    private final ByteArrayOutputStream readLineBuffer = new ByteArrayOutputStream(32);
    private String readLine() throws IOException {
        int chr;
        ByteArrayOutputStream baos = this.readLineBuffer;
        while(((chr = bis.read()) != '\n') && chr != -1){
            baos.write(chr);
        }
        if(baos.size() == 0) return "";
        String out = baos.toString();
        baos.reset();
        if(out.charAt(out.length() - 1) == '\r') return out.substring(0, out.length() - 1);
        return out;
    }

    public static Map<String, String> readHTTPParams(String meta) {
        HashMap<String, String> out = new HashMap<>();
        if(meta == null) return out;
        String[] args = meta.split("&");
        for (String arg : args) {
            String[] subArgs = _split(arg, "=");
            if (subArgs.length > 1) {
                out.put(subArgs[0], URLDecoder.decode(subArgs[1]));
            }
        }
        return out;
    }
    private Map<String, String> readHTTPBody() throws IOException {
        HashMap<String, String> out = new HashMap<>();
        String row = readLine();
        while(row.length() > 0) {
            String[] subArgs = _split(row, ":");
            out.put(subArgs[0].toLowerCase(), subArgs[1].trim());
        }
        return out;
    }

    public static String[] _split(String src, String filter) {
        int i = 0;
        int last = 0;
        int len = 1;
        while ((i = src.indexOf(filter, last)) != -1) {
            len++;
            last = i + filter.length();
        }
        String[] out = new String[len];
        last = 0;
        len = 0;
        while ((i = src.indexOf(filter, last)) != -1) {
            out[len] = src.substring(last, i);
            last = i + filter.length();
            len++;
        }
        out[len] = src.substring(last);
        return out;
    }

    private void writeHeader(String contentType, long l, OutputStream os) throws IOException {
        os.write(String.format("""

                HTTP/1.1 200 OK
                Content-Type: %s
                Content-Length: %s
                
                """,contentType, l).getBytes());
    }

    private void writeFile(String path, String type, String... replacements) throws IOException {
        try (BufferedInputStream lBis = new BufferedInputStream(new FileInputStream(path))) {
            byte[] data = lBis.readAllBytes();
            String s = new String(data);
            for (int i = 0; i < replacements.length-1; i +=2) {
                s = s.replace(replacements[i], replacements[i+1]);
            }
            data = s.getBytes();
            writeHeader(type, data.length, bos);
            bos.write(data);
        }
    }

}
