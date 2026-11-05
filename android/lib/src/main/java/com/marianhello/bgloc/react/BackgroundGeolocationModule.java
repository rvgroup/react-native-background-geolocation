package com.marianhello.bgloc.react;

import android.content.Context;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.marianhello.bgloc.BackgroundGeolocationFacade;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.PluginDelegate;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.react.data.LocationMapper;
import com.marianhello.bgloc.react.headless.HeadlessTaskRunner;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;

import org.json.JSONException;

import java.util.Collection;
import com.marianhello.utils.RecentLocationKeys;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

public class BackgroundGeolocationModule extends ReactContextBaseJavaModule implements LifecycleEventListener, PluginDelegate {

    public static final String LOCATION_EVENT = "location";
    public static final String STATIONARY_EVENT = "stationary";
    public static final String ACTIVITY_EVENT = "activity";

    public static final String FOREGROUND_EVENT = "foreground";
    public static final String BACKGROUND_EVENT = "background";
    public static final String AUTHORIZATION_EVENT = "authorization";

    public static final String START_EVENT = "start";
    public static final String STOP_EVENT = "stop";
    public static final String ABORT_REQUESTED_EVENT = "abort_requested";
    public static final String HTTP_AUTHORIZATION_EVENT = "http_authorization";
    public static final String ERROR_EVENT = "error";

    private static final int PERMISSIONS_REQUEST_CODE = 1;

    private BackgroundGeolocationFacade facade;
    private org.slf4j.Logger logger;

