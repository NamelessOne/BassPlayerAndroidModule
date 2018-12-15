package ru.sigil.bassplayerlib.listeners

import ru.sigil.bassplayerlib.IRadioStream

/**
 * Created by namelessone
 * on 15.12.18.
 */
interface IStreamChangedListener<T : IRadioStream> {
    fun onStreamChanged(stream: T?)
}