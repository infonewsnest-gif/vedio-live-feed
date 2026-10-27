package com.streamcast.studio.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.streamcast.studio.scene.SceneLayout
import com.streamcast.studio.service.StreamBroadcastService
import com.streamcast.studio.ui.theme.StreamCastTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val FB_PREFS = "fb_settings"
        private const val KEY_FB_APP_ID = "fb_app_id"
        private const val KEY_FB_TOKEN = "fb_client_token"
    }

    private lateinit var callbackManager: CallbackManager
    private var isFacebookLoggedIn by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraOk = permissions[Manifest.permission.CAMERA] != false
        val micOk = permissions[Manifest.permission.RECORD_AUDIO] != false
        if (!cameraOk || !micOk) {
            Toast.makeText(this, "ক্যামেরা ও মাইক্রোফোন পারমিশন প্রয়োজন!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        // আগে সেভ করা Facebook App ID / Client Token চালু করা
        val saved = getSharedPreferences(FB_PREFS, Context.MODE_PRIVATE)
        applyFacebookSettings(saved.getString(KEY_FB_APP_ID, "") ?: "", saved.getString(KEY_FB_TOKEN, "") ?: "")

        callbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    isFacebookLoggedIn = true
                    Toast.makeText(this@MainActivity, "ফেসবুক এসডিকে লগইন সফল!", Toast.LENGTH_SHORT).show()
                }

                override fun onCancel() {}

                override fun onError(error: FacebookException) {
                    Toast.makeText(this@MainActivity, "ত্রুটি: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        setContent {
            StreamCastTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isLive by remember { mutableStateOf(false) }
                    var streamPlatform by remember { mutableStateOf("Facebook") }
                    var caption by remember { mutableStateOf("") }
                    var streamKey by remember { mutableStateOf("") }
                    var showFbSettings by remember { mutableStateOf(false) }
                    val fbPrefs = getSharedPreferences(FB_PREFS, Context.MODE_PRIVATE)
                    var fbAppId by remember { mutableStateOf(fbPrefs.getString(KEY_FB_APP_ID, "") ?: "") }
                    var fbToken by remember { mutableStateOf(fbPrefs.getString(KEY_FB_TOKEN, "") ?: "") }
                    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
                    val breakingNewsText = "ব্রেকিং নিউজ: নিউজ নেস্ট স্ট্রিমিং অ্যাপ থেকে লাইভ সম্প্রচার চলছে..."

                    val videoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            selectedVideoUri = it
                            Toast.makeText(this@MainActivity, "ভিডিও সিলেক্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SceneLayout(
                            videoUri = selectedVideoUri,
                            tickerText = breakingNewsText,
                            modifier = Modifier.fillMaxSize()
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { streamPlatform = "Facebook" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (streamPlatform == "Facebook") Color(0xFF1877F2) else Color.DarkGray
                                    ),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text("ফেসবুক মোড", color = Color.White)
                                }

                                Button(
                                    onClick = { streamPlatform = "YouTube" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (streamPlatform == "YouTube") Color(0xFFFF0000) else Color.DarkGray
                                    ),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text("ইউটিউব মোড", color = Color.White)
                                }
                            }

                            if (streamPlatform == "Facebook") {
                                Button(
                                    onClick = {
                                        if (!isFacebookLoggedIn) {
                                            LoginManager.getInstance().logInWithReadPermissions(
                                                this@MainActivity,
                                                listOf("pages_show_list", "pages_manage_posts", "live_video")
                                            )
                                        } else {
                                            LoginManager.getInstance().logOut()
                                            isFacebookLoggedIn = false
                                            Toast.makeText(this@MainActivity, "ফেসবুক লগআউট হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFacebookLoggedIn) Color.Gray else Color(0xFF1877F2)
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(
                                        text = if (isFacebookLoggedIn) "ফেসবুক লগআউট (SDK)" else "ফেসবুক লগইন (SDK)",
                                        color = Color.White
                                    )
                                }

                                Button(
                                    onClick = { showFbSettings = !showFbSettings },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(
                                        text = if (showFbSettings) "ফেসবুক সেটিংস বন্ধ করুন" else "ফেসবুক সেটিংস (App ID / Token)",
                                        color = Color.White
                                    )
                                }

                                if (showFbSettings) {
                                    OutlinedTextField(
                                        value = fbAppId,
                                        onValueChange = { fbAppId = it },
                                        label = { Text("Facebook App ID") },
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = fbToken,
                                        onValueChange = { fbToken = it },
                                        label = { Text("Facebook Client Token") },
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            fbPrefs.edit()
                                                .putString(KEY_FB_APP_ID, fbAppId.trim())
                                                .putString(KEY_FB_TOKEN, fbToken.trim())
                                                .apply()
                                            applyFacebookSettings(fbAppId, fbToken)
                                            Toast.makeText(this@MainActivity, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Text("সেভ করুন", color = Color.White)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = streamKey,
                                onValueChange = { streamKey = it },
                                label = {
                                    Text(if (streamPlatform == "Facebook") "ফেসবুক Stream Key / API Key" else "ইউটিউব Stream Key / API Key")
                                },
                                singleLine = true,
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Text(text = "ফাইল ম্যানেজার থেকে ভিডিও সিলেক্ট করুন", color = Color.White)
                            }

                            OutlinedTextField(
                                value = caption,
                                onValueChange = { caption = it },
                                label = { Text("লাইভ ক্যাপশন / টাইটেল লিখুন...") },
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (isLive) {
                                        isLive = false
                                        stopLiveStream()
                                    } else if (selectedVideoUri == null) {
                                        Toast.makeText(this@MainActivity, "দয়া করে একটি ভিডিও সিলেক্ট করুন", Toast.LENGTH_SHORT).show()
                                    } else if (streamKey.isBlank() && !isFacebookLoggedIn) {
                                        Toast.makeText(this@MainActivity, "দয়া করে Stream Key দিন অথবা SDK দিয়ে লগইন করুন", Toast.LENGTH_SHORT).show()
                                    } else if (startLiveStream(streamPlatform, caption, streamKey, selectedVideoUri.toString())) {
                                        isLive = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLive) Color.Red else Color(0xFF00C853)
                                ),
                                modifier = Modifier.fillMaxWidth().height(45.dp)
                            ) {
                                Text(
                                    text = if (isLive) "লাইভ বন্ধ করুন" else "$streamPlatform লাইভ শুরু করুন",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun fieldColors() = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Black.copy(alpha = 0.6f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.LightGray,
        cursorColor = Color.White
    )

    @Deprecated("Needed by the Facebook SDK callback manager")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun applyFacebookSettings(appId: String, clientToken: String) {
        try {
            if (appId.isNotBlank()) FacebookSdk.setApplicationId(appId.trim())
            if (clientToken.isNotBlank()) FacebookSdk.setClientToken(clientToken.trim())
        } catch (e: Exception) {
            Toast.makeText(this, "ফেসবুক সেটিংস প্রয়োগ হয়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun neededPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun checkPermissions() {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startLiveStream(platform: String, caption: String, streamKey: String, videoUri: String): Boolean {
        val hasCamMic = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasCamMic) {
            Toast.makeText(this, "ক্যামেরা ও মাইক্রোফোন পারমিশন দিন", Toast.LENGTH_LONG).show()
            checkPermissions()
            return false
        }

        val intent = Intent(this, StreamBroadcastService::class.java).apply {
            action = StreamBroadcastService.ACTION_START_STREAM
            putExtra("EXTRA_PLATFORM", platform)
            putExtra("EXTRA_CAPTION", caption)
            putExtra("EXTRA_STREAM_KEY", streamKey)
            putExtra("EXTRA_VIDEO_URI", videoUri)
        }
        return try {
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "$platform লাইভ শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "লাইভ শুরু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun stopLiveStream() {
        stopService(Intent(this, StreamBroadcastService::class.java))
        Toast.makeText(this, "লাইভ বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
    }
}
