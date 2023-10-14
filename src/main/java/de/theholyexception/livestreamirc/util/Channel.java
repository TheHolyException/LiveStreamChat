package de.theholyexception.livestreamirc.util;

import de.theholyexception.livestreamirc.LiveStreamIRC;
import lombok.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;

public record Channel(@NonNull String platform, @NonNull String streamURL, @NonNull String streamer, long event) {

    public static Channel fromResultSet(ResultSet resultSet) throws SQLException {
        String platform = resultSet.getString("Platform");
        String streamURL = resultSet.getString("StreamURL");
        String streamer = resultSet.getString("Streamer").toLowerCase();
        long eventID = resultSet.getLong("EventID");
        return new Channel(platform, streamURL, streamer, eventID);
    }

    public void joinChannel() {
        LiveStreamIRC.getIrcList().get(platform).joinChannel(this);
    }
    public void leaveChannel() {
        LiveStreamIRC.getIrcList().get(platform).leaveChannel(this);
    }

}
