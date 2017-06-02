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

/**
 * Created by NamelessOne
 * on 17.09.2016.
 */
public class Player<T extends IRadioStream> implements IPlayer<T> {
    private final Set<IPlayerEventListener> eventListeners = new HashSet<>();
    private final Set<IPLayerErrorListener> errorListeners = new HashSet<>();
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
        for (IPLayerErrorListener listener : errorListeners) {
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
        this.stream = initialStream; //TODO избавиться от этого параметра в конструкторе
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
        rec(false);
        setAuthor("");
        setTitle("");
        setPlayState(PlayState.STOP);
    }

    @Override
    public void addEventListener(IPlayerEventListener listener) {
        eventListeners.add(listener);
    }

    @Override
    public void removeEventListener(IPlayerEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public void removeErrorListener(IPLayerErrorListener listener) {
        errorListeners.remove(listener);
    }

    @Override
    public void addErrorListener(IPLayerErrorListener listener) {
        errorListeners.add(listener);
    }

    @Override
    public void removeAllListeners() {
        eventListeners.clear();
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
        for (IPlayerEventListener listener : eventListeners) {
            try {
                listener.onPlayStateChanged(state);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //TODO ???
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
        for (IPlayerEventListener listener : eventListeners) {
            try {
                listener.onRecStateChanged(recActive);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ITrack getCurrentMP3Entity() {
        return currentMP3Entity;
    }

    @Override
    public void setVolume(float volume) {
        BASS.BASS_SetVolume(volume);
        for (IPlayerEventListener listener : eventListeners) {
            try {
                listener.onVolumeChanged(volume);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                // set sync for end of stream
                BASS.BASS_ChannelSetSync(getChan(),
                        BASS.BASS_SYNC_END, 0, EndSync, 0);
                // PLAY it!
                BASS.BASS_ChannelPlay(getChan(), false);
            } else {
                setBufferingProgress(progress);
                handler.postDelayed(this, 50);
            }
        }
    };

    private void setBufferingProgress(long progress) {
        for (IPlayerEventListener listener : eventListeners) {
            try {
                listener.onBufferingProgress(progress);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setTitle(String title) {
        this.title = title;
        for (IPlayerEventListener listener : eventListeners) {
            try {
                listener.onTitleChanged(title);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setAuthor(String author) {
        this.author = author;
        //List<IPlayerEventListener> wrongListeners = new ArrayList<>();
        for (IPlayerEventListener listener : eventListeners) {
            try {
                listener.onAuthorChanged(author);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
     * Выполняется после завершения проигрывания. В данный момент не используестя
     */

    private BASS.SYNCPROC EndSync = new BASS.SYNCPROC() {
        public void SYNCPROC(int handle, int channel, int data, Object user) {
            for (IPlayerEventListener listener : eventListeners) {
                try {
                    listener.endSync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
}
