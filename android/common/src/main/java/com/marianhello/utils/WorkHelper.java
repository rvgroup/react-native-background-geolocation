package com.marianhello.utils;

import android.content.Context;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class WorkHelper {
    public static WorkManager getWorkManager(Context context) {
        return WorkManager.getInstance(context);
    }

    public static void stop(Context context, String tag) {
        stop(context, tag, null);
    }

    public static void stop(Context context, String tag, String tag2) {
        WorkManager instance = getWorkManager(context);

        instance.cancelAllWorkByTag(tag);

        if (tag2 != null) {
            instance.cancelAllWorkByTag(tag2);
        }
    }

    public static boolean isRunning(Context context, String tag, String tag2) {
        boolean res = isRunning(context, tag);

        if (!res && tag2 != null) {
            res = isRunning(context, tag2);
        }

        return res;
    }

    public static boolean isRunning(Context context, String tag) {
        WorkManager instance = getWorkManager(context);

        ListenableFuture<List<WorkInfo>> statuses = instance.getWorkInfosByTag(tag);

        try {
            boolean running = false;
            List<WorkInfo> workInfoList = statuses.get();

            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                running = state == WorkInfo.State.RUNNING;
            }

            return running;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int getActiveWorkersCount(Context context, String tag) {
        WorkManager instance = getWorkManager(context);
        ListenableFuture<List<WorkInfo>> statuses = instance.getWorkInfosByTag(tag);

        try {
            List<WorkInfo> workInfoList = statuses.get().stream().filter(x -> x.getState() == WorkInfo.State.ENQUEUED || x.getState() == WorkInfo.State.RUNNING).collect(Collectors.toList());

            return workInfoList.size();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static void cancelAllWorkByTag(Context context, String tag) {
        WorkManager instance = getWorkManager(context);

        instance.cancelAllWorkByTag(tag);
    }

    public static void cancelAllWorkById(Context context, String workId) {
        WorkManager instance = getWorkManager(context);

        instance.cancelUniqueWork(workId);
    }

    public static boolean isScheduled(Context context, String tag) {
        WorkManager instance = getWorkManager(context);

        ListenableFuture<List<WorkInfo>> statuses = instance.getWorkInfosByTag(tag);

        try {
            boolean running = false;
            List<WorkInfo> workInfoList = statuses.get();

            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                running = state == WorkInfo.State.ENQUEUED;
            }

            return running;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
}