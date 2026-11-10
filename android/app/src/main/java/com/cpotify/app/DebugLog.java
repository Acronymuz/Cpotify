package com.cpotify.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Ring buffer kecil untuk debug tanpa adb. Tampilkan via tombol 🐞 di app. */
public class DebugLog {
    private static final int MAX = 80;
    private static final String[] buf = new String[MAX];
    private static int head = 0;
    private static int count = 0;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static synchronized void d(String tag, String msg) {
        android.util.Log.d(tag, msg);
        buf[head] = FMT.format(new Date()) + " " + tag + ": " + msg;
        head = (head + 1) % MAX;
        if (count < MAX) {
            count++;
        }
    }

    public static synchronized void d(String tag, String msg, Throwable t) {
        d(tag, msg + " | " + t);
    }

    public static synchronized String dump() {
        StringBuilder b = new StringBuilder();
        if (count == 0) {
            return "(kosong)";
        }
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + MAX * 2) % MAX;
            b.append(buf[idx]).append('\n');
        }
        return b.toString();
    }

    public static synchronized void clear() {
        head = 0;
        count = 0;
    }
}
