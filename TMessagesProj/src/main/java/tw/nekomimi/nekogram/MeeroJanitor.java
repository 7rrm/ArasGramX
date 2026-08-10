package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

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

    /* v189 (batch 3C): the policy numbers (size ladder, age ladder, default
     * picks, start delay, report threshold, day/week math) come from the
     * sealed motion table (dom 'C'); the literal block is the byte-identical
     * no-lib fallback. pol() loads once, jf() picks native-when-ready. */
    private static volatile float[] pol;

    private static float[] pol() {
        float[] p = pol;
        if (p == null && MeeroCore.motionCore()) {
            p = MeeroCore.nJanitorPolicy();
            if (p != null && p.length == 14) pol = p; else p = null;
        }
        return p;
    }

    private static float jf(int i, float legacy) {
        float[] p = pol();
        return p != null ? p[i] : legacy;
    }

    private static long dayMs() {
        return (long) jf(12, 86_400_000f);
    }

    /** SelectBox choices: must match the settings rows exactly. */
    private static long[] limits() {
        float[] p = pol();
        return p != null
                ? new long[]{(long) p[0], (long) p[1], (long) p[2], (long) p[3], (long) p[4]}
                : new long[]{1, 2, 4, 8, 16};
    }

    private static long[] ages() {
        float[] p = pol();
        return p != null
                ? new long[]{(long) p[5], (long) p[6], (long) p[7]}
                : new long[]{7, 14, 30};
    }

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
        Utilities.cacheClearQueue.postRunnable(MeeroJanitor::maybeRun, (long) jf(10, 15_000f));
    }

    public static long limitBytes() {
        final long[] lim = limits();
        int idx = NekoConfig.meeroJanitorLimit.Int();
        if (idx < 0 || idx >= lim.length) {
            idx = (int) jf(8, 3f);
        }
        return lim[idx] * 1024L * 1024L * 1024L;
    }

    public static long ageMillis() {
        final long[] age = ages();
        int idx = NekoConfig.meeroJanitorAge.Int();
        if (idx < 0 || idx >= age.length) {
            idx = (int) jf(9, 1f);
        }
        return age[idx] * dayMs();
    }

    /** Settings labels for the limit SelectBox. */
    public static String[] limitTitles() {
        final long[] lim = limits();
        final String[] out = new String[lim.length];
        for (int i = 0; i < lim.length; i++) {
            out[i] = lim[i] + " GB";
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
        final long interval = (mode == 0 ? dayMs() : (long) jf(13, 7f) * dayMs());
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
            if (freed > (long) jf(11, 8_388_608f)) {
                NekoConfig.meeroJanitorFreed.setConfigLong(freed);
                final String size = AndroidUtilities.formatFileSize(freed);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        Toast.makeText(ApplicationLoader.applicationContext,
                                MeeroStrings.f(7, size),
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
