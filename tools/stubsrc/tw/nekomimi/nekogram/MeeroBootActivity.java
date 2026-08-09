package tw.nekomimi.nekogram;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;

/**
 * MeeroX v171 - first-boot preparation splash ("شاشة تحضير").
 *
 * Lives in its own process (:meeroboot) and is launched by MeeroDexApp
 * ONLY while the encrypted dex vault is being prepared after an
 * install/update. It is deliberately framework-only: the vault is NOT
 * loaded in this process, so this class must never touch app classes.
 *
 * Communication is one-way plain-text markers the main process drops
 * into files/vaultdex (same uid, readable across both processes):
 *   ".prep" -> '0'..'100' decrypt percentage, ".done" -> boot complete.
 *
 * The splash shows the real decrypt progress, explains that this is a
 * once-per-update waiting, and releases itself the moment the main
 * process finishes (or after a long safety deadline). User-facing
 * copies exist in Arabic and English only - chosen by device locale.
 */
public class MeeroBootActivity extends Activity {

    private static final int ROSE = 0xFFFF4E8A;
    private static final int GREY = 0xFF9A9AA2;
    private static final long DEADLINE_MS = 180 * 1000; // never trap the user

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar bar;
    private TextView pctView;
    private TextView subView;
    private boolean ar;
    private long bornAt;
    // v173: never look frozen again. If the exact decrypt length is not
    // readable on this ROM (openFd quirk -> main process writes no .prep
    // values), the bar switches to a pulsing indeterminate mode instead
    // of sitting at a dead 0%. The moment real values arrive we go
    // determinate and show them, exactly like v171/v172.
    private boolean sawData;
    private boolean indet;
    // v178 wedge watchdog state
    private String lastCombo = "";
    private long lastChangeAt;
    private int phasePid = -1;
    private int phaseShown = -1;
    private boolean healed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bornAt = System.currentTimeMillis();
        lastChangeAt = bornAt;
        try {
            final java.util.Locale l = getResources().getConfiguration().locale;
            ar = l != null && "ar".equals(l.getLanguage());
        } catch (Throwable t) {
            ar = false;
        }

        final float dp = getResources().getDisplayMetrics().density;
        final Window w = getWindow();
        if (w != null) {
            w.setStatusBarColor(Color.BLACK);
            w.setNavigationBarColor(Color.BLACK);
        }

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        final int pad = (int) (40 * dp);
        root.setPadding(pad, pad, pad, pad);

