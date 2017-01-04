package ru.sigil.bassplayerlib;

/**
 * Created by namelessone
 * on 04.01.17.
 */

public interface ITrack {
    String getTitle();
    void setTitle(String title);
    String getArtist();
    void setArtist(String artist);
    String getTime();
    void setTime(String time);
    String getDirectory();
    void setDirectory(String directory);
}
