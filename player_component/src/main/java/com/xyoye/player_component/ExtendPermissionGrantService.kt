package com.xyoye.player_component

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class ExtendPermissionGrantService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_REDELIVER_INTENT
    }
}