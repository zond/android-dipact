package se.oort.diplicity

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider.getUriForFile
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


const val TAG = "Diplicity"
const val RC_NOTIFICATION = 9002
const val DIPLICITY_JSON = "DiplicityJSON"
const val CLICK_ACTION = "clickAction"
const val CHANNEL_ID = "default_channel"


var MainActivity: Main? = null
var FCMToken: String? = null

class Main : AppCompatActivity() {

    private val RC_SIGNIN = 9001
    private val CLIENT_ID =
        "635122585664-ao5i9f2p5365t4htql1qdb6uulso4929.apps.googleusercontent.com"
    private val SERVER_URL =
        if (BuildConfig.DEBUG) "http://localhost:8080" else CONFIG_SERVER_URL
    private var pendingAction: String? = null;

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun getToken() {
            this@Main.getToken()
        }

        @JavascriptInterface
        fun getAPI() : String? {
            return CONFIG_API
        }

        @JavascriptInterface
        fun downloadDataURI(uri: String, filename: String) {
            this@Main.downloadDataURI(uri, filename)
        }

        @JavascriptInterface
        fun startFCM() {
            MainActivity = this@Main
            if (FCMToken != null) {
                this@Main.onNewFCMToken(FCMToken!!)
            }
            FirebaseInstanceId.getInstance().instanceId
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "getInstanceId failed", task.exception)
                        return@OnCompleteListener
                    }
                    if (task.result?.token != FCMToken) {
                        FCMToken = task.result?.token
                        Log.d(TAG, "Got FCM token " + FCMToken!!)
                        this@Main.onNewFCMToken(FCMToken!!)
                    }
                })
        }

        @JavascriptInterface
        fun notificationStatus(): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return "OK"
            }
            val notificationManager =
				this@Main.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			if (!notificationManager.areNotificationsEnabled()) {
				return "Notifications blocked by Android"
			}
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			    return "OK"
			}
            val wantedChannel = NotificationChannel(
                CHANNEL_ID,
                "Default channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(wantedChannel)
            val foundChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (foundChannel.importance <= NotificationManager.IMPORTANCE_NONE) {
                return "Default channel blocked by Android"
            }
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
				return "OK";
			}
			if (foundChannel.group != null) {
				val foundGroup = notificationManager.getNotificationChannelGroup(foundChannel.group)
					if (foundGroup != null) {
						if (foundGroup.isBlocked) {
							return "Default channel group is blocked by Android"
						}
					}
			}
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
				return "OK";
			}
			if (notificationManager.areNotificationsPaused()) {
				return "Notifications paused by Android"
			}
            return "OK"
        }

        @JavascriptInterface
        fun pendingAction(): String? {
            return this@Main.pendingAction
        }

        @JavascriptInterface
        fun getDeviceID(): String? {
            return Settings.Secure.getString(this@Main.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        }

        @JavascriptInterface
        fun bounceNotification(payload: String) {
            ShowNotification(this@Main, payload)
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("diplicity", text)
            clipboard.setPrimaryClip(clip)
        }
    }

    fun onNewFCMToken(fcmToken: String) {
        runOnUiThread {
            runJavascript("window.Globals.messaging.onNewToken('" + fcmToken + "');")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent!!.extras != null && intent.extras!!.getString(CLICK_ACTION, null) != null) {
            executeAction(intent.extras!!.getString(CLICK_ACTION, null))
        }
    }

    fun executeAction(action: String) {
        pendingAction = action
        runOnUiThread {
            runJavascript("if (window.Globals && window.Globals.messaging && window.Globals.messaging.main) {" +
                    "window.Globals.messaging.main.renderPath(\"" + action + "\");" +
                    "}")
        }
    }

    fun incomingPayload(payload: String) {
        runOnUiThread {
            runJavascript("if (window.Globals && window.Globals.messaging) {" +
                    "window.Globals.messaging.onWrapperMessage(\"" + payload + "\");" +
                    "}")
        }
    }

    override fun onStop() {
        super.onStop()
        MainActivity = null
    }

    override fun onResume() {
        super.onResume()
        supportActionBar!!.hide()
        if (intent!!.extras != null && intent.extras!!.getString(CLICK_ACTION, null) != null) {
            executeAction(intent.extras!!.getString(CLICK_ACTION, null))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (web_view.canGoBack()) {
                        web_view.goBack()
                    } else {
                        finish()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result?.token
                // Log and toast
                Log.d(TAG, "got token " + token)
            })

        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        } else {
            val databasePath = web_view.getContext().getDir(
                "databases",
                Context.MODE_PRIVATE
            ).getPath()
			@Suppress("DEPRECATION")
            web_view.settings.databasePath = databasePath
        }
        web_view.setWebViewClient(object: WebViewClient() {
            override fun onPageFinished(web_view: WebView, url: String) {
                this@Main.web_view.visibility = WebView.VISIBLE
                this@Main.loading_content.visibility = View.GONE
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                if (url == null) {
                    return false
                }
                if (url.startsWith(SERVER_URL)) {
                    return false
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@Main.startActivity(intent)
                return true
            }
        })
        web_view.settings.javaScriptEnabled = true
        web_view.settings.domStorageEnabled = true
        web_view.addJavascriptInterface(WebAppInterface(this), "Wrapper")
        web_view.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        })
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			@Suppress("DEPRECATION")
            web_view.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // chromium, enable hardware acceleration
            web_view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            // older android version, disable hardware acceleration
            web_view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        web_view.loadUrl(SERVER_URL)
    }

    private fun getToken() {
        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(CLIENT_ID)
                .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGNIN)
    }

    private fun runJavascript(script: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            web_view.evaluateJavascript(script, null)
        } else {
            web_view.loadUrl("javascript:" + script)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGNIN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result!!.isSuccess) {
                finishLogin(result.signInAccount!!)
            } else {
                Log.e(TAG, "Error logging in: " + result.status)
                runOnUiThread {
                    runJavascript("window.Globals.WrapperCallbacks.getToken({error: '" + result.status + "'});")
                }
            }
        }
    }

    private fun finishLogin(account: GoogleSignInAccount) {
        Thread {
            val request = Request.Builder().url(
                "https://diplicity-engine.appspot.com/Auth/OAuth2Callback?code=" +
                        URLEncoder.encode(account.getServerAuthCode(), "UTF-8") +
                        "&approve-redirect=true&state=" + URLEncoder.encode(
                    "https://android-diplicity",
                    "UTF-8"
                )
            )
            val response = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
                .newCall(request.build()).execute()
            if (response.code < 300 || response.code >= 400) {
                Log.e(TAG, "Error logging in: " + response.code + "/" + response.body!!.string())
                runOnUiThread {
                    runJavascript("window.Globals.WrapperCallbacks.getToken({error: '" + response.body!!.string() + "'});")
                }
            }
            val url = response.headers["Location"]
            if (url == null) {
                Log.e(
                    TAG,
                    "Error logging in, missing Location header: " + response.code + "/" + response.body!!.string()
                )
                runJavascript("window.Globals.WrapperCallbacks.getToken({error: 'No Location header in response: " + response.body!!.string() + "'});")
            }
            val parsedURI: Uri = Uri.parse(url)
            runOnUiThread {
                runJavascript("window.Globals.WrapperCallbacks.getToken({token: '" + parsedURI.getQueryParameter("token")!! + "'});")
            }
        }.start()
    }

    private fun downloadDataURI(uri: String, filename: String) {
        // Save the file.
        val u = Uri.parse(uri)
        val semiColonSplit = u.schemeSpecificPart.split(";")
        val mimeType = semiColonSplit[0]
        val commaSplit = semiColonSplit[1].split(",")
        val base64Part = commaSplit[1]
        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
        val path = File(cacheDir, "images")
        if (!path.exists()) {
            path.mkdirs()
        }
        val outFile = File(path, filename)
        val outputStream = FileOutputStream(outFile)
        outputStream.write(bytes)
        outputStream.close()
        val fileURI = getUriForFile(this, "se.oort.diplicity.fileprovider", outFile);

        // Notify about saving the file.
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.type = mimeType
        intent.putExtra(Intent.EXTRA_STREAM, fileURI)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val digest = MessageDigest.getInstance("SHA-1")
        val notificationID = BigInteger(digest.digest(filename.toByteArray())).toInt()

        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, notificationID, intent, 0)

		val notificationBuilder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Notification.Builder(this, CHANNEL_ID)
		} else {
			@Suppress("DEPRECATION")
			Notification.Builder(this)
		}
		val notification: Notification = notificationBuilder
			.setSmallIcon(R.drawable.ic_otto)
            .setContentText(getString(R.string.msg_file_downloaded))
            .setContentTitle(filename)
            .setContentIntent(pendingIntent)
            .build()

        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL

        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Default channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationID, notification)
    }
}
