package ru.sigil.bassplayerlib

import ru.sigil.bassplayerlib.listeners.*

/**
 * Created by namelessone
 * on 14.12.18.
 */
interface IPlayer<T : IRadioStream> {
    val title: String?
    val author: String?
    val stream: T?
    val playState: PlayState
    val isRecActive: Boolean
    val chan: Int
    val fileLength: Long
    val isPaused: Boolean
    val currentMP3Entity: ITrack?

    var volume: Float
    var progress: Long

    fun playStream(radioStream: T)
    fun playFile(file: ITrack)
    fun pause()
    fun stop()
    fun addAuthorChangedListener(listener: IAuthorChangedListener)
    fun removeAuthorChangedListener(listener: IAuthorChangedListener)
    fun addBufferingProgressChangedListener(listener: IBufferingProgressListener)
    fun removeBufferingProgressChangedListener(listener: IBufferingProgressListener)
    fun addEndSyncListener(listener: IEndSyncListener)
    fun removeEndSyncListener(listener: IEndSyncListener)
    fun addPlayerErrorListener(listener: IPlayerErrorListener)
    fun removePlayerErrorListener(listener: IPlayerErrorListener)
    fun addPlayStateChangedListener(listener: IPlayStateChangedListener)
    fun removePlayStateChangedListener(listener: IPlayStateChangedListener)
    fun addRecStateChangedListener(listener: IRecStateChangedListener)
    fun removeRecStateChangedListener(listener: IRecStateChangedListener)
    fun addStreamChangedListener(listener: IStreamChangedListener<T>)
    fun removeStreamChangedListener(listener: IStreamChangedListener<T>)
    fun addTitleChangedListener(listener: ITitleChangedListener)
    fun removeTitleChangedListener(listener: ITitleChangedListener)
    fun addVolumeChangedListener(listener: IVolumeChangedListener)
    fun removeVolumeChangedListener(listener: IVolumeChangedListener)
    fun addSyncStallListener(listener: ISyncStallListener)
    fun removeSyncStallListener(listener: ISyncStallListener)
    fun removeAllListeners()
    fun rec(isActive: Boolean)
    fun resume()
}
