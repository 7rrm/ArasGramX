package tw.nekomimi.nekogram;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bornAt = System.currentTimeMillis();
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
                final String p = readSmall(new File(dir, ".prep"));
                if (p.length() > 0) {
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
                }
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 150);
        }
    };

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
            final byte[] b = new byte[8];
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
