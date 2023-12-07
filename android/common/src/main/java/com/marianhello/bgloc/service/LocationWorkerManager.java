package com.marianhello.bgloc.service;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
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

        //Data.Builder data = new Data.Builder();
        //data.putString(syncModeKey, syncMode.toString());

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(LocationWorker.class, LOCATION_INTERVAL_MIN, TimeUnit.MINUTES)
                //.setInputData(data.build())
                .addTag(LocationWorker.TAG)
                //.setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        workManager.enqueueUniquePeriodicWork(
                LocationWorker.TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                request);
    }

    public static void stopWorker(Context context) {
        WorkHelper.cancelAllWorkByTag(context, LocationWorker.TAG);
    }
}