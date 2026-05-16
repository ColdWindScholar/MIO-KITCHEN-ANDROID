package com.mio.kitchen

import android.app.Application
import android.content.Context

class PIO : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageConfig.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
    }
}
