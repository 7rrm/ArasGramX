package tw.nekomimi.nekogram;

import android.app.Activity;
import android.os.Build;
import android.os.Looper;
import android.view.Choreographer;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MeeroX v114-dbg: diagnostic-only probe for the hard freeze on the MeeroX
 * settings screen.
 *
 * The screen locks up while scrolling and no crash report survives, so this
 * probe watches the screen from a background thread. While the user scrolls,
 * it counts UI frames; if the frames stop for more than ~1.5s while scrolling
 * activity is recent, the main thread is frozen for real - and a background
 * thread can still photograph it: the probe dumps the main thread's live
 * stack, the scroll position and per-row bind timings into an internal file.
 * The settings screen offers to copy that file on its next resume, so the
 * user just pastes it back. Uncaught exceptions on this screen land in the
 * same file.
 *
 * Scope: runs only on MeeroSettingsActivity, reads nothing else, touches no
 * user content, never leaves the device, and dies with the fragment. Remove
 * this whole class once the freeze is fixed and verified.
 */
public final class MeeroFreezeProbe {

    private static final int STALL_MS = 1500;
    private static final int SCROLL_RECENT_MS = 5000;
    private static final int MAX_ROWS = 128;
    private static final int MAX_FRAMES = 70;
    private static final String EDITION = "114-dbg";

    /** Toggled by a hidden config (default on for the v114-dbg build only). */
    private static boolean enabled() {
        try {
            return NekoConfig.meeroFreezeProbe.Bool();
        } catch (Throwable t) {
            return false;
        }
    }

    // --- state, all volatile/atomic-safe because the watchdog reads them off-main ---
    private static volatile boolean attached;
    private static volatile long frameNanos;          // nanoTime of the last completed frame
    private static volatile long framesTotal;         // increments on every completed frame
    private static volatile long lastScrollNanos;
    private static volatile int lastPos = -1;
    private static volatile int lastDy;
    private static volatile boolean inStall;
    private static volatile long stallStartNanos;
    private static volatile boolean reportDialogShown;

    private static RecyclerView boundList;            // strong ref is fine: screen-scoped probe
    private static LinearLayoutManager boundLayout;
    private static Thread watchdog;

    private static final long[] rowMaxNanos = new long[MAX_ROWS];

