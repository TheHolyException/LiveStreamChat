package de.theholyexception.livestreamirc.webchat;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class WebSocketUtils {

    private WebSocketUtils() {}

    public static String calculateResponseSecret(byte[] challangeKey){
        return calculateResponseSecret(new String(challangeKey));
    }
    public static String calculateResponseSecret(String key){
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        return new String(Base64.getEncoder().encode(md.digest(key.getBytes())));
    }

    public static void send(DataOutputStream dos, byte[] data, int opCode, boolean maskedMode) throws IOException {
        ByteArrayOutputStream writeBuf = new ByteArrayOutputStream(data.length + 10);//the overhead can't be lager then 10 bytes.
        DataOutputStream d = new DataOutputStream(writeBuf);

        writeBuf.write(128 | (opCode & 0x0F));//opCode & FIN flag

        {
            int lenByte = maskedMode ? 0x80 : 0;
            if(data.length < 126){
                lenByte |= data.length;
            } else if(data.length <= 0xFFFF){
                lenByte |= 126;
            } else {
                lenByte |= 127;
            }
            writeBuf.write(lenByte);

            if (lenByte == 126) {
                d.writeShort(data.length);
            } else if (lenByte == 127) {
                d.writeLong(data.length);
            }
        }

        if (maskedMode) {
            int mask = new Random(System.currentTimeMillis()).nextInt();
            d.writeInt(mask);
            applyMask(d, data, maskToBytes(mask));
            writeBuf.writeTo(dos);
        } else {
            writeBuf.writeTo(dos);
            dos.write(data);
        }
        dos.flush();
    }

    private static byte[] maskToBytes(int mask){
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            out[i] = (byte)((mask >> ((3 - i) << 3)) & 0xFF);
        }
        return out;
    }

    private static void applyMask(DataOutputStream dos, byte[] data, byte[] maskBytes) throws IOException {//copying version
        for (int i = 0; i < data.length; i++) {
            dos.write(data[i] ^ maskBytes[i & 3]);
        }
    }

    private static void applyMask(byte[] data, byte[] maskBytes) throws IOException {//modifying version
        for (int i = 0; i < data.length; i++) {
            data[i] ^= maskBytes[i & 3];
        }
    }

    public static byte[] readNextFrame(DataInputStream dis) throws IOException {
        int len = dis.read();
        int mask = (len & 0x80);
        len &= 0x7F;
        if (len == 126) {
            len = dis.readUnsignedShort();
        } else if (len == 127) {
            len = (int)dis.readLong();
        }
        if (mask != 0) {
            mask = dis.readInt();
        }
        byte[] data = new byte[len];
        dis.readFully(data);

        if (mask != 0) applyMask(data, maskToBytes(mask));	//in the absolutely rare chance that responded mask == 0 the mask processor can be skipped, too, without any unexpected error.

        return data;
    }
}
