package ru.sigil.bassplayerlib.listeners

/**
 * Created by namelessone
 * on 15.12.18.
 */
interface IPlayerErrorListener {
    fun onError(message: String, errorCode: Int, exception: Exception? = null)
}
