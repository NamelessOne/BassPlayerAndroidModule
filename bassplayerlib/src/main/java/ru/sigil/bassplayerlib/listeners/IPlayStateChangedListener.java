package ru.sigil.bassplayerlib.listeners;

import ru.sigil.bassplayerlib.PlayState;

/**
 * Created by namelessone
 * on 10.06.17.
 */

public interface IPlayStateChangedListener {
    void onPlayStateChanged(PlayState playState);
}
