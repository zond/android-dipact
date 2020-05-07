package se.oort.dipact

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import java.lang.RuntimeException
import java.nio.charset.Charset
import java.util.*
import java.util.zip.Inflater

fun ShowNotification(context: Context, payload: String) {
    val compressedJSON =
        Base64.decode(payload, Base64.DEFAULT)
    val decompresser = Inflater()
    decompresser.setInput(compressedJSON, 0, compressedJSON.size)
    val result = ByteArray(8192)
    val resultLength = decompresser.inflate(result)
    decompresser.end()
    val actualResult = String(
        Arrays.copyOfRange(result, 0, resultLength),
        Charset.forName("UTF-8")
    )
    Log.d(TAG, "Got notification " + actualResult)

    val intent = Intent(context, Main::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

    val dipJSON = Gson().fromJson(
        actualResult,
        DiplicityJSON::class.java
    )
    val channelId = CHANNEL_ID
    val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    var notificationBuilder: NotificationCompat.Builder? = null
    if (dipJSON.phaseMeta != null) {
        intent.putExtra(CLICK_ACTION, "/Game/" + dipJSON.gameID)
        val pendingIntent = PendingIntent.getActivity(
            context, RC_NOTIFICATION, intent,
            PendingIntent.FLAG_ONE_SHOT
        )
        notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_otto)
            .setContentTitle(
                dipJSON.gameDesc + ": " +
                        dipJSON.phaseMeta.Season + " " +
                        dipJSON.phaseMeta.Year + ", " +
                        dipJSON.phaseMeta.Type
            )
            .setContentText(
                dipJSON.gameDesc + ": " +
                        dipJSON.phaseMeta.Season + " " +
                        dipJSON.phaseMeta.Year + ", " +
                        dipJSON.phaseMeta.Type + " has resolved"
            )
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
    } else if (dipJSON.message != null) {
        intent.putExtra(
            CLICK_ACTION,
            "/Game/" + dipJSON.message.GameID + "/Channel/" + dipJSON.message.ChannelMembers!!.joinToString(
                ","
            ) + "/Messages"
        )
        val pendingIntent = PendingIntent.getActivity(
            context, RC_NOTIFICATION, intent,
            PendingIntent.FLAG_ONE_SHOT
        )
        notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_otto)
            .setContentTitle(
                dipJSON.message.Sender + " -> " + dipJSON.message.ChannelMembers.joinToString(
                    ", "
                )
            )
            .setContentText(dipJSON.message.Body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
    }
    if (notificationBuilder == null) {
        Log.e(TAG, "Notification with neither phaseMeta nor message: " + Gson().toJson(dipJSON))
        return
    }

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Since android Oreo notification channel is needed.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Default channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    notificationManager.notify(0, notificationBuilder.build())
}

class MessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isEmpty()) {
            Log.e(TAG, "Message data payload empty: " + remoteMessage)
            return
        }
        val payload = remoteMessage.getData().get(DIPLICITY_JSON)!!
        if (MainActivity != null) {
            MainActivity!!.incomingPayload(payload)
            return
        }
        ShowNotification(this, payload)
    }

    override fun onNewToken(token: String) {
        FCMToken = token;
        if (MainActivity != null) {
            MainActivity!!.onNewFCMToken(token)
        }
    }
}