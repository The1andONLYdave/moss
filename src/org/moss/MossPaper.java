package org.moss;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class MossPaper extends WallpaperService {

    static final String SHARED_PREFS_NAME = "MossSettings";
    static final String TAG = "MossPaper";

    static final int DEFAULT_INTERVAL = 1000;

    private MossEngine mossEngine;
    private final IBinder mBinder = new PaperBinder();

    class PaperBinder extends Binder {
        MossPaper getService() {
            return MossPaper.this;
        }
    }

    public MossPaper() { }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public Engine onCreateEngine() {
        this.mossEngine = new MossEngine();
        return mossEngine;
    }

    public MossEngine getEngine() {
        return mossEngine;
    }

    class MossEngine extends Engine
        implements SharedPreferences.OnSharedPreferenceChangeListener {

        private final Handler mHandler = new Handler();
        private float mOffset;
        private SharedPreferences prefs;
        private Env.Current single = Env.Current.INSTANCE;
        private DataService dataService;
        private boolean isBound = false;
        private boolean isVisible = false;


        private final Runnable mDrawMoss = new Runnable() {
            public void run() {
                drawFrame();
            }
        };

        private ServiceConnection sconn = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                dataService = ((DataService.DataBinder) service).getService();
                if (null != single.env) {
                    dataService.setDataProviders(single.env.getDataProviders());
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                dataService = null;
            }
        };

        MossEngine() {
            mOffset = 0.5f;
            prefs = MossPaper.this.getSharedPreferences(SHARED_PREFS_NAME, 0);
            prefs.registerOnSharedPreferenceChangeListener(this);

            /* Maybe this should be in its own thread */
            /* Load config */
            Env.reload(MossPaper.this, prefs);
            onSharedPreferenceChanged(prefs, null);

            /* Start the data service */
            doBindService();

        }

        void doBindService() {
            if (!isBound) {
                bindService(new Intent(MossPaper.this,
                        DataService.class), sconn, Context.BIND_AUTO_CREATE);
                isBound = true;
            }
        }

        void doUnbindService() {
            if (isBound) {
                unbindService(sconn);
                isBound = false;
            }
        }

        void reloadConfig(SharedPreferences prefs) {
            if (prefs == null) {
                prefs = this.prefs;
            }

            single.env.startFileWatcher(mHandler, new Runnable() {
                public void run() {
                    reloadConfig(null);
                }
            });

            /* Update data providers and restart service */
            if (null != single.env.getDataProviders()) {
                doBindService();
                if (null != dataService) {
                    dataService.setDataProviders(single.env.getDataProviders());
                }
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (key == null
                    || "config_file".equals(key)
                    || "sample_config_file".equals(key)) {
                reloadConfig(prefs);
            } else {
                single.env.loadPrefs(prefs);
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            doUnbindService();
            mHandler.removeCallbacks(mDrawMoss);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.isVisible = visible;
            if (dataService != null) {
                dataService.setPaperVisible(isVisible);
            }
            if (isVisible) {
                drawFrame();
            } else {
                mHandler.removeCallbacks(mDrawMoss);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            single.env.setPaperHeight(height);
            single.env.setPaperWidth(width);
            drawFrame();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.isVisible = false;
            if (dataService != null) {
                dataService.setPaperVisible(isVisible);
            }
            mHandler.removeCallbacks(mDrawMoss);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                float xStep, float yStep, int xPixels, int yPixels) {
            this.mOffset = xOffset;
        }

        void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            final Rect frame = holder.getSurfaceFrame();
            final int width = frame.width();
            final int height = frame.height();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    single.env.draw(c);
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }

            mHandler.removeCallbacks(mDrawMoss);
            if (this.isVisible) {
                mHandler.postDelayed(mDrawMoss, DEFAULT_INTERVAL);
                if (null != dataService) {
                    dataService.setPaperVisible(isVisible);
                }
            }
        }
    }
}