    private static final Choreographer.FrameCallback heartbeat = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            framesTotal++;
            frameNanos = System.nanoTime();
            if (attached) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private static final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            lastScrollNanos = System.nanoTime();
            lastDy = dy;
            try {
                if (boundLayout != null) {
                    lastPos = boundLayout.findLastVisibleItemPosition();
                }
            } catch (Throwable ignored) {
            }
        }
    };

    private MeeroFreezeProbe() {
    }

    /** Called from MeeroSettingsActivity.createView on the main thread. */
    public static void attach(RecyclerView list, LinearLayoutManager layout) {
        if (!enabled() || list == null || attached) {
            return;
        }
        try {
            attached = true;
            boundList = list;
            boundLayout = layout;
            list.addOnScrollListener(scrollListener);
            frameNanos = System.nanoTime();
            Choreographer.getInstance().postFrameCallback(heartbeat);
            installCrashHook();
            startWatchdog();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Called from onFragmentDestroy. */
    public static void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        try {
            if (boundList != null) {
                boundList.removeOnScrollListener(scrollListener);
            }
        } catch (Throwable ignored) {
        }
        boundList = null;
        boundLayout = null;
        watchdog = null; // the loop exits on the next tick
        reportDialogShown = false;
    }

    // --- bind timing, wrapped around the adapter's super call ---

    public static long beginBind() {
        return System.nanoTime();
    }

    public static void endBind(int position, long t0) {
        if (!enabled() || position < 0 || position >= MAX_ROWS) {
            return;
        }
        long dt = System.nanoTime() - t0;
        if (dt > rowMaxNanos[position]) {
            rowMaxNanos[position] = dt;
        }
    }

    // --- watchdog: runs on a daemon thread, so it stays alive while the UI is dead ---

    private static void startWatchdog() {
        final Thread t = new Thread(() -> {
            long lastSeenFrames = framesTotal;
            while (attached && watchdog == Thread.currentThread()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    if (!attached) {
                        return;
                    }
                    long now = System.nanoTime();
                    long frames = framesTotal;
                    boolean framesMoving = frames != lastSeenFrames;
                    lastSeenFrames = frames;
                    long scrollAgeMs = (now - lastScrollNanos) / 1_000_000L;
                    long frameAgeMs = (now - frameNanos) / 1_000_000L;

                    if (!framesMoving && scrollAgeMs < SCROLL_RECENT_MS && frameAgeMs > STALL_MS) {
                        if (!inStall) {
                            inStall = true;
                            stallStartNanos = now - frameAgeMs * 1_000_000L;
                            captureStall(frameAgeMs, scrollAgeMs);
                        }
                    } else if (framesMoving && inStall) {
                        long recoveredMs = (now - stallStartNanos) / 1_000_000L;
                        inStall = false;
                        appendLine("recovered_after_ms=" + recoveredMs);
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "meero-freeze-watchdog");
        t.setDaemon(true);
        watchdog = t;
        t.start();
    }

    private static void captureStall(long frameAgeMs, long scrollAgeMs) {
        try {
            File f = reportFile();
            StringBuilder sb = new StringBuilder(8192);
            sb.append("=== MeeroX freeze report ===\n");
            sb.append("edition=").append(EDITION)
                    .append(" version=").append(BuildConfig.VERSION_NAME)
                    .append(" code=").append(BuildConfig.VERSION_CODE)
                    .append(" pkg=").append(ApplicationLoader.applicationContext.getPackageName()).append('\n');
            sb.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    .append(" sdk=").append(Build.VERSION.SDK_INT)
                    .append(" maxMemMB=").append(Runtime.getRuntime().maxMemory() / 1048576L).append('\n');
            sb.append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
            sb.append("frames_stalled_ms=").append(frameAgeMs)
                    .append(" last_scroll_age_ms=").append(scrollAgeMs)
                    .append(" last_row=").append(lastPos)
                    .append(" last_dy=").append(lastDy).append('\n');

            sb.append("-- bind maxima (top slow rows, ms) --\n");
            int printed = 0;
            for (int k = 0; k < 5; k++) {
                int best = -1;
                long bestV = 0;
                for (int i = 0; i < MAX_ROWS; i++) {
                    if (rowMaxNanos[i] > bestV) {
                        bestV = rowMaxNanos[i];
                        best = i;
                    }
                }
                if (best < 0 || bestV <= 0) {
                    break;
                }
                rowMaxNanos[best] = -bestV; // negative marks as consumed during this capture
                sb.append("row[").append(best).append("]=").append(bestV / 1_000_000L).append('\n');
                printed++;
            }
            for (int i = 0; i < MAX_ROWS; i++) { // restore negatives
                if (rowMaxNanos[i] < 0) {
                    rowMaxNanos[i] = -rowMaxNanos[i];
                }
            }
            if (printed == 0) {
                sb.append("(none recorded)\n");
            }

            Thread main = Looper.getMainLooper().getThread();
            sb.append("-- main thread state=").append(main.getState()).append(" --\n");
            StackTraceElement[] trace = main.getStackTrace();
            int n = Math.min(trace.length, MAX_FRAMES);
            for (int i = 0; i < n; i++) {
                sb.append("    at ").append(trace[i]).append('\n');
            }
            if (trace.length > n) {
                sb.append("    ... ").append(trace.length - n).append(" more\n");
            }

            PrintWriter pw = new PrintWriter(new FileWriter(f, false));
            pw.print(sb);
            pw.flush();
            pw.close();
            reportDialogShown = false;
            FileLog.e("MeeroFreezeProbe: stall captured, " + frameAgeMs + "ms at row " + lastPos);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void appendLine(String line) {
        try {
            FileWriter fw = new FileWriter(reportFile(), true);
            fw.write(line + "\n");
            fw.close();
        } catch (Throwable ignored) {
        }
    }

    // --- crash capture: same file, then chain to the previous handler ---

    private static boolean crashHookInstalled;

    private static synchronized void installCrashHook() {
        if (crashHookInstalled) {
            return;
        }
        crashHookInstalled = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringBuilder sb = new StringBuilder(4096);
                sb.append("\n=== MeeroX crash report ===\n");
                sb.append("edition=").append(EDITION).append(" thread=").append(thread.getName()).append('\n');
                sb.append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
                sb.append(error.getClass().getName()).append(": ").append(String.valueOf(error.getMessage())).append('\n');
                StackTraceElement[] trace = error.getStackTrace();
                int n = Math.min(trace.length, MAX_FRAMES);
                for (int i = 0; i < n; i++) {
                    sb.append("    at ").append(trace[i]).append('\n');
                }
                Throwable cause = error.getCause();
                int depth = 0;
                while (cause != null && depth++ < 3) {
                    sb.append("caused by ").append(cause.getClass().getName())
                            .append(": ").append(String.valueOf(cause.getMessage())).append('\n');
                    cause = cause.getCause();
                }
                FileWriter fw = new FileWriter(reportFile(), true);
                fw.write(sb.toString());
                fw.close();
                reportDialogShown = false;
            } catch (Throwable ignored) {
            }
            if (prev != null) {
                prev.uncaughtException(thread, error);
            }
        });
    }

    // --- delivery: the settings screen offers the report as a clipboard copy ---

    private static File reportFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "meero_freeze_report.txt");
    }

    /** Called from MeeroSettingsActivity.onResume. Shows at most once per new report. */
    public static void maybeShowReportDialog(BaseFragment fragment) {
        if (!enabled() || fragment == null || reportDialogShown) {
            return;
        }
        try {
            File f = reportFile();
            if (!f.exists() || f.length() < 40) {
                return;
            }
            Activity activity = fragment.getParentActivity();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            final StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            int total = 0;
            while ((line = br.readLine()) != null && total < 24000) {
                sb.append(line).append('\n');
                total += line.length();
            }
            br.close();
            final String report = sb.toString();
            if (report.length() < 40) {
                return;
            }
            reportDialogShown = true;
            AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle(fragment.getString(org.telegram.messenger.R.string.MeeroFreezeReportTitle));
            b.setMessage(fragment.getString(org.telegram.messenger.R.string.MeeroFreezeReportBody));
            b.setPositiveButton(fragment.getString(org.telegram.messenger.R.string.MeeroFreezeReportCopy), (d, w) -> {
                try {
                    AndroidUtilities.addToClipboard(report);
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    Toast.makeText(activity, fragment.getString(org.telegram.messenger.R.string.MeeroFreezeReportCopied), Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
            b.setNegativeButton(fragment.getString(org.telegram.messenger.R.string.MeeroFreezeReportLater), null);
            b.show();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
