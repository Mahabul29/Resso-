package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Admin panel (server.js) se bheji gayi notification ko app me dikhata hai.
 *
 * Server sirf GET /api/notification deta hai, isliye app khud check karta hai:
 * app khulte hi, aur jab tak app process zinda hai (music chal raha ho tab bhi) har
 * POLL_INTERVAL_MS par. Ek hi notification dobara nahi dikhti (id SharedPreferences me save hoti hai).
 */
object AdminNotifier {
    private const val TAG = "AdminNotifier"
    private const val CHANNEL_ID = "admin_updates"
    private const val PREFS = "admin_notifier"
    private const val KEY_LAST_ID = "last_id"
    private const val POLL_INTERVAL_MS = 2 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Application.onCreate se ek baar call karo. */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        createChannel(app)
        scope.launch {
            while (isActive) {
                checkNow(app)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** App foreground me aate hi turant check karne ke liye. */
    fun checkSoon(context: Context) {
        val app = context.applicationContext
        scope.launch { checkNow(app) }
    }

    private suspend fun checkNow(context: Context) {
        val base = AppConstants.NOTIFICATION_API_URL.trim().trimEnd('/')
        if (base.isEmpty()) {
            Log.w(TAG, "NOTIFICATION_API_URL empty hai (AppConstants.kt) - notification fetch skip")
            return
        }
        try {
            val body = withContext(Dispatchers.IO) {
                val req = Request.Builder().url("$base/api/notification").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Server ne ${resp.code} diya")
                        null
                    } else resp.body?.string()
                }
            } ?: return

            val n = JSONObject(body).optJSONObject("notification") ?: return
            val id = n.optString("id")
            if (id.isEmpty()) return

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_ID, null) == id) return

            val shown = show(
                context = context,
                id = id,
                title = n.optString("title"),
                message = n.optString("message"),
                linkUrl = n.optString("linkUrl").takeIf { it.isNotBlank() && it != "null" }
            )
            // Sirf tab "seen" mark karo jab sach me dikh gayi (permission na ho to baad me retry hoga).
            if (shown) prefs.edit().putString(KEY_LAST_ID, id).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Notification check fail", e)
        }
    }

    private fun show(context: Context, id: String, title: String, message: String, linkUrl: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS permission nahi hai")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        createChannel(context)

        val intent = if (linkUrl != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val pending = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "notify() refused", e)
            false
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Updates & announcements",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }
}
