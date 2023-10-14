package de.theholyexception.livestreamirc.ircprovider;

import de.theholyexception.livestreamirc.util.Channel;

public interface IRC {

    boolean isConnected();
    void joinChannel(Channel channel);
    void leaveChannel(Channel channel);

}