package io.github.newsticker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The heart: an overlay window pinned to the bottom of the screen, a 15-minute feed
 * refresh, and a foreground-app watcher that hides the bar while a video app plays.
 * Overlay is granted over ADB (_SYSTEM_ALERT_WINDOW appop); no UI runs from the service.
 */
public final class TickerService extends Service {
    private static final String CHANNEL = "tick";
    private static final long REFRESH_MIN = 15L;
    private static final long POLL_TOP_APP_S = 3L;
    private static final int MAX_PER_FEED = 12;

    /** Full-screen video apps of the owner; extended from time to time by hand. */
    private static final Set<String> VIDEO_APPS = new HashSet<>(List.of(
            "com.stremio.one", "com.google.android.youtube.tv", "com.google.android.youtube.tvmusic",
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom.nvidia",
            "com.amazon.amazonvideo.livingroom", "org.jellyfin.mobile", "org.videolan.vlc",
            "com.plexapp.plex", "tv.danmaku.ila.tv", "com.google.android.videos"));

    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler();
    private WindowManager wm;
    private TickerView view;
    private boolean shown;
    private String lastTop = "";
    private CharSequence cached = "";
    private CharSequence cachedBreaking = "";
    private int curLines = -1;
    private int curStyle = -1;
    private boolean refreshScheduled;
    private boolean pollScheduled;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Ticker", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("News Ticker")
                .setContentText("Barra notizie attiva")
                .build();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        boolean run = Prefs.enabled(this);
        refreshVisibility(run);
        if (run) {
            pool.execute(this::refreshAll);
            // once per service lifetime: extra onStartCommand calls (e.g. "Aggiorna ora")
            // must not stack duplicate periodic tasks
            if (!refreshScheduled) {
                refreshScheduled = true;
                pool.scheduleWithFixedDelay(this::refreshAll, REFRESH_MIN, REFRESH_MIN, TimeUnit.MINUTES);
            }
            if (!pollScheduled) {
                pollScheduled = true;
                pool.scheduleWithFixedDelay(this::pollTopApp, POLL_TOP_APP_S, POLL_TOP_APP_S, TimeUnit.SECONDS);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        pool.shutdownNow();
        removeOverlay();
        super.onDestroy();
    }

    // ---------- refresh ----------

    private void refreshAll() {
        List<Feed> feeds = Prefs.activeFeeds(this);
        int n = feeds.size();
        List<Integer> weights = new ArrayList<>(n);
        List<List<String>> fetched = new ArrayList<>(n);
        boolean anyFound = false;
        for (Feed f : feeds) {
            weights.add(Prefs.weight(this, f.name));
            try {
                fetched.add(trim(RssFetcher.titles(f.url, MAX_PER_FEED)));
                anyFound |= !fetched.get(fetched.size() - 1).isEmpty();
            } catch (Exception e) {
                fetched.add(null);   // keep the strip with what we already had on screen
            }
        }
        if (anyFound) {
            cached = Strip.join(feeds, weights, fetched);
        }
        // breaking line only exists in two-line mode
        if (Prefs.lines(this) == 2) {
            try {
                List<String> b = trim(RssFetcher.titles(Sources.BREAKING.url, 8));
                if (!b.isEmpty()) cachedBreaking = Strip.joinSimple(b);
            } catch (Exception e) {
                // keep the last breaking text on transient failures
            }
        }
        final CharSequence forText = cached;
        final CharSequence forBreaking = cachedBreaking;
        android.util.Log.i("NewsTick", "refresh done, items=" + feeds.size());
        main.post(() -> {
            if (view != null) {
                view.setContent(forText);
                if (Prefs.lines(TickerService.this) == 2) view.setBreaking(forBreaking);
                view.animate(shown);
            }
        });
    }

    private static List<String> trim(List<String> in) {
        if (in.size() <= MAX_PER_FEED) return in;
        return new ArrayList<>(in.subList(0, MAX_PER_FEED));
    }

    // ---------- top-app watcher ----------

    private void pollTopApp() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(now - 49_000, now);
            while (ev != null && ev.hasNextEvent()) {
                UsageEvents.Event e = new UsageEvents.Event();
                if (ev.getNextEvent(e)) {
                    if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                            && e.getPackageName() != null) {
                        lastTop = e.getPackageName();
                    }
                } else break;
            }
            boolean hide = VIDEO_APPS.contains(lastTop)
                    || ("home".equals(Prefs.mode(TickerService.this)) && !isLauncher(lastTop))
                    || "io.github.newsticker".equals(lastTop);
            if (hide && shown) {
                main.post(() -> setShown(false));
            } else if (!hide && !shown && view != null) {
                main.post(() -> setShown(true));
            }
        } catch (Exception e) {
            // Short-pol failures are noisy; only debug when run manually.
        }
    }

    /** Launchers that answer HOME on this TV; cached once. */
    private Set<String> launchers = null;

    private boolean isLauncher(String pkg) {
        if (launchers == null) {
            Set<String> s = new HashSet<>();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<android.content.pm.ResolveInfo> acts = getPackageManager()
                    .queryIntentActivities(q, PackageManager.MATCH_ALL);
            for (android.content.pm.ResolveInfo r : acts) {
                s.add(r.activityInfo == null ? null : r.activityInfo.packageName);
            }
            launchers = s;
        }
        return launchers.contains(pkg);
    }

    // ---------- overlay ----------

    private void refreshVisibility(boolean shouldRun) {
        if (shouldRun) addOverlay(); else removeOverlay();
    }

    private void addOverlay() {
        int lines = Prefs.lines(this);
        int style = Prefs.styleIndex(this);
        if (view != null) {
            if (curLines != lines || curStyle != style) {
                removeOverlay();   // rebuild with the new height/skin
            } else {
                setShown(true);
                return;
            }
        }
        if (!Prefs.enabled(this)) return;
        curLines = lines;
        curStyle = style;
        float d = getResources().getDisplayMetrics().density;
        int hDp = lines == 2 ? 58 : 34;
        view = new TickerView(this, style, lines == 2);
        view.setContent(cached);
        if (lines == 2) view.setBreaking(cachedBreaking);
        view.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, (int) (hDp * d)));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, (int) (hDp * d),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        try {
            wm.addView(view, lp);
            setShown(true);
        } catch (Exception e) {
            view = null;
        }
    }

    private void removeOverlay() {
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
            view = null;
        }
        shown = false;
    }

    private void setShown(boolean s) {
        if (view == null) return;
        if (s && !Prefs.enabled(this)) return;
        shown = s;
        view.animate(s);
    }
}
