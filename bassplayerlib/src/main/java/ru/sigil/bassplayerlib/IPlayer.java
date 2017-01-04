package ru.sigil.bassplayerlib;

/**
 * Created by NamelessOne
 * on 17.09.2016.
 */
public interface IPlayer {
    String[] RESERVED_CHARS = {"|", "\\", "?", "*", "<", "\"",
            ":", ">", "+", "[", "]", "/", "'", "%"};

    //TODO унифицировать play и playAAC
    void play(String URL, Bitrate bitrate);

    void playAAC(String URL, Bitrate bitrate);

    void playFile(ITrack file);

    void pause();

    void stop();

    void addEventListener(IPlayerEventListener listener);

    void removeEventListener(IPlayerEventListener listener);

    void removeErrorListener(IPLayerErrorListener listener);

    void addErrorListener(IPLayerErrorListener listener);

    void removeAllListeners();

    String currentTitle();

    String currentArtist();

    Bitrate currentBitrate();

    PlayState currentState();

    void setBitrate(Bitrate bitrate);

    boolean isRecActive();

    void setChan(int chan);

    int getChan();

    long getFileLength();

    float getVolume();

    void rec(boolean isActive);

    boolean isPaused();

    ITrack getCurrentMP3Entity();

    void setVolume(float volume);

    void setProgress(long position);

    void resume();

    long getProgress();
}
