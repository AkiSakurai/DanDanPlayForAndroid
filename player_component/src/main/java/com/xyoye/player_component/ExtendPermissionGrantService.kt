package com.xyoye.player_component

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xyoye.player_component.ui.activities.player.PlayerActivity
import com.xyoye.player_component.ui.activities.player_intent.PlayerIntentActivity

class ExtendPermissionGrantService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel_id = "dandanplay"
        val requestID = 1

        val channel = NotificationChannel(channel_id, channel_id, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = channel_id
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = Intent(this, PlayerIntentActivity::class.java).let {
            it.setDataAndType(intent!!.data, intent.type)
            it.action = intent.action
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            PendingIntent.getActivity(this, requestID, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val notifyBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, channel_id)
                .setSmallIcon(R.drawable.ic_video_play)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentTitle("Resume Playing")
                .setContentIntent(pendingIntent)
                .setSilent(true)
        val myNotification: Notification = notifyBuilder.build()

        startForeground(requestID, myNotification)

        return Service.START_REDELIVER_INTENT
    }
}