package se.oort.dipact

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    private val CHANNEL_ID = "default_channel"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.e(TAG, "Message data payload empty")
        }

        sendNotification(remoteMessage)
    }

    override fun onNewToken(token: String) {
        FCMToken = token;
        if (MainActivity != null) {
            MainActivity!!.onNewFCMToken(token)
        }
    }

    private fun sendNotification(remoteMessage: RemoteMessage) {
//        val intent = Intent(this, Main::class.java)
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//        val pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
//            PendingIntent.FLAG_ONE_SHOT)
//
//        val channelId = CHANNEL_ID
//        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//        val notificationBuilder = NotificationCompat.Builder(this, channelId)
//            .setSmallIcon(R.drawable.ic_stat_ic_notification)
//            .setContentTitle(getString(R.string.fcm_message))
//            .setContentText(messageBody)
//            .setAutoCancel(true)
//            .setSound(defaultSoundUri)
//            .setContentIntent(pendingIntent)
//
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        // Since android Oreo notification channel is needed.
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(channelId,
//                "Default channel",
//                NotificationManager.IMPORTANCE_DEFAULT)
//            notificationManager.createNotificationChannel(channel)
//        }
//
//        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }
}