    private final RecentLocationKeys _sendLocations = new RecentLocationKeys(2048);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(64),
            runnable -> new Thread(runnable, "bgloc-native-task"));
    private final ThreadPoolExecutor locationExecutor = new ThreadPoolExecutor(
            0, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<Runnable>(),
            runnable -> new Thread(runnable, "bgloc-location-await"));
    private volatile boolean invalidated;

    public static class ErrorMap {
        public static ReadableMap from(String message, int code) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", code);
            out.putString("message", message);
            return out;
        }

        public static ReadableMap from(String message, Throwable cause, int code) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", code);
            out.putString("message", message);
            out.putMap("cause", from(cause));
            return out;
        }

        public static ReadableMap from(PluginException e) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", e.getCode());
            out.putString("message", e.getMessage());
            if (e.getCause() != null) {
                out.putMap("cause", from(e.getCause()));
            }

            return out;
        }

        private static WritableMap from(Throwable e) {
            WritableMap out = Arguments.createMap();
            out.putString("message", e.getMessage());
            return out;
        }
    }

    public BackgroundGeolocationModule(ReactApplicationContext reactContext) {
        super(reactContext);

        facade = new BackgroundGeolocationFacade(getContext(), this);
        logger = LoggerManager.getLogger(BackgroundGeolocationModule.class);

        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return "BackgroundGeolocation";
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     */
    @Override
    public void onHostResume() {
        if (invalidated) return;
        logger.info("App will be resumed");
        facade.resume();
        sendEvent(FOREGROUND_EVENT, null);
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onHostPause() {
        if (invalidated) return;
        logger.info("App will be paused");
        facade.pause();
        sendEvent(BACKGROUND_EVENT, null);
    }

    /**
     * The final call you receive before your activity is destroyed.
     * Checks to see if it should turn off
     */
    @Override
    public void onHostDestroy() {
        if (invalidated) return;
        logger.info("Destroying plugin");
        facade.destroy();
//        facade = null;
    }

    @Override
    public void invalidate() {
        invalidated = true;
        getReactApplicationContext().removeLifecycleEventListener(this);
        executor.shutdownNow();
        locationExecutor.shutdownNow();
        facade.destroy();
        _sendLocations.clear();
        super.invalidate();
    }

    private void runOnBackgroundThread(Runnable runnable, Callback error) {
        runOnBackgroundThread(executor, runnable, error);
    }

    private void runOnBackgroundThread(ThreadPoolExecutor pool, Runnable runnable, Callback error) {
        if (invalidated) return;
        try {
            pool.execute(() -> {
                if (invalidated) return;
                try {
                    runnable.run();
                } catch (Exception e) {
                    if (!invalidated && getReactApplicationContext().hasActiveReactInstance()) {
                        invokeCallback(error, ErrorMap.from(e.getMessage(), e, PluginException.SERVICE_ERROR));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (!invalidated) invokeCallback(error, ErrorMap.from("Too many pending geolocation operations", e,
                    PluginException.SERVICE_ERROR));
        }
    }

    private void invokeCallback(Callback callback, Object... args) {
        if (!invalidated && getReactApplicationContext().hasActiveReactInstance()) callback.invoke(args);
    }

    @ReactMethod
    public void start() {
        facade.start();
    }

    @ReactMethod
    public void stop() {
        facade.stop();
        _sendLocations.clear();
    }

    @ReactMethod
    public void switchMode(Integer mode, Callback success, Callback error) {
        facade.switchMode(mode);
    }

    @ReactMethod
    public void configure(final ReadableMap options, final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Config config = ConfigMapper.fromMap(options);
                    facade.configure(config);
                    invokeCallback(success, true);
                } catch (JSONException e) {
                    logger.error("Configuration error: {}", e.getMessage());
                    invokeCallback(error, ErrorMap.from("Configuration error", e, PluginException.CONFIGURE_ERROR));
                } catch (PluginException e) {
                    logger.error("Configuration error: {}", e.getMessage());
                    invokeCallback(error, ErrorMap.from(e));
                }
            }
        }, error);
    }

    @ReactMethod
    public void showLocationSettings() {
        BackgroundGeolocationFacade.showLocationSettings(getContext());
    }

    @ReactMethod
    public void showAppSettings() {
        BackgroundGeolocationFacade.showAppSettings(getContext());
    }

    @ReactMethod
    public void getStationaryLocation(Callback success, Callback error) {
        BackgroundLocation stationaryLocation = facade.getStationaryLocation();
        if (stationaryLocation != null) {
            invokeCallback(success, LocationMapper.toWriteableMap(stationaryLocation));
        } else {
            invokeCallback(success);
        }
    }

    @ReactMethod
    public void getLocations(final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                WritableArray locationsArray = Arguments.createArray();
                Collection<BackgroundLocation> locations = facade.getLocations();
                for (BackgroundLocation location : locations) {
                    locationsArray.pushMap(LocationMapper.toWriteableMapWithId(location));
                }
                invokeCallback(success, locationsArray);
            }
        }, error);
    }

    @ReactMethod
    public void getValidLocations(final Callback success, Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                WritableArray locationsArray = Arguments.createArray();
                Collection<BackgroundLocation> locations = facade.getValidLocations();
                for (BackgroundLocation location : locations) {
                    locationsArray.pushMap(LocationMapper.toWriteableMapWithId(location));
                }
                invokeCallback(success, locationsArray);
            }
        }, error);
    }

    @ReactMethod
    public void deleteLocation(final Integer locationId, final Callback success, Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                facade.deleteLocation(locationId.longValue());
                invokeCallback(success, true);
            }
        }, error);
    }

    @ReactMethod
    public void deleteAllLocations(final Callback success, Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                facade.deleteAllLocations();
                invokeCallback(success, true);
            }
        }, error);
    }

    @ReactMethod
    public void getCurrentLocation(final ReadableMap options, final Callback success, final Callback error) {
      runOnBackgroundThread(locationExecutor, new Runnable() {
        public void run() {
          try {
              int timeout = options.hasKey("timeout") ? options.getInt("timeout") : Integer.MAX_VALUE;
              long maximumAge = options.hasKey("maximumAge") ? options.getInt("maximumAge") : Long.MAX_VALUE;
              boolean enableHighAccuracy = options.hasKey("enableHighAccuracy") && options.getBoolean("enableHighAccuracy");

              BackgroundLocation location = facade.getCurrentLocation(timeout, maximumAge, enableHighAccuracy);
               invokeCallback(success, LocationMapper.toWriteableMap(location));
          } catch (PluginException e) {
               invokeCallback(error, ErrorMap.from(e));
          }
        }
      }, error);
    }

    @ReactMethod
    public void getConfig(final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                Config config = facade.getConfig();
                ReadableMap out = ConfigMapper.toMap(config);
                invokeCallback(success, out);
            }
        }, error);
    }

    @ReactMethod
    public void getLogEntries(final Integer limit, final Integer offset, final String minLevel, final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                WritableArray logEntriesArray = Arguments.createArray();
                Collection<LogEntry> logEntries = facade.getLogEntries(limit, offset, minLevel);
                for (LogEntry logEntry : logEntries) {
                    WritableMap out = Arguments.createMap();
                    out.putInt("id", logEntry.getId());
                    out.putInt("context", logEntry.getContext());
                    out.putString("level", logEntry.getLevel());
                    out.putString("message", logEntry.getMessage());
                    out.putString("timestamp", new Long(logEntry.getTimestamp()).toString());
                    out.putString("logger", logEntry.getLoggerName());
                    if (logEntry.hasStackTrace()) {
                        out.putString("stackTrace", logEntry.getStackTrace());
                    }

                    logEntriesArray.pushMap(out);
                }
                invokeCallback(success, logEntriesArray);
            }
        }, error);
    }

    @ReactMethod
    public void checkStatus(final Callback success, final Callback error) {
        runOnBackgroundThread(new Runnable() {
            public void run() {
                try {
                    WritableMap out = Arguments.createMap();
                    out.putBoolean("isRunning", facade.isRunning());
                    out.putBoolean("hasPermissions", facade.hasPermissions()); //@Deprecated
                    out.putBoolean("locationServicesEnabled", facade.locationServicesEnabled());
                    out.putInt("authorization", getAuthorizationStatus());
                    invokeCallback(success, out);
                } catch (PluginException e) {
                    logger.error("Location service checked failed: {}", e.getMessage());
                    invokeCallback(error, ErrorMap.from(e));
                }
            }
        }, error);
    }

    @ReactMethod
    public void registerHeadlessTask(Callback success, Callback error) {
        logger.debug("Registering headless task");
        facade.registerHeadlessTask(HeadlessTaskRunner.class.getName());
        invokeCallback(success);
    }

    @ReactMethod
    public void forceSync(Callback success, Callback error) {
        facade.forceSync();
        invokeCallback(success);
    }

    private void sendEvent(String eventName, Object params) {
        if (invalidated || !getReactApplicationContext().hasActiveReactInstance()) return;
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendError(PluginException error) {
        WritableMap out = Arguments.createMap();
        out.putInt("code", error.getCode());
        out.putString("message", error.getMessage());

        sendEvent(ERROR_EVENT, out);
    }

    private void sendError(int code, String message) {
        WritableMap out = Arguments.createMap();
        out.putInt("code", code);
        out.putString("message", message);

        sendEvent(ERROR_EVENT, out);
    }

    public int getAuthorizationStatus() {
        return facade.getAuthorizationStatus();
    }

    public Context getContext() {
        return getReactApplicationContext().getBaseContext();
    }

    @Override
    public void onAuthorizationChanged(int authStatus) {
        sendEvent(AUTHORIZATION_EVENT, authStatus);
    }

    @Override
    public void onLocationChanged(BackgroundLocation location) {
        long key = location.getTime() / 2000;

        if (_sendLocations.add(key)) {
            sendEvent(LOCATION_EVENT, LocationMapper.toWriteableMapWithId(location));
        }
    }

    @Override
    public void onStationaryChanged(BackgroundLocation location) {
        sendEvent(STATIONARY_EVENT, LocationMapper.toWriteableMapWithId(location));
    }

    @Override
    public void onActivityChanged(BackgroundActivity activity) {
        WritableMap out = Arguments.createMap();
        out.putInt("confidence", activity.getConfidence());
        out.putString("type", BackgroundActivity.getActivityString(activity.getType()));
        sendEvent(ACTIVITY_EVENT, out);
    }

    @Override
    public void onServiceStatusChanged(int status) {
        switch (status) {
            case BackgroundGeolocationFacade.SERVICE_STARTED:
                sendEvent(START_EVENT, null);
                return;
            case BackgroundGeolocationFacade.SERVICE_STOPPED:
                sendEvent(STOP_EVENT, null);
                return;
        }
    }

    @Override
    public void onError(PluginException error) {
        sendError(error);
    }

    @Override
    public void onAbortRequested() {
        sendEvent(ABORT_REQUESTED_EVENT, null);
    }

    @Override
    public void onHttpAuthorization() {
        sendEvent(HTTP_AUTHORIZATION_EVENT, null);
    }
}
