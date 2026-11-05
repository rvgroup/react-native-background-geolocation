package com.marianhello.bgloc.service;

import android.content.Context;
import android.location.LocationManager;

import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLocationManager;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 30})
public class LocationWorkerTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(Runnable::run).build());
        LocationWorkerManager.stopWorker(context);
    }

    private String begin() {
        LocationWorkerManager.beginTracking(context);
        return context.getSharedPreferences("bgloc-worker", Context.MODE_PRIVATE).getString("generation", null);
    }

    private LocationWorker worker(String generation) {
        return TestListenableWorkerBuilder.from(context, LocationWorker.class)
                .setInputData(new Data.Builder().putString(LocationWorkerManager.GENERATION, generation).build())
                .build();
    }

    @Test public void staleWorkCompletesWithoutRequestingLocation() throws Exception {
        String generation = begin();
        LocationWorkerManager.stopWorker(context);
        assertEquals(ListenableWorker.Result.success(), worker(generation).startWork().get(5, TimeUnit.SECONDS));
    }

    @Test public void stopThenStartRejectsOldCallback() {
        String oldGeneration = begin();
        LocationWorkerManager.stopWorker(context);
        String currentGeneration = begin();
        Runnable delivery = mock(Runnable.class);
        assertFalse(LocationWorkerManager.complete(context, oldGeneration, delivery, true));
        verifyNoInteractions(delivery);
        assertTrue(LocationWorkerManager.isCurrent(context, currentGeneration));
        assertTrue(LocationWorkerManager.complete(context, currentGeneration, delivery, false));
        verify(delivery).run();
    }

    @Test public void disabledProvidersCompleteWithRetry() throws Exception {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        ShadowLocationManager shadow = Shadow.extract(manager);
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        assertEquals(ListenableWorker.Result.retry(), worker(begin()).startWork().get(5, TimeUnit.SECONDS));
    }

    @Test public void enabledProviderTimesOutOnOldAndNewAndroid() throws Exception {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        ShadowLocationManager shadow = Shadow.extract(manager);
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        LocationWorker worker = worker(begin());
        com.google.common.util.concurrent.ListenableFuture<ListenableWorker.Result> future = worker.startWork();
        ((ShadowLooper) Shadow.extract(android.os.Looper.getMainLooper())).idleFor(31, TimeUnit.SECONDS);
        assertEquals(ListenableWorker.Result.retry(), future.get(5, TimeUnit.SECONDS));
    }
}
