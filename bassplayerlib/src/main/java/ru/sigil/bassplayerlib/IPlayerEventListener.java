package ru.sigil.bassplayerlib;

/**
 * Created by NamelessOne
 * on 17.09.2016.
 */
public interface IPlayerEventListener {
    void onTitleChanged(String title);
    void onAuthorChanged(String author);
    void onPlayStateChanged(PlayState playState);
    void onRecStateChanged(boolean isRec);
    void onBitrateChanged(Bitrate bitrate); //TODO
    void onBufferingProgress(long progress);
    void endSync();
    void onVolumeChanged(float volume);
}
