package ru.sigil.bassplayerlib;

import ru.sigil.bassplayerlib.listeners.IAuthorChangedListener;
import ru.sigil.bassplayerlib.listeners.IBufferingProgressListener;
import ru.sigil.bassplayerlib.listeners.IEndSyncListener;
import ru.sigil.bassplayerlib.listeners.IPlayStateChangedListener;
import ru.sigil.bassplayerlib.listeners.IPlayerErrorListener;
import ru.sigil.bassplayerlib.listeners.IRecStateChangedListener;
import ru.sigil.bassplayerlib.listeners.IStreamChangedListener;
import ru.sigil.bassplayerlib.listeners.ITitleChangedListener;
import ru.sigil.bassplayerlib.listeners.IVolumeChangedListener;

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

    void addAuthorChangedListener(IAuthorChangedListener listener);

    void removeAuthorChangedListener(IAuthorChangedListener listener);

    void addBufferingProgressChangedListener(IBufferingProgressListener listener);

    void removeBufferingProgressChangedListener(IBufferingProgressListener listener);

    void addEndSyncChangedListener(IEndSyncListener listener);

    void removeEndSyncChangedListener(IEndSyncListener listener);

    void addPlayerErrorChangedListener(IPlayerErrorListener listener);

    void removePlayerErrorChangedListener(IPlayerErrorListener listener);

    void addPlayStateChangedListener(IPlayStateChangedListener listener);

    void removePlayStateChangedListener(IPlayStateChangedListener listener);

    void addRecStateChangedListener(IRecStateChangedListener listener);

    void removeRecStateChangedListener(IRecStateChangedListener listener);

    void addStreamChangedListener(IStreamChangedListener<T> listener);

    void removeStreamChangedListener(IStreamChangedListener<T> listener);

    void addTitleChangedListener(ITitleChangedListener listener);

    void removeTitleChangedListener(ITitleChangedListener listener);

    void addVolumeChangedListener(IVolumeChangedListener listener);

    void removeVolumeChangedListener(IVolumeChangedListener listener);

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
