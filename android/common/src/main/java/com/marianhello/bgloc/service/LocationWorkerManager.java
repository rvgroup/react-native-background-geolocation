package com.marianhello.bgloc.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LocationWorkerManager {
    static final String GENERATION = "generation";
    private static final String PREFERENCES = "bgloc-worker";

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    // A persisted token survives process recreation but is invalidated by stop/start.
    public static synchronized void beginTracking(Context context) {
        preferences(context).edit().putString(GENERATION, UUID.randomUUID().toString()).commit();
        WorkManager.getInstance(context).cancelUniqueWork(LocationWorker.TAG);
    }

    public static synchronized void ensureTracking(Context context) {
        if (preferences(context).getString(GENERATION, null) == null) beginTracking(context);
    }

    public static synchronized boolean isCurrent(Context context, String generation) {
        return generation != null && generation.equals(preferences(context).getString(GENERATION, null));
    }

    public static synchronized void startWorker(Context context) {
        String generation = preferences(context).getString(GENERATION, null);
        if (generation != null) enqueue(context, generation, ExistingWorkPolicy.KEEP);
    }

    static synchronized boolean complete(Context context, String generation, Runnable delivery, boolean scheduleNext) {
        if (!isCurrent(context, generation)) return false;
        delivery.run();
        // Append rather than REPLACE: replacing a currently running worker cancels it.
        if (scheduleNext) enqueue(context, generation, ExistingWorkPolicy.APPEND_OR_REPLACE);
        return true;
    }

    private static void enqueue(Context context, String generation, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LocationWorker.class)
                .setInputData(new Data.Builder().putString(GENERATION, generation).build())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag(LocationWorker.TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(LocationWorker.TAG, policy, request);
    }

    public static synchronized void stopWorker(Context context) {
        preferences(context).edit().remove(GENERATION).commit();
        WorkManager.getInstance(context).cancelAllWorkByTag(LocationWorker.TAG);
    }
}
