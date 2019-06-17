package com.example.turnbyturn

import android.app.Application
import android.support.v4.content.ContextCompat
import com.mapbox.mapboxsdk.Mapbox

class MainApplication: Application() {

    companion object {
        var instance: MainApplication? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Mapbox.getInstance(applicationContext, resources.getString(R.string.mapbox_access_token))
    }
}