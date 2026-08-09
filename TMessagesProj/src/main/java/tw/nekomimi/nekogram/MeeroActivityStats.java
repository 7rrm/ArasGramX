package tw.nekomimi.nekogram;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

/**
 * MeeroX v102: activity details engine.
 *
 * Computes personal activity stats from the LOCALLY stored message database
 * (messages_v2): per-day counts, top private chats by exchanged messages and
 * an outgoing-messages hour-of-day histogram. Everything stays on device -
 * nothing is ever sent anywhere. Also keeps a simple app-open counter which
 * starts at v102 install time (Telegram stores no historical open times -
 * documented to the user on the screen).
 *
 * v185 (batch 2C): the SQL stays (touching Telegram's live database handle
 * from native would break the never-risk-stock law), but the DECISION layer
 * moved into libmeerocore: the local-day/week/month bounds, the hourly-bucket
 * assembly with its validity gate, and the dry-chat policy (dedup-first-win,
 * not-outgoing filter, freshest-first top 5). Raw rows go in, decisions come
 * out. Legacy in-Java logic kept as the degraded fallback.
 */
public final class MeeroActivityStats {

    private MeeroActivityStats() {}

    public static final class TopChat {
        public long dialogId;
        public int count;
    }

    public static final class Summary {
        public boolean ready;
        public int today;
        public int week;
        public int month;
        public int total;
        public final ArrayList<TopChat> top = new ArrayList<>();
        public final int[] hourly = new int[24];
        public boolean hasHourly;
        // v104: quiet chats - private dialogs whose LAST message is incoming
        public int dryCount;
        public final ArrayList<DryChat> dryTop = new ArrayList<>(); // max 5, freshest wait first
    }

    public static final class DryChat {
        public long dialogId;
        public long lastIncomingSec;
    }

    /** Called from LaunchActivity once per cold open. */
    public static void onAppOpened() {
        if (!NekoConfig.meeroActivityStats.Bool()) return;
        NekoConfig.meeroStatsOpens.setConfigInt(NekoConfig.meeroStatsOpens.Int() + 1);
        if (NekoConfig.meeroStatsSince.Int() == 0) {
            NekoConfig.meeroStatsSince.setConfigInt((int) (System.currentTimeMillis() / 1000L));
        }
    }

