package ru.sigil.bassplayerlib;

import android.os.Environment;
import android.os.Handler;

import com.un4seen.bass.BASS;
import com.un4seen.bass.BASS_AAC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import ru.sigil.bassplayerlib.listeners.IAuthorChangedListener;
import ru.sigil.bassplayerlib.listeners.IBufferingProgressListener;
import ru.sigil.bassplayerlib.listeners.IEndSyncListener;
import ru.sigil.bassplayerlib.listeners.IPlayStateChangedListener;
import ru.sigil.bassplayerlib.listeners.IPlayerErrorListener;
import ru.sigil.bassplayerlib.listeners.IRecStateChangedListener;
import ru.sigil.bassplayerlib.listeners.IStreamChangedListener;
import ru.sigil.bassplayerlib.listeners.ISyncStallListener;
import ru.sigil.bassplayerlib.listeners.ITitleChangedListener;
import ru.sigil.bassplayerlib.listeners.IVolumeChangedListener;

/**
 * Created by NamelessOne
 * on 17.09.2016.
 */
public class Player<T extends IRadioStream> implements IPlayer<T> {

    private final Set<IAuthorChangedListener> authorChangedEventListeners = new HashSet<>();
    private final Set<IBufferingProgressListener> bufferingProgressEventListeners = new HashSet<>();
    private final Set<IEndSyncListener> endSyncEventListeners = new HashSet<>();
    private final Set<IPlayerErrorListener> playerErrorEventListeners = new HashSet<>();
    private final Set<IPlayStateChangedListener> playStateChangedListeners = new HashSet<>();
    private final Set<IRecStateChangedListener> recStateChangedListeners = new HashSet<>();
    private final Set<IStreamChangedListener<T>> streamChangedListeners = new HashSet<>();
    private final Set<ITitleChangedListener> titleChangedListeners = new HashSet<>();
    private final Set<IVolumeChangedListener> volumeChangedListeners = new HashSet<>();
    private final Set<ISyncStallListener> syncStallListeners = new HashSet<>();

    private String title;
    private String author;
    private T stream;
    private PlayState playState = PlayState.STOP;
    private boolean rec = false;
    private String recDirectory;
    private ITrackFactory trackFactory;
    private ITrack currentMP3Entity;
    //TODO вынести наружу, делать через Event
    private ITracksCollection mp3Collection;

    private int chan;

    public int getChan() {
        return chan;
    }

    public float getVolume() {
        return BASS.BASS_GetVolume();
    }

