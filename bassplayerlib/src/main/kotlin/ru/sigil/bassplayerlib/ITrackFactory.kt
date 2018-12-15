package ru.sigil.bassplayerlib

/**
 * Created by namelessone
 * on 15.12.18.
 */
interface ITrackFactory {
    fun createTrack(author: String?, title: String?, recDirectory: String, time: String?): ITrack
}
