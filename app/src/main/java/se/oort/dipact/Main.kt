package se.oort.dipact

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val TAG = "Dipact"
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
        if (BuildConfig.DEBUG) "http://localhost:8080" else "https://dipact.appspot.com"
    private var pendingAction: String? = null;

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun getToken() {
            this@Main.getToken()
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
        fun pendingAction(): String? {
            return this@Main.pendingAction
        }

        @JavascriptInterface
        fun bounceNotification(payload: String) {
            ShowNotification(this@Main, payload)
        }
    }

    fun onNewFCMToken(fcmToken: String) {
        runOnUiThread {
            web_view.evaluateJavascript("Globals.messaging.onNewToken('" + fcmToken + "');", null)
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
            web_view.evaluateJavascript(
                "if (window.Globals && window.Globals.messaging && window.Globals.messaging.main) {" +
                        "window.Globals.messaging.main.renderPath(\"" + action + "\");" +
                        "}", null
            )
        }
    }

    fun incomingPayload(payload: String) {
        runOnUiThread {
            web_view.evaluateJavascript(
                "if (window.Globals && window.Globals.messaging) {" +
                        "window.Globals.messaging.onWrapperMessage(\"" + payload + "\");" +
                        "}", null
            )
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
        }
        web_view.setWebViewClient(WebViewClient())
        web_view.settings.javaScriptEnabled = true
        web_view.settings.domStorageEnabled = true
        web_view.addJavascriptInterface(WebAppInterface(this), "Wrapper")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGNIN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result!!.isSuccess) {
                finishLogin(result.signInAccount!!)
            } else {
                throw RuntimeException("Failed login: " + result.status)
            }
        }
    }

    private fun finishLogin(account: GoogleSignInAccount) {
        Thread {
            val request = Request.Builder().url(
                "https://diplicity-engine.appspot.com/Auth/OAuth2Callback?code=" +
                        URLEncoder.encode(account.getServerAuthCode(), "UTF-8") +
                        "&approve-redirect=true&state=" + URLEncoder.encode(
                    "https://android-dipact",
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
            if (response.code != 307) {
                throw RuntimeException("Unsuccessful response " + response.body!!.string())
            }
            val url = response.headers["Location"]
            if (url == null) {
                throw RuntimeException("No Location header in response " + response.body!!.string())
            }
            val parsedURI: Uri = Uri.parse(url)
                ?: throw java.lang.RuntimeException("Unparseable Location header " + url.toString() + " in response")
            runOnUiThread {
                web_view.evaluateJavascript(
                    "Globals.WrapperCallbacks.getToken('" + parsedURI.getQueryParameter("token")!! + "');",
                    null
                );
            }
        }.start()
    }
}
