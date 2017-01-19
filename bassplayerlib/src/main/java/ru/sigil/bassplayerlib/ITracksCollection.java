package ru.sigil.bassplayerlib;

/**
 * Created by namelessone
 * on 04.01.17.
 */

public interface ITracksCollection {
    void add(ITrack mp3entity);

    void remove(ITrack mp3entity);

    ITrack getNext(ITrack entity);
}