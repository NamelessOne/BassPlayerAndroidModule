package ru.sigil.bassplayerlib

import android.os.Environment
import android.os.Handler
import com.un4seen.bass.BASS
import com.un4seen.bass.BASS_AAC
import ru.sigil.bassplayerlib.listeners.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.HashSet

/**
 * Created by namelessone
 * on 15.12.18.
 */

class Player<T : IRadioStream>(private val mp3Collection: ITracksCollection, private val trackFactory: ITrackFactory, initialStream: T) : IPlayer<T> {

    private val reservedChars = arrayOf("|", "\\", "?", "*", "<", "\"", ":", ">", "+", "[", "]", "/", "'", "%")

    private val authorChangedEventListeners = HashSet<IAuthorChangedListener>()
    private val bufferingProgressEventListeners = HashSet<IBufferingProgressListener>()
    private val endSyncEventListeners = HashSet<IEndSyncListener>()
    private val playerErrorEventListeners = HashSet<IPlayerErrorListener>()
    private val playStateChangedListeners = HashSet<IPlayStateChangedListener>()
    private val recStateChangedListeners = HashSet<IRecStateChangedListener>()
    private val streamChangedListeners = HashSet<IStreamChangedListener<T>>()
    private val titleChangedListeners = HashSet<ITitleChangedListener>()
    private val volumeChangedListeners = HashSet<IVolumeChangedListener>()
    private val syncStallListeners = HashSet<ISyncStallListener>()

    override var stream: T? = null
        set(value) {
            field = value
            for (listener in streamChangedListeners) {
                listener.onStreamChanged(value)
            }
        }

