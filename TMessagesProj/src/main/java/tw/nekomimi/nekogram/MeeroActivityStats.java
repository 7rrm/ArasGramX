package tw.nekomimi.nekogram;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * MeeroX v102: activity details engine.
 *
 * Computes personal activity stats from the LOCALLY stored message database
 * (messages_v2): per-day counts, top private chats by exchanged messages and
 * an outgoing-messages hour-of-day histogram. Everything stays on device -
 * nothing is ever sent anywhere. Also keeps a simple app-open counter which
 * starts at v102 install time (Telegram stores no historical open times -
 * documented to the user on the screen).
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
        storage.getStorageQueue().postRunnable(() -> {
            Summary s = new Summary();
            try {
                SQLiteDatabase db = storage.getDatabase();
                if (db != null) {
                    long nowSec = System.currentTimeMillis() / 1000L;
                    s.total = countSince(db, 0);
                    s.today = countSince(db, localMidnightSec());
                    s.week = countSince(db, nowSec - 7L * 86400L);
                    s.month = countSince(db, nowSec - 30L * 86400L);
                    fillTop(db, s);
                    fillHourly(db, s);
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

    /** Hour-of-day histogram of OUTGOING messages in device-local time. */
    private static void fillHourly(SQLiteDatabase db, Summary s) {
        SQLiteCursor c = null;
        try {
            c = db.queryFinalized("SELECT strftime('%H', datetime(date, 'unixepoch', 'localtime')) AS h, COUNT(*) FROM messages_v2 WHERE out = 1 GROUP BY h");
            while (c.next()) {
                try {
                    int h = Integer.parseInt(c.stringValue(0));
                    if (h >= 0 && h < 24) {
                        s.hourly[h] = c.intValue(1);
                        if (c.intValue(1) > 0) s.hasHourly = true;
                    }
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {} finally {
            if (c != null) c.dispose();
        }
    }
}