        final TextView title = new TextView(this);
        title.setText("MeeroX");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final View gap1 = new View(this);
        root.addView(gap1, new LinearLayout.LayoutParams(1, (int) (30 * dp)));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        try {
            bar.setProgressTintList(ColorStateList.valueOf(ROSE));
            bar.setIndeterminateTintList(ColorStateList.valueOf(ROSE));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A2A2E));
        } catch (Throwable ignored) {
        }
        final LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (6 * dp));
        root.addView(bar, barLp);

        final View gap2 = new View(this);
        root.addView(gap2, new LinearLayout.LayoutParams(1, (int) (14 * dp)));

        pctView = new TextView(this);
        pctView.setText(ar ? "…0٪" : "0%…");
        pctView.setTextColor(ROSE);
        pctView.setTextSize(15);
        pctView.setTypeface(Typeface.DEFAULT_BOLD);
        pctView.setGravity(android.view.Gravity.CENTER);
        root.addView(pctView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final View gap3 = new View(this);
        root.addView(gap3, new LinearLayout.LayoutParams(1, (int) (26 * dp)));

        subView = new TextView(this);
        subView.setText(ar
                ? "جارِ تجهيز ميروX لأول مرة…\nيحدث هذا مرة واحدة فقط بعد كل تحديث"
                : "Preparing MeeroX for the first time…\nThis happens only once after each update");
        subView.setTextColor(GREY);
        subView.setTextSize(14);
        subView.setGravity(android.view.Gravity.CENTER);
        subView.setLineSpacing(0, 1.3f);
        if (ar) {
            try {
                subView.setTextDirection(View.TEXT_DIRECTION_RTL);
            } catch (Throwable ignored) {
            }
        }
        root.addView(subView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        handler.post(tick);
    }

    /**
     * v178 self-healing boot (his architecture ask - "works on every
     * user's device, not just mine"): any content change in .prep or
     * .phase is a heartbeat from the booting main process. If the beats
     * go silent for 35 s and .done never arrived, the boot is wedged -
     * so we do exactly what he proved manually on his own device: kill
     * the wedged sibling (same uid, pid learned from .phase) and
     * relaunch the launcher intent. The second pass reads the already
     * cached vault, boots fast and warm, and walks in clean. Device-
     * independent by construction: no need to know WHY it wedged.
     */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                final File dir = new File(getFilesDir(), "vaultdex");
                if (new File(dir, ".done").exists()
                        || System.currentTimeMillis() - bornAt > DEADLINE_MS) {
                    finishNoAnim();
                    return;
                }
                final long now = System.currentTimeMillis();
                final String ph = readSmall(new File(dir, ".phase"));
                final String p = readSmall(new File(dir, ".prep"));
                final String combo = p + "|" + ph;
                if (!combo.equals(lastCombo)) {
                    lastCombo = combo;
                    lastChangeAt = now;
                    parsePhase(ph);
                }
                if (!healed && now - bornAt > 25000 && now - lastChangeAt > 35000) {
                    selfHeal(dir);
                    return;
                }
                if (p.length() > 0) {
                    sawData = true;
                    if (indet) {
                        bar.setIndeterminate(false);
                        indet = false;
                    }
                    int v;
                    try {
                        v = Integer.parseInt(p.trim());
                    } catch (Throwable t) {
                        v = 0;
                    }
                    v = Math.max(0, Math.min(100, v));
                    bar.setProgress(v);
                    pctView.setText(ar ? v + "٪" : v + "%");
                    if (v >= 100) {
                        // decrypt done; ART + first-run init still working
                        subView.setText(ar ? "تشغيل الواجهة…" : "Starting the interface…");
                    }
                } else if (!sawData && !indet && now - bornAt > 800) {
                    // v173: no length info on this ROM -> live pulse instead
                    // of a frozen-looking 0% (his on-device report, owned).
                    indet = true;
                    bar.setIndeterminate(true);
                    pctView.setText("…");
                }
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 150);
        }
    };

    private void parsePhase(String ph) {
        try {
            final int sc = ph.indexOf(';');
            if (sc <= 0) {
                return;
            }
            final int code = Integer.parseInt(ph.substring(0, sc).trim());
            try {
                phasePid = Integer.parseInt(ph.substring(sc + 1).trim());
            } catch (Throwable ignored) {
            }
            if (code == phaseShown) {
                return;
            }
            phaseShown = code;
            if (code >= 40 && code < 60) {
                // doubles as on-device forensics: a future screenshot
                // tells us exactly which stage a wedge sits in
                subView.setText(ar ? "نتحقق من سلامة الملفات…" : "Verifying file integrity…");
            } else if (code >= 60) {
                subView.setText(ar ? "تهيئة الواجهة…" : "Preparing the interface…");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * v179: ONE self-heal per install, never a loop. (Owned: v178's
     * identical-text heartbeats blinded this very watchdog into killing
     * HEALTHY slow boots, and every kill restarted an uncached boot -
     * the flicker loop he filmed.) If the marker says this install was
     * already healed once, the cover simply steps aside instead of
     * healing again: the user lands on his proven manual escape route
     * (the system screen) rather than being trapped on black forever.
     */
    private void selfHeal(File dir) {
        // cap is keyed to THIS install's stamp, so every update earns
        // exactly one fresh heal chance
        String stampStr = "";
        try {
            stampStr = String.valueOf(getPackageManager()
                    .getPackageInfo(getPackageName(), 0).lastUpdateTime);
        } catch (Throwable ignored) {
        }
        String heal = "";
        try {
            heal = readSmall(new File(dir, ".heal"));
        } catch (Throwable ignored) {
        }
        if (heal.length() > 0 && heal.equals(stampStr)) {
            // already healed once for this install -> step aside into
            // his proven manual route instead of looping on black
            healed = true;
            finishNoAnim();
            return;
        }
        healed = true;
        writeSmall(new File(dir, ".heal"), stampStr);
        try {
            subView.setText(ar
                    ? "معالجة ذاتية لعائق بسيط… ثوانٍ وتدخل تلقائياً"
                    : "Self-healing a small hiccup… entering automatically");
        } catch (Throwable ignored) {
        }
        try {
            if (phasePid > 0 && phasePid != Process.myPid()) {
                Process.killProcess(phasePid); // same-uid sibling only
            }
        } catch (Throwable ignored) {
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    final Intent li = getPackageManager()
                            .getLaunchIntentForPackage(getPackageName());
                    if (li != null) {
                        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(li);
                    }
                } catch (Throwable ignored) {
                }
                finishNoAnim(); // the cached vault makes the relaunch fast
            }
        }, 1500);
    }

    private static void writeSmall(File f, String s) {
        java.io.FileOutputStream fos = null;
        try {
            fos = new java.io.FileOutputStream(f);
            fos.write(s.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Swallowed on purpose: a few seconds of one-time preparation,
        // canceling out of it would only show a black gap while the main
        // process keeps working anyway.
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    private void finishNoAnim() {
        try {
            finish();
            overridePendingTransition(0, 0);
        } catch (Throwable ignored) {
        }
    }

    private static String readSmall(File f) {
        FileInputStream in = null;
        try {
            if (!f.exists()) {
                return "";
            }
            in = new FileInputStream(f);
            final byte[] b = new byte[32]; // v178: ".phase" carries "code;pid"
            final int n = in.read(b);
            return n <= 0 ? "" : new String(b, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