    @Override
    public void rec(boolean isActive) {
        // -------------------------------------
        if (currentState() != PlayState.PLAY && currentState() != PlayState.BUFFERING) {
            return;
        }
        if (!isActive) {
            saveRecInfoToDatabase();
            //----------------------------------------------------
        } else {
            File dir = new File(Environment.getExternalStorageDirectory()
                    + "/fantasyradio/records/");
            dir.mkdirs();
            String fileName = title;
            if (fileName == null)
                fileName = "rec";
            if (fileName.length() < 2)
                fileName = "rec";
            try {
                for (String s : RESERVED_CHARS) {
                    fileName = fileName.replace(s, "_");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                fileName = new String(fileName.getBytes(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            File f = new File(dir.toString() + "/" + fileName);
            try {
                if (!f.createNewFile()) {
                    f = new File(f.toString()
                            + System.currentTimeMillis());
                }
            } catch (IOException e) {
                e.printStackTrace();
                error("Не удалось создать файл для записи", -1);
                return;
            }
            recDirectory = f.toString();
        }
        // -------------------------------------
        setRecActive(isActive);
    }

    private void saveRecInfoToDatabase() {
        // А тут мы пишем инфу о записанном
        // файле в базу
        ITrack mp3Entity = trackFactory.createTrack(author, title, recDirectory, "");
        mp3Collection.remove(mp3Entity);
        mp3Collection.add(mp3Entity);
    }

    @Override
    public long getFileLength() {
        return BASS.BASS_ChannelGetLength(
                getChan(), BASS.BASS_POS_BYTE);
    }

    public void setChan(int chan) {
        this.chan = chan;
    }

    public void playAAC(T stream) {
        try {
            setPlayState(PlayState.PLAY);
            setTitle("Соединение...");
            setStream(stream);
            new Thread(new OpenURLAAC(stream.getStreamURL())).start();
        } catch (Exception e) {
            setPlayState(PlayState.STOP);
            e.printStackTrace();
        }
    }

    @Override
    public void playFile(ITrack entity) {
        //TODO
        setAuthor(entity.getArtist() == null ? "" : entity.getArtist());
        setTitle(entity.getTitle() == null ? "" : entity.getArtist());
        String file = entity.getDirectory();
        BASS.BASS_StreamFree(getChan());
        // -------------------------------------------------
        int x;
        if ((x = BASS.BASS_StreamCreateFile(file, 0, 0, 0)) == 0
                && (x = BASS.BASS_MusicLoad(file, 0, 0,
                BASS.BASS_MUSIC_RAMP, 0)) == 0) {
            if ((x = BASS_AAC.BASS_AAC_StreamCreateFile(file, 0, 0, 0)) == 0) {
                // whatever it is, it ain't playable
                setChan(x);
                error("Can't play the file", BASS.BASS_ErrorGetCode());
                currentMP3Entity = entity;
                setPlayState(PlayState.STOP);
                return;
            }
        }
        currentMP3Entity = entity;
        setChan(x);
        BASS.BASS_ChannelPlay(getChan(), false);
        BASS.BASS_ChannelSetSync(getChan(), BASS.BASS_SYNC_END, 0,
                EndSync, null);
        setPlayState(PlayState.PLAY_FILE);
    }

    private void error(String message, int code) {
        for (IPlayerErrorListener listener : playerErrorEventListeners) {
            listener.onError(message, code);
        }
    }

    @Override
    public boolean isPaused() {
        //TODO по хорошему это должен быть state
        return BASS.BASS_ChannelIsActive(getChan()) == BASS.BASS_ACTIVE_PAUSED;
    }

    @Override
    public void pause() {
        //TODO
        BASS.BASS_ChannelPause(getChan());
        setPlayState(PlayState.PAUSE);
    }

    public Player(ITracksCollection mp3Collection, ITrackFactory trackFactory, T initialStream) {
        this.mp3Collection = mp3Collection;
        this.trackFactory = trackFactory;
        this.setStream(initialStream);
        BASS.BASS_Free();
        BASS.BASS_Init(-1, 44100, 0);
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_NET_PLAYLIST, 1);
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_NET_PREBUF, 0);
        BASS.BASS_SetVolume((float) 0.5);
    }

    @Override
    public void stop() {
        //TODO BASS.BASS_ChannelStop(getChan()); ???
        BASS.BASS_StreamFree(getChan());
        if (rec) {
            rec(false);
        }
        setAuthor("");
        setTitle("");
        setPlayState(PlayState.STOP);
    }

    @Override
    public void addAuthorChangedListener(IAuthorChangedListener listener) {
        authorChangedEventListeners.add(listener);
    }

    public void removeAuthorChangedListener(IAuthorChangedListener listener) {
        authorChangedEventListeners.remove(listener);
    }

    @Override
    public void addBufferingProgressChangedListener(IBufferingProgressListener listener) {
        bufferingProgressEventListeners.add(listener);
    }

    public void removeBufferingProgressChangedListener(IBufferingProgressListener listener) {
        bufferingProgressEventListeners.remove(listener);
    }

    @Override
    public void addEndSyncListener(IEndSyncListener listener) {
        endSyncEventListeners.add(listener);
    }

    @Override
    public void removeEndSyncListener(IEndSyncListener listener) {
        endSyncEventListeners.remove(listener);
    }

    @Override
    public void addPlayerErrorListener(IPlayerErrorListener listener) {
        playerErrorEventListeners.add(listener);
    }

    @Override
    public void removePlayerErrorListener(IPlayerErrorListener listener) {
        playerErrorEventListeners.remove(listener);
    }

    @Override
    public void addPlayStateChangedListener(IPlayStateChangedListener listener) {
        playStateChangedListeners.add(listener);
    }

    @Override
    public void removePlayStateChangedListener(IPlayStateChangedListener listener) {
        playStateChangedListeners.remove(listener);
    }

    @Override
    public void addRecStateChangedListener(IRecStateChangedListener listener) {
        recStateChangedListeners.add(listener);
    }

    @Override
    public void removeRecStateChangedListener(IRecStateChangedListener listener) {
        recStateChangedListeners.remove(listener);
    }

    @Override
    public void addStreamChangedListener(IStreamChangedListener<T> listener) {
        streamChangedListeners.add(listener);
    }

    @Override
    public void removeStreamChangedListener(IStreamChangedListener<T> listener) {
        streamChangedListeners.remove(listener);
    }

    @Override
    public void addTitleChangedListener(ITitleChangedListener listener) {
        titleChangedListeners.add(listener);
    }

    @Override
    public void removeTitleChangedListener(ITitleChangedListener listener) {
        titleChangedListeners.remove(listener);
    }

    @Override
    public void addVolumeChangedListener(IVolumeChangedListener listener) {
        volumeChangedListeners.add(listener);
    }

    @Override
    public void removeVolumeChangedListener(IVolumeChangedListener listener) {
        volumeChangedListeners.remove(listener);
    }

    @Override
    public void addSyncStallListener(ISyncStallListener listener) {
        syncStallListeners.add(listener);
    }

    @Override
    public void removeSyncStallListener(ISyncStallListener listener) {
        syncStallListeners.remove(listener);
    }

    @Override
    public void removeAllListeners() {
        authorChangedEventListeners.clear();
        bufferingProgressEventListeners.clear();
        endSyncEventListeners.clear();
        playerErrorEventListeners.clear();
        playStateChangedListeners.clear();
        recStateChangedListeners.clear();
        streamChangedListeners.clear();
        titleChangedListeners.clear();
        volumeChangedListeners.clear();
        syncStallListeners.clear();
    }

    @Override
    public String currentTitle() {
        return title;
    }

    @Override
    public String currentArtist() {
        return author;
    }

    @Override
    public T currentStream() {
        return stream;
    }

    @Override
    public PlayState currentState() {
        //TODO
        return playState;
    }

    private void setPlayState(PlayState state) {
        playState = state;
        for (IPlayStateChangedListener listener : playStateChangedListeners) {
            listener.onPlayStateChanged(state);
        }
        if (state != PlayState.PLAY && state != PlayState.BUFFERING) {
            setRecActive(false);
        }
    }

    @Override
    public boolean isRecActive() {
        //TODO
        return rec;
    }

    public void setStream(T stream) {
        this.stream = stream;
        for (IStreamChangedListener listener : streamChangedListeners) {
            listener.onStreamChanged(stream);
        }
    }

    /**
     * Начинаем играть поток
     */
    @Override
    public void play(T stream) {
        try {
            setPlayState(PlayState.PLAY);
            setTitle("Соединение...");
            setStream(stream);
            new Thread(new OpenURL(stream.getStreamURL())).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setRecActive(boolean recActive) {
        rec = recActive;
        for (IRecStateChangedListener listener : recStateChangedListeners) {
            listener.onRecStateChanged(recActive);
        }
    }

    public ITrack getCurrentMP3Entity() {
        return currentMP3Entity;
    }

    @Override
    public void setVolume(float volume) {
        BASS.BASS_SetVolume(volume);
        for (IVolumeChangedListener listener : volumeChangedListeners) {
            listener.onVolumeChanged(volume);
        }
    }

    //TODO сделать методом интерфейса?
    private long fileLength() {
        try {
            return BASS.BASS_ChannelGetLength(getChan(), BASS.BASS_POS_BYTE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public void setProgress(long progress) {
        BASS.BASS_ChannelSetPosition(getChan(), (progress * fileLength()) / 100,
                BASS.BASS_POS_BYTE);
        if (!(BASS.BASS_ChannelIsActive(getChan()) == BASS.BASS_ACTIVE_PAUSED)) {
            BASS.BASS_ChannelPlay(getChan(), false);
        }
    }

    @Override
    public void resume() {
        BASS.BASS_ChannelPlay(getChan(), false);
        setPlayState(PlayState.PLAY_FILE);
    }

    @Override
    public long getProgress() {
        return BASS.BASS_ChannelGetPosition(
                getChan(), BASS.BASS_POS_BYTE)
                * 100
                / BASS.BASS_ChannelGetLength(getChan(),
                BASS.BASS_POS_BYTE);
    }

    /**
     * Магия BASS.dll для AAC потока
     */
    private class OpenURLAAC implements Runnable {
        String url;

        public OpenURLAAC(String p) {
            url = p;
        }

        public void run() {
            int r;
            synchronized (lock) { // make sure only 1 thread at a time can
                // do
                // the following
                r = ++req; // increment the request counter for this request
            }
            BASS.BASS_StreamFree(chan); // close old stream
            int c = BASS_AAC
                    .BASS_AAC_StreamCreateURL(url, 0, BASS.BASS_STREAM_BLOCK
                            | BASS.BASS_STREAM_STATUS
                            | BASS.BASS_STREAM_AUTOFREE, StatusProc, r); // open
            // URL
            synchronized (lock) {
                if (r != req) { // there is a newer request, discard this
                    // stream
                    if (c != 0)
                        BASS.BASS_StreamFree(c);
                    return;
                }
                setChan(c); // this is now the current stream
            }

            if (chan != 0) {
                handler.postDelayed(timer, 50);
            } // start prebuffer
            // monitoring
        }
    }

    /**
     * Магия BASS.dll
     */
    private class OpenURL implements Runnable {
        String url;

        public OpenURL(String p) {
            url = p;
        }

        public void run() {
            int r;
            synchronized (lock) { // make sure only 1 thread at a time can
                // do
                // the following
                r = ++req; // increment the request counter for this request
            }
            BASS.BASS_StreamFree(getChan()); // close old stream
            int c = BASS.BASS_StreamCreateURL(url, 0, BASS.BASS_STREAM_BLOCK
                            | BASS.BASS_STREAM_STATUS | BASS.BASS_STREAM_AUTOFREE,
                    StatusProc, r); // open URL
            synchronized (lock) {
                if (r != req) { // there is a newer request, discard this
                    // stream
                    if (c != 0)
                        BASS.BASS_StreamFree(c);
                    return;
                }
                setChan(c); // this is now the current stream
            }

            if (getChan() != 0) { // failed to open
                handler.postDelayed(timer, 50); // start prebuffer
                // monitoring
            }
        }

    }

    private final Object lock = new Object();
    private int req; // request number/counter
    private Handler handler = new Handler();
    private Runnable timer = new Runnable() {
        public void run() {
            // monitor prebuffering progress
            long progress = BASS.BASS_StreamGetFilePosition(
                    getChan(), BASS.BASS_FILEPOS_BUFFER)
                    * 100
                    / BASS.BASS_StreamGetFilePosition(getChan(),
                    BASS.BASS_FILEPOS_END); // percentage of buffer
            // filled
            if (progress > 75
                    || BASS.BASS_StreamGetFilePosition(getChan(),
                    BASS.BASS_FILEPOS_CONNECTED) == 0) { // over 75%
                // full
                // (or
                // end
                // of
                // download)
                // get the broadcast name and URL
                String[] icy = (String[]) BASS.BASS_ChannelGetTags(
                        getChan(), BASS.BASS_TAG_ICY);
                if (icy == null)
                    icy = (String[]) BASS.BASS_ChannelGetTags(
                            getChan(), BASS.BASS_TAG_HTTP); // no
                // ICY
                // tags,
                // try
                // HTTP
                // get the stream title and set sync for subsequent titles
                DoMeta();
                BASS.BASS_ChannelSetSync(getChan(),
                        BASS.BASS_SYNC_META, 0, MetaSync, 0); // Shoutcast
                BASS.BASS_ChannelSetSync(getChan(),
                        BASS.BASS_SYNC_OGG_CHANGE, 0, MetaSync, 0); // Icecast/OGG
                // set sync for stalling/buffering
                BASS.BASS_ChannelSetSync(getChan(), BASS.BASS_SYNC_STALL, 0, StallSync, 0);
                // set sync for end of stream
                BASS.BASS_ChannelSetSync(getChan(), BASS.BASS_SYNC_END, 0, EndSync, 0);
                // PLAY it!
                BASS.BASS_ChannelPlay(getChan(), false);
            } else {
                setBufferingProgress(progress);
                handler.postDelayed(this, 50);
            }
        }
    };

    private void setBufferingProgress(long progress) {
        for (IBufferingProgressListener listener : bufferingProgressEventListeners) {
            listener.onBufferingProgress(progress);
        }
    }

    private void setTitle(String title) {
        this.title = title;
        for (ITitleChangedListener listener : titleChangedListeners) {
            listener.onTitleChanged(title);
        }
    }

    private void setAuthor(String author) {
        this.author = author;
        for (IAuthorChangedListener listener : authorChangedEventListeners) {
            listener.onAuthorChanged(author);
        }
    }

    private void DoMeta() {
        String meta = (String) BASS.BASS_ChannelGetTags(getChan(),
                BASS.BASS_TAG_META);
        if (meta != null) { // got Shoutcast metadata
            int ti = meta.indexOf("StreamTitle='");
            if (ti >= 0) {
                try {
                    String title = meta.substring(ti + 13, meta.indexOf("'", ti + 13));
                    title = new String(title.getBytes("cp-1252"), "cp-1251");
                    setTitle(title);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                String[] ogg = (String[]) BASS.BASS_ChannelGetTags(
                        getChan(), BASS.BASS_TAG_OGG);
                if (ogg != null) { // got Icecast/OGG tags
                    String artist = null, title = null;
                    for (String s : ogg) {
                        if (s.regionMatches(true, 0, "artist=", 0, 7)) {
                            artist = s.substring(7);
                        } else if (s.regionMatches(true, 0, "title=", 0, 6)) {
                            title = s.substring(6);
                        }
                    }
                    if (title != null) {
                        setTitle(title);
                    }
                    if (artist != null)
                        setAuthor(artist);

                }
            }
        } else {
            setAuthor("");
            setTitle("");
        }
    }

    /**
     * Получаем метаданные (название, исполнитель и т.д.)
     */
    private BASS.SYNCPROC MetaSync = new BASS.SYNCPROC() {
        public void SYNCPROC(int handle, int channel, int data, Object user) {
            new Thread(new Runnable() {
                public void run() {
                    DoMeta();
                }
            }).start();
        }
    };

    /**
     * Выполняется после завершения проигрывания.
     */
    private BASS.SYNCPROC EndSync = new BASS.SYNCPROC() {
        public void SYNCPROC(int handle, int channel, int data, Object user) {
            for (IEndSyncListener listener : endSyncEventListeners) {
                listener.endSync();
            }
        }
    };

    private BASS.DOWNLOADPROC StatusProc = new BASS.DOWNLOADPROC() {
        /**
         * Тут можно получить байты потока. Используется для записи.
         * @param buffer Данные потока
         * @param length Длина куска данных потока
         * @param user BASS.dll магия. ХЗ что это
         */
        public void DOWNLOADPROC(ByteBuffer buffer, int length, Object user) {
            if (rec) {
                byte[] ba = new byte[length];
                FileOutputStream fos = null;
                try {
                    buffer.get(ba);
                    //1111
                    fos = new FileOutputStream(recDirectory, true);
                    fos.write(ba);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                try {
                    if (fos != null) {
                        fos.flush();
                        fos.close();
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }
    };

    /**
     Выполняется при остановке воспроизведения
     */
    BASS.SYNCPROC StallSync = new BASS.SYNCPROC() {
        public void SYNCPROC(int handle, int channel, int data, Object user) {
            if (data==0) // stalled
                handler.postDelayed(timer, 50); // start buffer monitoring
            for (ISyncStallListener listener : syncStallListeners) {
                listener.onSyncStall();
            }
        }
    };
}
