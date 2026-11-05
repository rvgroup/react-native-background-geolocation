package com.marianhello.utils;

import android.app.PendingIntent;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SystemUtilsTest {
    @Test public void packageScopedMutableIntentDoesNotNeedUnsafeFlag() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        int flags = SystemUtils.getMuttableIntentFlag(PendingIntent.FLAG_CANCEL_CURRENT);
        assertEquals(0, flags & PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
        PendingIntent intent = PendingIntent.getBroadcast(context, 1,
                new Intent("bgloc.test").setPackage(context.getPackageName()), flags);
        assertNotNull(intent);
        assertFalse(intent.isImmutable());
    }
}
