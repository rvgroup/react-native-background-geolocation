package com.marianhello.bgloc.service;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.BackgroundLocation;

public class LocationWorker extends ListenableWorker {
    public static final String TAG = LocationWorker.class.getSimpleName();

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        Context context = this.getApplicationContext();

        return CallbackToFutureAdapter.getFuture(completer -> {
            try {
                LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

                String provider = findLocationProvider(locationManager);

                if (provider != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        locationManager.getCurrentLocation(
                                provider,
                                null,
                                context.getMainExecutor(),
                                location -> {
                                    // TODO проверить нужно ли отправлять координату или ее поймает LocationServiceImpl.onLocation
                                    try {
                                        postLocation(context, location);
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }

                                    completer.set(Result.success());

                                    LocationWorkerManager.startWorker(context);
                                });
                    }
                } else {
                    completer.set(Result.failure());
                }
            } catch (Exception ex) {
                completer.setException(ex);
                completer.set(Result.failure());
            }

            return "LocationWorker.startWork";
        });
    }

    private void postLocation(Context context, Location location) {
        BackgroundLocation bgLocation = new BackgroundLocation(Config.WORKER_PROVIDER, location);

        LocationServiceImpl.broadcastLocation(context, bgLocation);
    }

    private String findLocationProvider(LocationManager locationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                return LocationManager.FUSED_PROVIDER;
            }
        }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }

        return null;
    }
}
