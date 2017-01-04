package ru.sigil.bassplayerlib;

/**
 * Created by namelessone
 * on 04.01.17.
 */

public interface ITrackFactory {
    ITrack createTrack(String author, String title, String recDirectory, String time);
}
