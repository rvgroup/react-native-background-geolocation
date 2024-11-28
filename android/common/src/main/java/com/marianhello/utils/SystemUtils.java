package com.marianhello.utils;

import android.os.Build;
import android.app.PendingIntent;

public class SystemUtils {
    public static int getIntentFlag(int intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return intent | PendingIntent.FLAG_IMMUTABLE;
        } else {
            return intent;
        }
    }

    public static int getMuttableIntentFlag(int intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return intent | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return intent | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        } else {
            return intent;
        }
    }
}