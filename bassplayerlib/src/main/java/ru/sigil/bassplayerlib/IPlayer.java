package ru.sigil.bassplayerlib;

/**
 * Created by NamelessOne
 * on 17.09.2016.
 */
public interface IPlayer<T extends IRadioStream> {
    String[] RESERVED_CHARS = {"|", "\\", "?", "*", "<", "\"",
            ":", ">", "+", "[", "]", "/", "'", "%"};

    //TODO унифицировать play и playAAC
    void play(T radioStream);

    void playAAC(T radioStream);

    void playFile(ITrack file);

    void pause();

    void stop();

    void addEventListener(IPlayerEventListener<T> listener);

    void removeEventListener(IPlayerEventListener<T> listener);

    void removeErrorListener(IPLayerErrorListener listener);

    void addErrorListener(IPLayerErrorListener listener);

    void removeAllListeners();

    String currentTitle();

    String currentArtist();

    T currentStream();

    PlayState currentState();

    void setStream(T stream);

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
