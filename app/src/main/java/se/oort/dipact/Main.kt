package se.oort.dipact

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


class Main : AppCompatActivity() {

    private val RC_SIGNIN = 9001
    private val CLIENT_ID = "635122585664-ao5i9f2p5365t4htql1qdb6uulso4929.apps.googleusercontent.com";

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
                loadWebView(parsedURI.getQueryParameter("token")!!)
            }
        }.start()
    }

    private fun loadWebView(token: String) {
        web_view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        web_view.settings.javaScriptEnabled = true
        web_view.settings.domStorageEnabled = true
        web_view.loadUrl("https://dipact.appspot.com?token=" + token)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

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
                finishLogin(result!!.signInAccount!!)
            } else {
                throw RuntimeException("Failed login: " + result!!.status)
            }
        }
    }
}
