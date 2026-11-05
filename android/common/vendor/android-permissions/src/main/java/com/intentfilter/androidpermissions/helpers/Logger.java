package com.intentfilter.androidpermissions.helpers;

import android.util.Log;

public class Logger {
    private String LOG_TAG;

    private Logger(Class clazz) {
        this.LOG_TAG = clazz.getSimpleName();
    }

    public static Logger loggerFor(Class clazz) {
        return new Logger(clazz);
    }

    public void i(String message) {
        // The original published 0.1.7 AAR has BuildConfig.DEBUG = false.
    }

    public void e(String message) {
        Log.e(LOG_TAG, message);
    }
}
