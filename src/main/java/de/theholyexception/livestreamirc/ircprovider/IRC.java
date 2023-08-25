package de.theholyexception.livestreamirc.ircprovider;

public interface IRC {

    void joinChannel(String channel);
    void leaveChannel(String channel);

}