    /** Computes on the storage queue thread, answers on the UI thread. */
    public static void compute(int account, Utilities.Callback<Summary> callback) {
        MessagesStorage storage = MessagesStorage.getInstance(account);
        final boolean nativeCore = MeeroCore.ready();
        if (nativeCore) MeeroCore.nAsReset();
        storage.getStorageQueue().postRunnable(() -> {
            Summary s = new Summary();
            try {
                SQLiteDatabase db = storage.getDatabase();
                if (db != null) {
                    long nowSec = System.currentTimeMillis() / 1000L;
                    // v185 (batch 2C): day/week/month bounds from libmeerocore
                    // (device-local midnight, same as the old Calendar code)
                    long midnight = localMidnightSec(), weekAgo = nowSec - 7L * 86400L, monthAgo = nowSec - 30L * 86400L;
                    if (nativeCore) {
                        String b = MeeroCore.nAsBounds(System.currentTimeMillis());
                        if (b != null) {
                            String[] f = b.split("\t", -1);
                            try {
                                midnight = Long.parseLong(f[0]);
                                weekAgo = Long.parseLong(f[1]);
                                monthAgo = Long.parseLong(f[2]);
                            } catch (Throwable ignore) {}
                        }
                    }
                    s.total = countSince(db, 0);
                    s.today = countSince(db, midnight);
                    s.week = countSince(db, weekAgo);
                    s.month = countSince(db, monthAgo);
                    fillTop(db, s);
                    fillHourly(db, s, nativeCore);
                    fillDry(db, s, org.telegram.messenger.UserConfig.getInstance(account).getClientUserId(), nativeCore);
                    s.ready = true;
                }
            } catch (Throwable ignore) {}
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.run(s);
            });
        });
    }

    private static long localMidnightSec() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis() / 1000L;
    }

    private static int countSince(SQLiteDatabase db, long sinceSec) {
        SQLiteCursor c = null;
        try {
            c = db.queryFinalized("SELECT COUNT(*) FROM messages_v2 WHERE date >= " + sinceSec);
            return c.next() ? c.intValue(0) : 0;
        } catch (Throwable ignore) {
            return 0;
        } finally {
            if (c != null) c.dispose();
        }
    }

    /** Top 10 private dialogs (uid > 0) by stored message count. */
    private static void fillTop(SQLiteDatabase db, Summary s) {
        SQLiteCursor c = null;
        try {
            c = db.queryFinalized("SELECT uid, COUNT(*) AS c FROM messages_v2 WHERE uid > 0 GROUP BY uid ORDER BY c DESC LIMIT 10");
            while (c.next()) {
                TopChat tc = new TopChat();
                tc.dialogId = c.longValue(0);
                tc.count = c.intValue(1);
                s.top.add(tc);
            }
        } catch (Throwable ignore) {} finally {
            if (c != null) c.dispose();
        }
    }

    /** Hour-of-day histogram of OUTGOING messages in device-local time.
     *  v185: bucket assembly + validity gate run natively when the lib is up. */
    private static void fillHourly(SQLiteDatabase db, Summary s, boolean nativeCore) {
        SQLiteCursor c = null;
        try {
            c = db.queryFinalized("SELECT strftime('%H', datetime(date, 'unixepoch', 'localtime')) AS h, COUNT(*) FROM messages_v2 WHERE out = 1 GROUP BY h");
            while (c.next()) {
                try {
                    int h = Integer.parseInt(c.stringValue(0));
                    if (nativeCore) {
                        MeeroCore.nAsSetHour(h, c.intValue(1));
                    } else if (h >= 0 && h < 24) {
                        s.hourly[h] = c.intValue(1);
                        if (c.intValue(1) > 0) s.hasHourly = true;
                    }
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {} finally {
            if (c != null) c.dispose();
        }
        if (nativeCore) {
            for (int h = 0; h < 24; h++) s.hourly[h] = MeeroCore.nAsHourAt(h);
            s.hasHourly = MeeroCore.nAsHasHourly();
        }
    }

    /** v104: quiet ("dry") chats - private dialogs whose LAST stored message
     *  came from the other side, i.e. you still owe them a reply. Saved
     *  Messages, the service account, groups and channels never count.
     *  v185: rows stream into libmeerocore; the dedup-first-win policy,
     *  not-yours filter and freshest-first top 5 are all native. */
    private static void fillDry(SQLiteDatabase db, Summary s, long selfId, boolean nativeCore) {
        SQLiteCursor c = null;
        try {
            c = db.queryFinalized(
                    "SELECT m.uid, m.date, m.out FROM messages_v2 m INNER JOIN " +
                    "(SELECT uid AS u, MAX(date) AS md FROM messages_v2 WHERE uid > 0 AND uid <> 777000 AND uid <> " + selfId + " GROUP BY uid) t " +
                    "ON m.uid = t.u AND m.date = t.md");
            if (nativeCore) {
                while (c.next()) {
                    MeeroCore.nAsDryFeed(c.longValue(0), c.longValue(1), c.intValue(2));
                }
            } else {
                HashSet<Long> seen = new HashSet<>();
                ArrayList<DryChat> all = new ArrayList<>();
                while (c.next()) {
                    long uid = c.longValue(0);
                    if (!seen.add(uid)) continue;
                    if (c.intValue(2) != 0) continue; // last message here was yours
                    DryChat d = new DryChat();
                    d.dialogId = uid;
                    d.lastIncomingSec = c.longValue(1);
                    all.add(d);
                }
                s.dryCount = all.size();
                all.sort((a, b) -> Long.compare(b.lastIncomingSec, a.lastIncomingSec));
                for (int i = 0; i < all.size() && i < 5; i++) s.dryTop.add(all.get(i));
            }
        } catch (Throwable ignore) {} finally {
            if (c != null) c.dispose();
        }
        if (nativeCore) {
            s.dryCount = MeeroCore.nAsDryCount();
            for (int i = 0; i < 5; i++) {
                long id = MeeroCore.nAsDryTopIdAt(i);
                if (id == 0) break;
                DryChat d = new DryChat();
                d.dialogId = id;
                d.lastIncomingSec = MeeroCore.nAsDryTopSecAt(i);
                s.dryTop.add(d);
            }
        }
    }
}
