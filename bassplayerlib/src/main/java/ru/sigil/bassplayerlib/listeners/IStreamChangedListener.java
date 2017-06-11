package ru.sigil.bassplayerlib.listeners;

import ru.sigil.bassplayerlib.IRadioStream;

/**
 * Created by namelessone
 * on 10.06.17.
 */

public interface IStreamChangedListener <T extends IRadioStream> {
    //TODO generic всё портит=(
    void onStreamChanged(T stream);
}
