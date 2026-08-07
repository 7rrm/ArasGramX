package tw.nekomimi.nekogram;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/**
 * MeeroX v159 (approved feature): Auto Janitor — scheduled, size-capped cache
 * cleaning that runs by itself.
 *
 * What it does:
 *   1. On app start (15s late, fully off the UI thread) it checks whether a
 *      cleaning pass is due: daily/weekly by the chosen schedule, or whenever
 *      the cache is over the chosen size limit.
 *   2. A pass deletes re-downloadable cloud-media copies only (images, video,
 *      voice, documents, stories, stream cache): files older than the chosen
 *      age first, then - if still over the limit - oldest-first until the
 *      cache fits again.
 *   3. A small toast reports how much was freed.
 *
 * Hard safety rules:
 *   - Messages, the database, settings and accounts are NEVER touched; only
 *     plain files inside Telegram's media/cache directories are removed, all
 *     of which Telegram transparently re-downloads on demand.
 *   - Music files (.mp3/.m4a) are always kept - the user usually saves those
 *     deliberately.
 *   - The master switch defaults OFF; nothing ever runs without the user
 *     arming it first.
 */
public class MeeroJanitor {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** SelectBox choices: must match the settings rows exactly. */
    public static final long[] LIMIT_GB = {1, 2, 4, 8, 16};
    public static final long[] AGE_DAYS = {7, 14, 30};

    private static boolean started;
    private static volatile boolean running;

    private MeeroJanitor() {
    }

    /** Idempotent entry point from ApplicationLoader.onCreate. */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        // Late enough that first frames, login and cold-sync all win the race.
        Utilities.cacheClearQueue.postRunnable(MeeroJanitor::maybeRun, 15_000);
    }

    public static long limitBytes() {
        int idx = NekoConfig.meeroJanitorLimit.Int();
        if (idx < 0 || idx >= LIMIT_GB.length) {
            idx = 3;
        }
        return LIMIT_GB[idx] * 1024L * 1024L * 1024L;
    }

    public static long ageMillis() {
        int idx = NekoConfig.meeroJanitorAge.Int();
        if (idx < 0 || idx >= AGE_DAYS.length) {
            idx = 1;
        }
        return AGE_DAYS[idx] * DAY_MS;
    }

    /** Settings labels for the limit SelectBox. */
    public static String[] limitTitles() {
        final String[] out = new String[LIMIT_GB.length];
        for (int i = 0; i < LIMIT_GB.length; i++) {
            out[i] = LIMIT_GB[i] + " GB";
        }
        return out;
    }

    private static boolean musicFile(String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".m4a");
    }

    /** Recursively gathers files with their sizes; returns the total size. */
    private static long collect(File dir, ArrayList<File> out) {
        if (dir == null) {
            return 0;
        }
        final File[] entries;
        try {
            entries = dir.listFiles();
        } catch (Throwable e) {
            return 0;
        }
        if (entries == null) {
            return 0;
        }
        long total = 0;
        for (File f : entries) {
            try {
                if (f.isDirectory()) {
                    total += collect(f, out);
                } else if (!musicFile(f.getName())) {
                    out.add(f);
                    total += f.length();
                }
            } catch (Throwable ignore) {
            }
        }
        return total;
    }

    private static void maybeRun() {
        try {
            if (running || !NekoConfig.meeroAutoJanitor.Bool()) {
                return;
            }
        } catch (Throwable e) {
            return;
        }
        if (UserConfig.getActivatedAccountsCount() < 1) {
            return;
        }
        final long now = System.currentTimeMillis();
        int mode;
        long lastRun;
        try {
            mode = NekoConfig.meeroJanitorMode.Int();
            lastRun = NekoConfig.meeroJanitorLastRun.Long();
        } catch (Throwable e) {
            return;
        }
        final long interval = (mode == 0 ? DAY_MS : 7L * DAY_MS);
        final boolean scheduledDue = mode != 2 && now - lastRun >= interval;
        running = true;
        // Never delete a file another FileLoader thread is about to write.
        FileLoader.getInstance(UserConfig.selectedAccount).getFileLoaderQueue().postRunnable(() -> {
            FileLoader.getInstance(UserConfig.selectedAccount).cancelLoadAllFiles();
            Utilities.globalQueue.postRunnable(() -> runPass(now, scheduledDue));
        });
    }

    private static void runPass(long now, boolean scheduledDue) {
        try {
            final ArrayList<File> files = new ArrayList<>();
            long total = 0;
            final int[] types = {
                    FileLoader.MEDIA_DIR_IMAGE,
                    FileLoader.MEDIA_DIR_VIDEO,
                    FileLoader.MEDIA_DIR_AUDIO,
                    FileLoader.MEDIA_DIR_DOCUMENT,
                    FileLoader.MEDIA_DIR_STORIES,
                    FileLoader.MEDIA_DIR_CACHE,
            };
            for (int type : types) {
                try {
                    total += collect(FileLoader.checkDirectory(type), files);
                } catch (Throwable ignore) {
                }
            }

            final long limit = limitBytes();
            final boolean overLimit = total > limit;
            if (!scheduledDue && !overLimit) {
                return;
            }

            long freed = 0;
            // Phase 1 (scheduled passes): age out the old files first.
            if (scheduledDue) {
                final long cutoff = now - ageMillis();
                for (int i = files.size() - 1; i >= 0; i--) {
                    final File f = files.get(i);
                    final long len = f.length();
                    if (f.lastModified() < cutoff && f.delete()) {
                        freed += len;
                        files.remove(i);
                    }
                }
            }

            // Phase 2: still over the limit? Reclaim oldest-first until it fits.
            long remaining = total - freed;
            if (remaining > limit && !files.isEmpty()) {
                files.sort(Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < files.size() && remaining > limit; i++) {
                    final File f = files.get(i);
                    final long len = f.length();
                    if (f.delete()) {
                        remaining -= len;
                        freed += len;
                    }
                }
            }

            NekoConfig.meeroJanitorLastRun.setConfigLong(now);
            if (freed > 8L * 1024L * 1024L) {
                NekoConfig.meeroJanitorFreed.setConfigLong(freed);
                final String size = AndroidUtilities.formatFileSize(freed);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        Toast.makeText(ApplicationLoader.applicationContext,
                                LocaleController.formatString("JanitorReport", R.string.JanitorReport, size),
                                Toast.LENGTH_LONG).show();
                    } catch (Throwable ignore) {
                    }
                });
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            running = false;
        }
    }
}
