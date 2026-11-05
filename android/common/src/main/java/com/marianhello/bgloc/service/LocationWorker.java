package com.marianhello.bgloc.service;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.BackgroundLocation;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocationWorker extends ListenableWorker {
    public static final String TAG = LocationWorker.class.getSimpleName();
    static final long LOCATION_TIMEOUT_MS = 30000;
    private final CancellationSignal cancellation = new CancellationSignal();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean();
    private Runnable timeout;

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            completer.addCancellationListener(this::cancelRequest, Runnable::run);
            String generation = getInputData().getString(LocationWorkerManager.GENERATION);
            if (isStopped() || !LocationWorkerManager.isCurrent(getApplicationContext(), generation)) {
                finish(completer, Result.success(), generation, null, false);
                return TAG;
            }

            timeout = () -> finish(completer, Result.retry(), generation, null, false);
            handler.postDelayed(timeout, LOCATION_TIMEOUT_MS);
            try {
                LocationManager manager = (LocationManager) getApplicationContext()
                        .getSystemService(Context.LOCATION_SERVICE);
                String provider = findLocationProvider(manager);
                if (provider == null) {
                    finish(completer, Result.retry(), generation, null, false);
                } else {
                    LocationManagerCompat.getCurrentLocation(manager, provider, cancellation,
                            ContextCompat.getMainExecutor(getApplicationContext()), location -> {
                                if (location == null) {
                                    finish(completer, Result.retry(), generation, null, false);
                                } else {
                                    finish(completer, Result.success(), generation, location, true);
                                }
                            });
                }
            } catch (SecurityException e) {
                // Permissions may have been revoked after this work was scheduled.
                finish(completer, Result.failure(), generation, null, false);
            } catch (RuntimeException e) {
                finish(completer, Result.retry(), generation, null, false);
            }
            return TAG;
        });
    }

    private void finish(CallbackToFutureAdapter.Completer<Result> completer, Result result,
                        String generation, Location location, boolean scheduleNext) {
        if (!finished.compareAndSet(false, true)) return;
        cleanup();
        if (isStopped()) {
            completer.setCancelled();
            return;
        }
        try {
            // Serialize the callback with stop/start and reject stale generations.
            boolean delivered = LocationWorkerManager.complete(getApplicationContext(), generation, () -> {
                if (location != null) {
                    LocationServiceImpl.broadcastLocation(getApplicationContext(),
                            new BackgroundLocation(Config.WORKER_PROVIDER, location));
                }
            }, scheduleNext);
            completer.set(delivered ? result : Result.success());
        } catch (RuntimeException e) {
            completer.set(Result.retry());
        }
    }

    private void cleanup() {
        if (timeout != null) handler.removeCallbacks(timeout);
        cancellation.cancel();
    }

    private void cancelRequest() {
        finished.set(true);
        cleanup();
    }

    @Override
    public void onStopped() {
        cancelRequest();
        super.onStopped();
    }

    private String findLocationProvider(LocationManager manager) {
        if (manager == null) return null;
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) return LocationManager.FUSED_PROVIDER;
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        return null;
    }
}
