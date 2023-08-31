package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;

public record Channel(@NonNull String platform, @NonNull String channelName, @NonNull String streamer) {

    public static Channel fromResultSet(ResultSet resultSet) throws SQLException {
        String platform = resultSet.getString("Platform");
        String channelName = resultSet.getString("Channel");
        String streamer = resultSet.getString("Streamer").toLowerCase();
        return new Channel(platform, channelName, streamer);
    }

    public void joinChannel() {
        LiveStreamIRC.getIrcList().get(platform).joinChannel(channelName);
    }
    public void leaveChannel() {
        LiveStreamIRC.getIrcList().get(platform).leaveChannel(channelName);
    }

}
