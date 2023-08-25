package de.theholyexception.livestreamirc.util;

public record Message(String platform, String channel, String username, String message, long timestamp) { }
