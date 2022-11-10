package com.marianhello.utils;

import android.os.Build;
import android.app.PendingIntent;

public class SystemUtils {
    public static int getIntentFlag(int intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return intent | PendingIntent.FLAG_IMMUTABLE;
        } else {
            return intent;
        }
    }
}
