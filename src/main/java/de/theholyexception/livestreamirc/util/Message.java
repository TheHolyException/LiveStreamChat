package de.theholyexception.livestreamirc.util;

import java.util.Base64;

public record Message(String platform, String channel, String username, String message, long timestamp) {

    public String b64Message() {
        return new String(Base64.getEncoder().encode(message.getBytes()));
    }

    public String b64Username() {
        return new String(Base64.getEncoder().encode(username.getBytes()));
    }

}
