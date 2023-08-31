package de.theholyexception.livestreamirc.ircprovider;

public interface IRC {

    boolean isConnected();
    void joinChannel(String channel);
    void leaveChannel(String channel);

}