    init {
        stream = initialStream
        BASS.BASS_Free()
        BASS.BASS_Init(-1, 44100, 0)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_NET_PLAYLIST, 1)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_NET_PREBUF, 0)
        BASS.BASS_SetVolume(0.5.toFloat())
    }

    override var title: String? = null
        private set(value) {
            field = value
            for (listener in titleChangedListeners) {
                listener.onTitleChanged(title!!)
            }
        }
    override var author: String? = null
        private set(value) {
            field = value
            for (listener in authorChangedEventListeners) {
                listener.onAuthorChanged(author!!)
            }
        }

    override var playState = PlayState.STOP
        private set(value) {
            field = value
            for (listener in playStateChangedListeners) {
                listener.onPlayStateChanged(value)
            }
            if (value != PlayState.PLAY && value != PlayState.BUFFERING) {
                isRecActive = false
            }
        }

    private var recDirectory: String? = null

    override var isRecActive: Boolean = false
        private set(value) {
            field = value
            for (listener in recStateChangedListeners) {
                listener.onRecStateChanged(value)
            }
        }

    override var currentMP3Entity: ITrack? = null
        private set

    override var chan: Int = 0
        private set

    override var volume: Float
        get() = BASS.BASS_GetVolume()
        set(value) {
            BASS.BASS_SetVolume(value)
            for (listener in volumeChangedListeners) {
                listener.onVolumeChanged(value)
            }
        }

    override var progress: Long
        get() = BASS.BASS_ChannelGetPosition(chan, BASS.BASS_POS_BYTE) * 100 /
                BASS.BASS_ChannelGetLength(chan, BASS.BASS_POS_BYTE)
        set(value) {
            BASS.BASS_ChannelSetPosition(chan, (value * (fileLength ?: 0)) / 100,
                    BASS.BASS_POS_BYTE)
            if (BASS.BASS_ChannelIsActive(chan) != BASS.BASS_ACTIVE_PAUSED) {
                BASS.BASS_ChannelPlay(chan, false)
            }
        }

    override val isPaused
        get() = BASS.BASS_ChannelIsActive(chan) == BASS.BASS_ACTIVE_PAUSED

    override val fileLength: Long?
        get() {
            try {
                return BASS.BASS_ChannelGetLength(chan, BASS.BASS_POS_BYTE)
            } catch (e: Exception) {
                error("Error getting file length", BASS.BASS_ErrorGetCode(), e)
            }

            return null
        }

    override fun rec(isActive: Boolean) {
        // -------------------------------------
        if (playState !== PlayState.PLAY && playState !== PlayState.BUFFERING) {
            return
        }
        if (!isActive) {
            saveRecInfoToDatabase()
            //----------------------------------------------------
        } else {
            val dir = File(Environment.getExternalStorageDirectory().toString() + "/fantasyradio/records/")
            dir.mkdirs()
            var fileName: String? = title
            if (fileName == null || fileName.length < 2)
                fileName = "rec"
            for (s in reservedChars) {
                fileName = fileName!!.replace(s, "_")
            }
            try {
                fileName = String(fileName!!.toByteArray(), Charset.forName("UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                error("Can't create file for recording", -1, e)
            }

            var f = File(dir.toString() + "/" + fileName)
            try {
                if (!f.createNewFile()) {
                    f = File(f.toString() + System.currentTimeMillis())
                }
            } catch (e: IOException) {
                e.printStackTrace()
                error("Can't create file for recording", -1, e)
                return
            }

            recDirectory = f.toString()
        }
        // -------------------------------------
        isRecActive = isActive
    }

    private fun saveRecInfoToDatabase() {
        // Save rec file info into database
        if (recDirectory != null) {
            val mp3Entity = trackFactory.createTrack(author, title, recDirectory!!, "")
            mp3Collection.remove(mp3Entity)
            mp3Collection.add(mp3Entity)
        }
    }

    private fun error(message: String, code: Int, exception: Exception?) {
        for (listener in playerErrorEventListeners) {
            listener.onError(message, code, exception)
        }
    }

    override fun playFile(file: ITrack) {
        author = if (file.artist == null) "" else file.artist
        title = if (file.title == null) "" else if (file.artist == null) "" else file.artist
        val fileDirectory = file.directory
        BASS.BASS_StreamFree(chan)
        // -------------------------------------------------
        var x = BASS.BASS_StreamCreateFile(fileDirectory, 0, 0, 0)
        if (x == 0) {
            x = BASS.BASS_MusicLoad(fileDirectory, 0, 0, BASS.BASS_MUSIC_RAMP, 0)
            if (x == 0) {
                x = BASS_AAC.BASS_AAC_StreamCreateFile(fileDirectory, 0, 0, 0)
                if (x == 0) {
                    chan = x
                    error("Can't play the file", BASS.BASS_ErrorGetCode(), null)
                    currentMP3Entity = file
                    playState = PlayState.STOP
                    return
                }
            }
        }
        currentMP3Entity = file
        chan = x
        BASS.BASS_ChannelPlay(chan, false)
        BASS.BASS_ChannelSetSync(chan, BASS.BASS_SYNC_END, 0, EndSyncFile, null)
        playState = PlayState.PLAY_FILE
    }

    override fun pause() {
        BASS.BASS_ChannelPause(chan)
        playState = PlayState.PAUSE
    }

    override fun stop() {
        BASS.BASS_StreamFree(chan)
        if (isRecActive) {
            rec(false)
        }
        author = ""
        title = ""
        playState = PlayState.STOP
    }

    override fun addAuthorChangedListener(listener: IAuthorChangedListener) {
        authorChangedEventListeners.add(listener)
    }

    override fun removeAuthorChangedListener(listener: IAuthorChangedListener) {
        authorChangedEventListeners.remove(listener)
    }

    override fun addBufferingProgressChangedListener(listener: IBufferingProgressListener) {
        bufferingProgressEventListeners.add(listener)
    }

    override fun removeBufferingProgressChangedListener(listener: IBufferingProgressListener) {
        bufferingProgressEventListeners.remove(listener)
    }

    override fun addEndSyncListener(listener: IEndSyncListener) {
        endSyncEventListeners.add(listener)
    }

    override fun removeEndSyncListener(listener: IEndSyncListener) {
        endSyncEventListeners.remove(listener)
    }

    override fun addPlayerErrorListener(listener: IPlayerErrorListener) {
        playerErrorEventListeners.add(listener)
    }

    override fun removePlayerErrorListener(listener: IPlayerErrorListener) {
        playerErrorEventListeners.remove(listener)
    }

    override fun addPlayStateChangedListener(listener: IPlayStateChangedListener) {
        playStateChangedListeners.add(listener)
    }

    override fun removePlayStateChangedListener(listener: IPlayStateChangedListener) {
        playStateChangedListeners.remove(listener)
    }

    override fun addRecStateChangedListener(listener: IRecStateChangedListener) {
        recStateChangedListeners.add(listener)
    }

    override fun removeRecStateChangedListener(listener: IRecStateChangedListener) {
        recStateChangedListeners.remove(listener)
    }

    override fun addStreamChangedListener(listener: IStreamChangedListener<T>) {
        streamChangedListeners.add(listener)
    }

    override fun removeStreamChangedListener(listener: IStreamChangedListener<T>) {
        streamChangedListeners.remove(listener)
    }

    override fun addTitleChangedListener(listener: ITitleChangedListener) {
        titleChangedListeners.add(listener)
    }

    override fun removeTitleChangedListener(listener: ITitleChangedListener) {
        titleChangedListeners.remove(listener)
    }

    override fun addVolumeChangedListener(listener: IVolumeChangedListener) {
        volumeChangedListeners.add(listener)
    }

    override fun removeVolumeChangedListener(listener: IVolumeChangedListener) {
        volumeChangedListeners.remove(listener)
    }

    override fun addSyncStallListener(listener: ISyncStallListener) {
        syncStallListeners.add(listener)
    }

    override fun removeSyncStallListener(listener: ISyncStallListener) {
        syncStallListeners.remove(listener)
    }

    override fun removeAllListeners() {
        authorChangedEventListeners.clear()
        bufferingProgressEventListeners.clear()
        endSyncEventListeners.clear()
        playerErrorEventListeners.clear()
        playStateChangedListeners.clear()
        recStateChangedListeners.clear()
        streamChangedListeners.clear()
        titleChangedListeners.clear()
        volumeChangedListeners.clear()
        syncStallListeners.clear()
    }

    /**
     * Начинаем играть поток
     */
    override fun playStream(radioStream: T) {
        try {
            playState = PlayState.PLAY
            title = "Соединение..."
            stream = radioStream
            if (radioStream.streamFormat === StreamFormat.AAC) {
                Thread(OpenURLAAC(radioStream.streamURL)).start()
            } else {
                Thread(OpenURL(radioStream.streamURL)).start()
            }
        } catch (e: Exception) {
            error("Error playing stream", BASS.BASS_ErrorGetCode(), e)
        }

    }

    override fun resume() {
        BASS.BASS_ChannelPlay(chan, false)
        playState = PlayState.PLAY_FILE
    }

    /**
     * Магия BASS.dll для AAC потока
     */
    private inner class OpenURLAAC(internal var url: String) : Runnable {
        override fun run() {
            var r = 0
            synchronized(lock) {
                // make sure only 1 thread at a time can
                // do
                // the following
                r = ++req // increment the request counter for this request
            }
            BASS.BASS_StreamFree(chan) // close old stream
            val c = BASS_AAC
                    .BASS_AAC_StreamCreateURL(url, 0, BASS.BASS_STREAM_BLOCK
                            or BASS.BASS_STREAM_STATUS
                            or BASS.BASS_STREAM_AUTOFREE, StatusProc, r) // open
            // URL
            synchronized(lock) {
                if (r != req) { // there is a newer request, discard this
                    // stream
                    if (c != 0)
                        BASS.BASS_StreamFree(c)
                    return
                }
                chan = c // this is now the current stream
            }

            if (chan != 0) {
                handler.postDelayed(timer, 50)
            } // start prebuffer
            // monitoring
        }
    }

    /**
     * Магия BASS.dll
     */
    private inner class OpenURL(internal var url: String) : Runnable {
        override fun run() {
            var r = 0
            synchronized(lock) {
                // make sure only 1 thread at a time can
                // do
                // the following
                r = ++req // increment the request counter for this request
            }
            BASS.BASS_StreamFree(chan) // close old stream
            val c = BASS.BASS_StreamCreateURL(url, 0, BASS.BASS_STREAM_BLOCK
                    or BASS.BASS_STREAM_STATUS or BASS.BASS_STREAM_AUTOFREE,
                    StatusProc, r) // open URL
            synchronized(lock) {
                if (r != req) { // there is a newer request, discard this
                    // stream
                    if (c != 0)
                        BASS.BASS_StreamFree(c)
                    return
                }
                chan = c // this is now the current stream
            }

            if (chan != 0) { // failed to open
                handler.postDelayed(timer, 50) // start prebuffer
                // monitoring
            }
        }
    }

    private val lock = Any()
    private var req: Int = 0 // request number/counter
    private val handler = Handler()
    private val timer = object : Runnable {
        override fun run() {
            // monitor prebuffering progress
            val progress = BASS.BASS_StreamGetFilePosition(
                    chan, BASS.BASS_FILEPOS_BUFFER) * 100 / BASS.BASS_StreamGetFilePosition(chan,
                    BASS.BASS_FILEPOS_END) // percentage of buffer
            // filled
            if (progress > 75 || BASS.BASS_StreamGetFilePosition(chan,
                            BASS.BASS_FILEPOS_CONNECTED) == 0L) { // over 75%
                // full
                // (or
                // end
                // of
                // download)
                // get the broadcast name and URL
                var icy = BASS.BASS_ChannelGetTags(
                        chan, BASS.BASS_TAG_ICY) as Array<*>?
                if (icy == null)
                    icy = BASS.BASS_ChannelGetTags(chan, BASS.BASS_TAG_HTTP) as Array<*>? // no
                // ICY
                // tags,
                // try
                // HTTP
                // get the stream title and set sync for subsequent titles
                doMeta()
                BASS.BASS_ChannelSetSync(chan,
                        BASS.BASS_SYNC_META, 0, MetaSync, 0) // Shoutcast
                BASS.BASS_ChannelSetSync(chan,
                        BASS.BASS_SYNC_OGG_CHANGE, 0, MetaSync, 0) // Icecast/OGG
                // set sync for stalling/buffering
                BASS.BASS_ChannelSetSync(chan, BASS.BASS_SYNC_STALL, 0, StallSync, 0)
                // set sync for end of stream
                BASS.BASS_ChannelSetSync(chan, BASS.BASS_SYNC_END, 0, EndSync, 0)
                // PLAY it!
                BASS.BASS_ChannelPlay(chan, false)
            } else {
                setBufferingProgress(progress)
                handler.postDelayed(this, 50)
            }
        }
    }

    private fun setBufferingProgress(progress: Long) {
        for (listener in bufferingProgressEventListeners) {
            listener.onBufferingProgress(progress)
        }
    }

    private fun doMeta() {
        val meta = BASS.BASS_ChannelGetTags(chan, BASS.BASS_TAG_META) as String?
        if (meta != null) { // got Shoutcast metadata
            val ti = meta.indexOf("StreamTitle='")
            if (ti >= 0) {
                try {
                    var title = meta.substring(ti + 13, meta.indexOf("'", ti + 13))
                    title = String(title.toByteArray(charset("cp-1252")), charset("cp-1251"))
                    this.title = title
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } else {
                val ogg = BASS.BASS_ChannelGetTags(chan, BASS.BASS_TAG_OGG) as Array<String>?
                if (ogg != null) { // got Icecast/OGG tags
                    var artist: String? = null
                    var title: String? = null
                    for (s in ogg) {
                        if (s.regionMatches(0, "artist=", 0, 7, ignoreCase = true)) {
                            artist = s.substring(7)
                        } else if (s.regionMatches(0, "title=", 0, 6, ignoreCase = true)) {
                            title = s.substring(6)
                        }
                    }
                    if (title != null) {
                        this.title = title
                    }
                    if (artist != null)
                        author = artist

                }
            }
        } else {
            author = ""
            this.title = ""
        }
    }

    /**
     * Получаем метаданные (название, исполнитель и т.д.)
     */
    private val MetaSync = BASS.SYNCPROC { _, _, _, _ -> Thread(Runnable { doMeta() }).start() }

    /**
     * Выполняется после завершения проигрывания потока.
     */
    private val EndSync = BASS.SYNCPROC { _, _, _, _ ->
        for (listener in endSyncEventListeners) {
            listener.endSync()
        }
        val stream = stream
        if (stream != null) {
            stop()
            playStream(stream)
        }
    }

    /**
     * Выполняется после завершения проигрывания файла.
     */
    private val EndSyncFile = BASS.SYNCPROC { _, _, _, _ ->
        for (listener in endSyncEventListeners) {
            listener.endSync()
        }
    }

    private val StatusProc = BASS.DOWNLOADPROC { buffer, length, _ ->
        /**
         * Тут можно получить байты потока. Используется для записи.
         * @param buffer Данные потока
         * @param length Длина куска данных потока
         */
        if (isRecActive) {
            val ba = ByteArray(length)
            var fos: FileOutputStream? = null
            try {
                buffer.get(ba)
                //1111
                fos = FileOutputStream(recDirectory, true)
                fos.write(ba)
            } catch (e1: Exception) {
                error("Error recording stream", BASS.BASS_ErrorGetCode(), e1)
            }

            try {
                if (fos != null) {
                    fos.flush()
                    fos.close()
                }
            } catch (e1: Exception) {
                error("Error recording stream", BASS.BASS_ErrorGetCode(), e1)
            }

        }
    }

    /**
     * Выполняется при остановке воспроизведения
     */
    private val StallSync = BASS.SYNCPROC { _, _, data, _ ->
        //TODO исправить
        /*if (data == 0)
        // stalled
            handler.postDelayed(timer, 50) // start buffer monitoring
        for (listener in syncStallListeners) {
            listener.onSyncStall(data)
        }*/
    }
}