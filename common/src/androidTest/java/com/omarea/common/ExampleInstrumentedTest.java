package com.omarea.common;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

/**
 * RU: Базовый instrumented-тест — проверяет, что package name приложения
 *     совпадает с ожидаемым. Stage 18.
 * EN: Baseline instrumented test — verifies that the app package name
 *     matches the expected value. Stage 18.
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void appPackageIsCorrect() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("com.omarea.common.test", context.getPackageName());
    }
}
