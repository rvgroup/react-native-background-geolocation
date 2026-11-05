package com.intentfilter.androidpermissions;

import com.intentfilter.androidpermissions.PermissionManager.PermissionRequestListener;
import com.intentfilter.androidpermissions.helpers.AppStatus;
import com.intentfilter.androidpermissions.helpers.Logger;
import com.marianhello.backgroundgeolocation.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.List;

import static com.intentfilter.androidpermissions.services.BroadcastService.IntentAction.ACTION_PERMISSIONS_REQUEST;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PermissionHandlerTest {
    @Mock private AppStatus appStatus;
    @Mock private PermissionManager manager;
    @Mock private PermissionRequestListener listener;
    @Mock private Logger logger;

    private PermissionHandler handler;
    private static final String COARSE = "android.permission.ACCESS_COARSE_LOCATION";
    private static final String FINE = "android.permission.ACCESS_FINE_LOCATION";
    private static final List<String> LOCATION = asList(COARSE, FINE);

    @Before
    public void setUp() {
        handler = new PermissionHandler(appStatus, logger, manager);
    }

    @Test
    public void alreadyGrantedAndRepeatedStartCompleteWithoutPrompt() {
        when(manager.permissionAlreadyGranted(COARSE)).thenReturn(true);
        when(manager.permissionAlreadyGranted(FINE)).thenReturn(true);
        handler.checkPermissions(LOCATION, listener);
        handler.checkPermissions(LOCATION, listener);
        verify(listener, times(2)).onPermissionGranted();
        verify(listener, never()).onPermissionDenied();
        verify(manager, never()).registerBroadcastReceiver(anyString());
        verify(manager, never()).startPermissionActivity(anySet());
    }

    @Test
    public void firstForegroundRequestWaitsForAllPermissions() {
        when(appStatus.isInForeground()).thenReturn(true);
        handler.checkPermissions(LOCATION, listener);
        verify(manager).registerBroadcastReceiver(ACTION_PERMISSIONS_REQUEST);
        verify(manager).startPermissionActivity(new HashSet<>(LOCATION));
        verifyNoInteractions(listener);
        handler.onPermissionsResult(new String[]{COARSE}, new String[]{});
        verifyNoInteractions(listener);
        handler.onPermissionsResult(new String[]{FINE}, new String[]{});
        verify(listener).onPermissionGranted();
        verify(manager).unregisterBroadcastReceiver();
    }

    @Test
    public void denialThenRetryCanBeGranted() {
        when(appStatus.isInForeground()).thenReturn(true);
        handler.checkPermissions(LOCATION, listener);
        handler.onPermissionsResult(new String[]{}, new String[]{COARSE, FINE});
        verify(listener).onPermissionDenied();
        verify(listener, never()).onPermissionGranted();

        PermissionRequestListener retry = mock(PermissionRequestListener.class);
        handler.checkPermissions(LOCATION, retry);
        verify(manager, times(2)).startPermissionActivity(new HashSet<>(LOCATION));
        handler.onPermissionsResult(new String[]{COARSE, FINE}, new String[]{});
        verify(retry).onPermissionGranted();
        verify(retry, never()).onPermissionDenied();
        verify(listener, never()).onPermissionGranted();
    }

    @Test
    public void partiallyGrantedRequestOnlyPromptsForMissingPermission() {
        when(appStatus.isInForeground()).thenReturn(true);
        when(manager.permissionAlreadyGranted(COARSE)).thenReturn(true);
        handler.checkPermissions(LOCATION, listener);
        verify(manager).startPermissionActivity(new HashSet<>(singletonList(FINE)));
        handler.onPermissionsResult(new String[]{FINE}, new String[]{});
        verify(listener).onPermissionGranted();
    }

    @Test
    public void concurrentFacadeAndLocationManagerRequestsSharePrompt() {
        when(appStatus.isInForeground()).thenReturn(true);
        PermissionRequestListener currentLocation = mock(PermissionRequestListener.class);
        handler.checkPermissions(LOCATION, listener);
        handler.checkPermissions(LOCATION, currentLocation);
        verify(manager).startPermissionActivity(new HashSet<>(LOCATION));
        handler.onPermissionsResult(new String[]{COARSE, FINE}, new String[]{});
        verify(listener).onPermissionGranted();
        verify(currentLocation).onPermissionGranted();
    }

    @Test
    public void mixedResultNotifiesOnlyRelevantListeners() {
        PermissionRequestListener coarseOnly = mock(PermissionRequestListener.class);
        handler.checkPermissions(LOCATION, listener);
        handler.checkPermissions(singletonList(COARSE), coarseOnly);
        handler.onPermissionsResult(new String[]{COARSE}, new String[]{FINE});
        verify(listener).onPermissionDenied();
        verify(listener, never()).onPermissionGranted();
        verify(coarseOnly).onPermissionGranted();
        verify(coarseOnly, never()).onPermissionDenied();
    }

    @Test
    public void interruptedRequestCanBeRetried() {
        when(appStatus.isInForeground()).thenReturn(true);
        handler.checkPermissions(LOCATION, listener);
        handler.invalidatePendingPermissionRequests(LOCATION);
        verify(listener).onPermissionDenied();
        verify(manager).unregisterBroadcastReceiver();
        handler.checkPermissions(LOCATION, listener);
        verify(manager, times(2)).startPermissionActivity(new HashSet<>(LOCATION));
        handler.onPermissionsResult(new String[]{COARSE, FINE}, new String[]{});
        verify(listener).onPermissionGranted();
    }

    @Test
    public void backgroundRequestUsesNotification() {
        handler.checkPermissions(LOCATION, listener);
        verify(manager).showPermissionNotification(new HashSet<>(LOCATION),
                R.string.title_permission_required, R.string.message_permission_required);
        verify(manager, never()).startPermissionActivity(anySet());
        handler.onPermissionsResult(new String[]{COARSE, FINE}, new String[]{});
        verify(listener).onPermissionGranted();
    }

    @Test
    public void dismissedNotificationDeniesAndAllowsRetry() {
        handler.checkPermissions(LOCATION, listener);
        handler.invalidatePendingPermissionRequests(LOCATION);
        handler.checkPermissions(LOCATION, listener);
        verify(listener).onPermissionDenied();
        verify(manager, times(2)).showPermissionNotification(new HashSet<>(LOCATION),
                R.string.title_permission_required, R.string.message_permission_required);
    }
}
