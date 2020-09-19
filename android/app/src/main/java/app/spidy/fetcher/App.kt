package app.spidy.fetcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import app.spidy.fetcher.utils.Ads
import com.google.android.gms.ads.MobileAds

class App: Application() {

    companion object {
        const val CHANNEL_ID = "SERVICE_NOTIFICATION_CHANNEL_ID"
    }

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "IdmService Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}