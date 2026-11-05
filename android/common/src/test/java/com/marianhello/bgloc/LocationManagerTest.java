package com.marianhello.bgloc;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import com.github.jparkie.promise.Promise;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocationManagerTest {
    private android.location.LocationManager platform;
    private LocationManager manager;

    @Before public void setUp() {
        Context context = mock(Context.class);
        platform = mock(android.location.LocationManager.class);
        when(context.getSystemService(Context.LOCATION_SERVICE)).thenReturn(platform);
        when(platform.getAllProviders()).thenReturn(Collections.emptyList());
        manager = new LocationManager(context);
    }

    @Test public void timeoutAlwaysRemovesListener() throws Exception {
        FutureTask<Location> task = new FutureTask<>(() -> manager.getCurrentLocationNoCheck(0, 0, true));
        new Thread(task).start();
        try {
            task.get(5, TimeUnit.SECONDS);
            fail("Expected timeout");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
        }
        verify(platform).removeUpdates(any(LocationListener.class));
    }

    @Test public void interruptionAlwaysRemovesListener() throws Exception {
        CountDownLatch registered = new CountDownLatch(1);
        doAnswer(invocation -> { registered.countDown(); return null; })
                .when(platform).requestSingleUpdate(any(Criteria.class), any(LocationListener.class), any(Looper.class));
        FutureTask<Location> task = new FutureTask<>(() -> manager.getCurrentLocationNoCheck(60000, 0, true));
        Thread thread = new Thread(task);
        thread.start();
        assertTrue(registered.await(5, TimeUnit.SECONDS));
        thread.interrupt();
        try {
            task.get(5, TimeUnit.SECONDS);
            fail("Expected interruption");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof InterruptedException);
        }
        verify(platform).removeUpdates(any(LocationListener.class));
    }

    @Test public void successfulRequestAlsoRemovesListener() throws Exception {
        Location location = new Location("gps");
        doAnswer(invocation -> {
            ((LocationListener) invocation.getArgument(1)).onLocationChanged(location);
            return null;
        }).when(platform).requestSingleUpdate(any(Criteria.class), any(LocationListener.class), any(Looper.class));
        FutureTask<Location> task = new FutureTask<>(() -> manager.getCurrentLocationNoCheck(1000, 0, true));
        new Thread(task).start();
        assertSame(location, task.get(5, TimeUnit.SECONDS));
        verify(platform).removeUpdates(any(LocationListener.class));
    }

    @Test(expected = IllegalStateException.class)
    public void blockingMainThreadRequestIsRejected() throws Exception {
        manager.getCurrentLocationNoCheck(1000, 0, true);
    }

    @Test public void grantedCallbackDoesNotBlockMainThreadAndCanBeCancelled() throws Exception {
        android.app.Application application = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication shadow = org.robolectric.shadow.api.Shadow.extract(application);
        shadow.grantPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        LocationManager blocking = new LocationManager(application) {
            @Override public Location getCurrentLocationNoCheck(int timeout, long age, boolean accuracy)
                    throws InterruptedException {
                assertNotSame(Looper.getMainLooper(), Looper.myLooper());
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                    return null;
                } finally {
                    interrupted.countDown();
                }
            }
        };
        Promise<Location> promise = blocking.getCurrentLocation(60000, 0, true);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        blocking.cancel(promise);
        assertTrue(promise.isCancelled());
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }
}
