package se.oort.dipact

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
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
var MainActivity: Main? = null
var FCMToken: String? = null

class Main : AppCompatActivity() {

    private val RC_SIGNIN = 9001
    private val CLIENT_ID = "635122585664-ao5i9f2p5365t4htql1qdb6uulso4929.apps.googleusercontent.com"
    private val SERVER_URL = if (BuildConfig.DEBUG) "http://localhost:8080" else "https://dipact.appspot.com"
    //private val SERVER_URL = "https://dipact.appspot.com"

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
    }

    fun onNewFCMToken(fcmToken: String) {
        web_view.evaluateJavascript("Globals.messaging.onNewToken('" + fcmToken + "');", null)
    }

    private fun loadWebView(token: String) {
        web_view.loadUrl("https://dipact.appspot.com?token=" + token)
    }

    override fun onResume() {
        super.onResume()
        supportActionBar!!.hide()
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
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
        if(requestCode == RC_SIGNIN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result!!.isSuccess) {
                finishLogin(result.signInAccount!!)
            } else {
                throw RuntimeException("Failed login: " + result.status)
            }
        }
    }

    private fun finishLogin(account : GoogleSignInAccount) {
        Thread {
            val request = Request.Builder().url("https://diplicity-engine.appspot.com/Auth/OAuth2Callback?code=" +
                    URLEncoder.encode(account.getServerAuthCode(), "UTF-8") +
                    "&approve-redirect=true&state=" + URLEncoder.encode("https://android-dipact", "UTF-8"))
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
                web_view.evaluateJavascript (
                    "Globals.WrapperCallbacks.getToken('" + parsedURI.getQueryParameter("token")!! + "');",
                    null);
            }
        }.start()
    }
}
