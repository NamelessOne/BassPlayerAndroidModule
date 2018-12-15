package ru.sigil.bassplayerlib

/**
 * Created by namelessone
 * on 15.12.18.
 */
interface ITrack {
    val title: String?
    val artist: String?
    val time: String? //А нужно ли это свойство??
    val directory: String
}
