package tw.nekomimi.nekogram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * MeeroX v195 DIAGNOSTIC (temporary, owner-ordered): the "join watch".
 *
 * Why it exists: the owner proved by A/B test (same account, official
 * Telegram joins fine, MeeroX shows the "too many communities" limit
 * surface) that the fault is ours - YET static review shows the whole join
 * path (addUserToChat request build, error mapping, the sheet/page call
 * sites) byte-identical to stock Telegram. So the trigger is runtime data:
 * WHICH request failed with WHICH exact server text. This recorder answers
 * precisely that in one user repro.
 *
 * How: every error through AlertsCreator.processError() is appended to a
 * small in-memory ring; the moment the TYPE_TO0_MANY_COMMUNITIES sheet or
 * the TooManyCommunitiesActivity page is constructed, a compact technical
 * summary is auto-copied to the clipboard and a toast says so (vaulted
 * string id 466). If the surface fires with an EMPTY ring, that alone is
 * the smoking gun: a caller that bypasses processError entirely.
 *
 * Zero visible UI change besides the toast on that one surface. Nothing is
 * written to disk; the ring holds at most 20 short lines and dies with the
 * process. Removal once the fix lands: delete this file + 3 hook lines.
 */
public final class MeeroJoinWatch {

    private MeeroJoinWatch() {
    }

    private static final int RING = 20;
    private static final Deque<String> ring = new ArrayDeque<>();

    public static void record(String via, TLObject req, TLRPC.TL_error error) {
        try {
            if (error == null) {
                return;
            }
            String rq = req == null ? "?" : req.getClass().getSimpleName();
            String line = rq + " | code=" + error.code + " | "
                    + String.valueOf(error.text) + " | via=" + via;
            synchronized (ring) {
                ring.addLast(line);
                while (ring.size() > RING) {
                    ring.removeFirst();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static void onTooManyCommunitiesShown(Context ctx) {
        if (ctx == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MXW195 diag report:");
        synchronized (ring) {
            if (ring.isEmpty()) {
                sb.append(" NO processError RECORDED (direct caller!)");
            } else {
                for (String s : ring) {
                    sb.append('\n').append(s);
                }
            }
        }
        try {
            ClipboardManager cm =
                    (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("mxw195", sb.toString()));
            }
        } catch (Throwable ignore) {
        }
        try {
            Toast.makeText(ctx, MeeroStrings.s(466), Toast.LENGTH_LONG).show();
        } catch (Throwable ignore) {
        }
    }
}
