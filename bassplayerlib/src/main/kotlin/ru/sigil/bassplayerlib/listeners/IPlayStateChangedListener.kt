package ru.sigil.bassplayerlib.listeners

import ru.sigil.bassplayerlib.PlayState

/**
 * Created by namelessone
 * on 15.12.18.
 */
interface IPlayStateChangedListener {
    fun onPlayStateChanged(playState: PlayState)
}
