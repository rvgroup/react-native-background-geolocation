package com.marianhello.bgloc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Looper;

import com.github.jparkie.promise.Promise;
import com.github.jparkie.promise.Promises;
import com.intentfilter.androidpermissions.PermissionManager;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

public class LocationManager {
    private Context mContext;
    private static LocationManager mLocationManager;
    private static final Executor REQUEST_EXECUTOR = new ThreadPoolExecutor(
            0, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<Runnable>(),
            runnable -> new Thread(runnable, "bgloc-current-location"));
    private final ConcurrentHashMap<Promise<Location>, FutureTask<Void>> requests = new ConcurrentHashMap<>();

    public static final String[] PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    LocationManager(Context context) {
        mContext = context;
    }

    public class PermissionDeniedException extends Exception {}

    public static synchronized LocationManager getInstance(Context context) {
        if (mLocationManager == null) {
            mLocationManager = new LocationManager(context.getApplicationContext());
        }
        return mLocationManager;
    }

    public Promise<Location> getCurrentLocation(final int timeout, final long maximumAge, final boolean enableHighAccuracy) {
        final Promise<Location> promise = Promises.promise();

        PermissionManager permissionManager = PermissionManager.getInstance(mContext);
        permissionManager.checkPermissions(Arrays.asList(PERMISSIONS), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {
                // Permission results arrive on the main thread. Never wait for a
                // location there: its LocationListener also runs on that thread.
                if (promise.isCancelled()) return;
                FutureTask<Void> task = new FutureTask<Void>(() -> {
                        try {
                            Location location = getCurrentLocationNoCheck(timeout, maximumAge, enableHighAccuracy);
                            synchronized (promise) {
                                if (!promise.isDone()) promise.set(location);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            fail(promise, e);
                        } catch (Exception e) {
                            fail(promise, e);
                        }
                        return null;
                    }) {
                    @Override protected void done() {
                        requests.remove(promise, this);
                    }
                };
                requests.put(promise, task);
                if (promise.isCancelled()) {
                    task.cancel(true);
                    return;
                }
                try {
                    REQUEST_EXECUTOR.execute(task);
                } catch (RejectedExecutionException e) {
                    requests.remove(promise, task);
                    fail(promise, e);
                }
            }

            @Override
            public void onPermissionDenied() {
                fail(promise, new PermissionDeniedException());
            }
        });

        return promise;
    }

    private static void fail(Promise<Location> promise, Exception error) {
        synchronized (promise) {
            if (!promise.isDone()) promise.setError(error);
        }
    }

    public void cancel(Promise<Location> promise) {
        synchronized (promise) {
            if (!promise.isDone()) promise.cancel();
        }
        FutureTask<Void> task = requests.remove(promise);
        if (task != null) task.cancel(true);
    }

    /**
     * Get current location without permission checking
     *
     * @param timeout
     * @param maximumAge
     * @param enableHighAccuracy
     * @return
     * @throws InterruptedException
     * @throws TimeoutException
     */
    @SuppressLint("MissingPermission")
    public Location getCurrentLocationNoCheck(int timeout, long maximumAge, boolean enableHighAccuracy) throws InterruptedException, TimeoutException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("A blocking location request cannot run on the main thread");
        }
        if (timeout < 0 || maximumAge < 0) {
            throw new IllegalArgumentException("timeout and maximumAge must be non-negative");
        }
        final long minLocationTime = System.currentTimeMillis() - maximumAge;
        final android.location.LocationManager locationManager = (android.location.LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        Location lastKnownGPSLocation = locationManager.getAllProviders().contains(android.location.LocationManager.GPS_PROVIDER)
                ? locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) : null;
        if (lastKnownGPSLocation != null && lastKnownGPSLocation.getTime() >= minLocationTime) {
            return lastKnownGPSLocation;
        }

        Location lastKnownNetworkLocation = locationManager.getAllProviders().contains(android.location.LocationManager.NETWORK_PROVIDER)
                ? locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) : null;
        if (lastKnownNetworkLocation != null && lastKnownNetworkLocation.getTime() >= minLocationTime) {
            return lastKnownNetworkLocation;
        }

        Criteria criteria = new Criteria();
        criteria.setAccuracy(enableHighAccuracy ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);

        CurrentLocationListener locationListener = new CurrentLocationListener();
        try {
            locationManager.requestSingleUpdate(criteria, locationListener, Looper.getMainLooper());
            if (!locationListener.mCountDownLatch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException();
            }
            return locationListener.mLocation;
        } finally {
            locationManager.removeUpdates(locationListener);
        }
    }

    static class CurrentLocationListener implements LocationListener {
        Location mLocation = null;
        final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public void onLocationChanged(Location location) {
            mLocation = location;
            mCountDownLatch.countDown();
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    }
}
