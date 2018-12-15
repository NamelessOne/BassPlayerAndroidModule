package ru.sigil.bassplayerlib

/**
 * Created by namelessone
 * on 15.12.18.
 */
interface ITracksCollection {
    fun add(mp3entity: ITrack)
    fun remove(mp3entity: ITrack)
}