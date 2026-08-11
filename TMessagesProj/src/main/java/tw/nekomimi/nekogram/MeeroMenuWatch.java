package tw.nekomimi.nekogram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

/**
 * MeeroX v201 DIAGNOSTIC (temporary, owner-ordered, v195 school).
 *
 * Field evidence at v200: the chat header menu's MAIN card still eats taps
 * (row tapped -> popup closes -> nothing runs) while the destructive split
 * card below the 8dp gap works. So the touch is lost between dispatch and
 * the row listeners, and only raw facts from the failing device point at
 * the exact consumer.
 *
 * Mechanism: every DOWN reaching a skinned popup layout is recorded in a
 * tiny in-memory ring (coordinates, the layout's measured box, and whether
 * ANY child consumed it); every row click that actually fires is recorded
 * too. The MOMENT a skinned DOWN is consumed by NO child, that is the
 * owner's dead-tap by definition: the ring is auto-copied to the clipboard
 * and the vaulted toast s(466) (v195's "paste it to the developer" row,
 * reactivated - vault ids never renumber, wording fits) asks for the paste.
 * A 1.5s debounce keeps repeat taps from spamming. Tapping a working row
 * (CLICK line present) right before a dead one gives the smoking-gun
 * asymmetry in a single paste.
 *
 * Nothing touches disk; the ring dies with the process. Removal when the
 * consumer is identified: the dispatchTouchEvent override in
 * ActionBarPopupWindow.ActionBarPopupWindowLayout, the onClickFired() lines
 * in ActionBarMenuItem's guarded listeners, and this file. Row 466 remains
 * the reserved generic "report copied" toast row.
 */
public final class MeeroMenuWatch {

    private MeeroMenuWatch() {
    }

    private static final int CAP = 24;
    private static final String[] ring = new String[CAP];
    private static int wrote;
    private static long lastCaptureMs;

    private static void rec(String line) {
        try {
            synchronized (ring) {
                ring[wrote % CAP] = line;
                wrote++;
            }
        } catch (Throwable ignore) {
        }
    }

    /** A row's click listener actually ran (called from ActionBarMenuItem's guarded dispatch sites). */
    public static void onClickFired(Object tag) {
        rec("CLICK id=" + tag);
    }

    /**
     * A DOWN reached the skinned popup layout. When NO child consumed it the
     * tap is dead with certainty: capture the ring to the clipboard and ask
     * for the paste. Never throws.
     */
    public static void onDown(Context ctx, float x, float y, int boxW, int boxH, boolean consumed) {
        rec("DOWN x=" + (int) x + " y=" + (int) y + " box=" + boxW + "x" + boxH + " consumed=" + consumed);
        if (consumed || ctx == null) {
            return;
        }
        try {
            final long now = System.currentTimeMillis();
            if (now - lastCaptureMs < 1500) {
                return;
            }
            lastCaptureMs = now;
            String report;
            synchronized (ring) {
                StringBuilder sb = new StringBuilder("MXW201 diag report:\n\n");
                final int n = Math.min(wrote, CAP);
                final int start = wrote > CAP ? wrote % CAP : 0;
                for (int i = 0; i < n; i++) {
                    sb.append(ring[(start + i) % CAP]).append('\n');
                }
                report = sb.toString();
            }
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("MXW201", report));
            }
            Toast.makeText(ctx.getApplicationContext(), MeeroStrings.s(466), Toast.LENGTH_LONG).show();
        } catch (Throwable ignore) {
        }
    }
}
