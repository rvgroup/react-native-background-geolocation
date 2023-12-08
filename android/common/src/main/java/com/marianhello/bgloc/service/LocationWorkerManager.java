package com.marianhello.bgloc.service;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.marianhello.utils.WorkHelper;

import java.util.concurrent.TimeUnit;

public class LocationWorkerManager {
    private static final int LOCATION_INTERVAL_MIN = 1;

    public static void startWorker(Context context) {
        WorkManager workManager = WorkHelper.getWorkManager(context);

        /*
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
         */

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LocationWorker.class)
                .setInitialDelay(LOCATION_INTERVAL_MIN, TimeUnit.MINUTES)
                .addTag(LocationWorker.TAG)
                //.setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        workManager.enqueueUniqueWork(
                LocationWorker.TAG,
                ExistingWorkPolicy.REPLACE,
                request);
    }

    public static void stopWorker(Context context) {
        WorkHelper.cancelAllWorkByTag(context, LocationWorker.TAG);
    }
}