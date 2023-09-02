package de.theholyexception.livestreamirc.webchat;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlTable;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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

            if (methode.equals("GET")) {
                String[] params = args[1].split("\\?");

                if (args[1].endsWith(".css"))
                    writeFile("web/"+args[1].substring(1), "text/css");

                else if (args[1].endsWith(".js")) {
                    TomlTable table = LiveStreamIRC.getCfg().getTable("websocket");
                    writeFile("web/"+args[1].substring(1), "text/javascript"
                            ,"###WEBSOCKETURL###", "ws://"+table.getString("host") + ":" + table.getLong("port"));
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
            String[] subArgs = split(arg, "=");
            if (subArgs.length > 1) {
                out.put(subArgs[0], URLDecoder.decode(subArgs[1], StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    public static String[] split(String src, String filter) {
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
