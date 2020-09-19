package app.spidy.fetcher.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.room.Room
import app.spidy.fetcher.App
import app.spidy.fetcher.R
import app.spidy.fetcher.activities.MainActivity
import app.spidy.fetcher.data.IdmSnapshot
import app.spidy.fetcher.databases.IdmDatabase
import app.spidy.fetcher.interfaces.ServiceListener
import app.spidy.idm.Idm
import app.spidy.idm.data.Detect
import app.spidy.idm.data.Snapshot
import app.spidy.idm.interfaces.IdmListener
import app.spidy.kotlinutils.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class IdmService: Service() {
    companion object {
        var isRunning = false
        private const val NOTIFICATION_ID = 101
    }
    /* Fields */
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var idmDatabase: IdmDatabase
    private var maxDownloadSize: Int = 4
    private val idm = Idm(this)
    val uIds = Collections.synchronizedCollection(ArrayList<String>())
    private val snapshots = HashMap<String, Snapshot>()

    /* Listeners */
    var serviceListener: ServiceListener? = null
    var idmListener: IdmListener? = null
    private val localIdmListener = object : IdmListener {
        override fun onComplete(snapshot: Snapshot) {
            onUiThread {
                toast("A download completed")
                idmListener?.onComplete(snapshot)
            }
        }

        override fun onError(e: Exception, uId: String) {
            debug("Err: $e")
            if (snapshots[uId] != null) {
                snapshots[uId]!!.state = Snapshot.STATE_DONE
                updateSnapshot(snapshots[uId]!!, IdmSnapshot.STATUS_FAILED)
            }
            onUiThread {
                toast(e.message)
                idmListener?.onError(e, uId)
            }
            updateOnCompleteNotification(uId)
        }

        override fun onFail(snapshot: Snapshot) {
            debug("Failed!")
            updateSnapshot(snapshot, IdmSnapshot.STATUS_FAILED)
            onUiThread { idmListener?.onFail(snapshot) }
            updateOnCompleteNotification(snapshot.uId)
        }

        override fun onInit(uId: String, message: String) {
            onUiThread {
                idmListener?.onInit(uId, message)
            }
            if (!uIds.contains(uId)) uIds.add(uId)
            if (uIds.size == 1) {
                notification.setContentTitle(message)
                startForeground(NOTIFICATION_ID, notification.build())
            } else {
                notification.setContentTitle("${uIds.size} File(s) Downloading")
            }
            startForeground(NOTIFICATION_ID, notification.build())
        }

        override fun onPause(snapshot: Snapshot) {
            updateSnapshot(snapshot, IdmSnapshot.STATUS_PAUSED)
            onUiThread { idmListener?.onPause(snapshot) }
            updateOnCompleteNotification(snapshot.uId)
        }

        override fun onProgress(snapshot: Snapshot) {
            idm.maxSpeed = maxDownloadSize * 1024
            if (uIds.size == 1) {
                val progress = (snapshot.downloadedSize / snapshot.contentSize.toFloat() * 100).toInt()
                notification
                    .setProgress(100, progress, false)
                    .setContentText("")
                    .setSubText("$progress% • ${snapshot.speed}")
            } else {
                if (snapshot.uId == uIds.first()) {
                    var downloadedSize = 0L
                    var contentSize = 0L
                    for ((k, v) in snapshots) {
                        downloadedSize += v.downloadedSize
                        contentSize += v.contentSize
                    }
                    val progress = (downloadedSize / contentSize.toFloat() * 100).toInt()
                    notification
                        .setProgress(100, progress, false)
                        .setContentText("")
                        .setSubText("$progress%")
                }
            }
            startForeground(NOTIFICATION_ID, notification.build())
            onUiThread { idmListener?.onProgress(snapshot) }
        }

        override fun onStart(snapshot: Snapshot) {
            insertSnapshot(snapshot, IdmSnapshot.STATUS_PROGRESS)
            if (!uIds.contains(snapshot.uId)) {
                uIds.add(snapshot.uId)
            }
            snapshots[snapshot.uId] = snapshot
            if (uIds.size == 1) {
                notification
                    .setSubText("0%")
                    .setContentTitle(snapshot.fileName)
            } else {
                notification
                    .setSubText("0%")
                    .setContentTitle("${uIds.size} File(s) Downloading")
            }
            startForeground(NOTIFICATION_ID, notification.build())
            onUiThread { idmListener?.onStart(snapshot) }
        }

        override fun onCopy(snapshot: Snapshot, progress: Int) {
            if (uIds.size == 1) {
                notification.setProgress(100, progress, false)
                    .setSubText("$progress% • Finalizing...")
                startForeground(NOTIFICATION_ID, notification.build())
            }
            onUiThread { idmListener?.onCopy(snapshot, progress) }
        }

        override fun onCopied(snapshot: Snapshot) {
            updateSnapshot(snapshot, IdmSnapshot.STATUS_COMPLETED)
            onUiThread { idmListener?.onCopied(snapshot) }
            updateOnCompleteNotification(snapshot.uId)
        }

        override fun onCopyError(e: Exception, snapshot: Snapshot) {
            updateSnapshot(snapshot, IdmSnapshot.STATUS_FAILED)
            onUiThread { idmListener?.onCopyError(e, snapshot) }
            updateOnCompleteNotification(snapshot.uId)
        }
    }

    /* Overrides */
    override fun onBind(intent: Intent?): IBinder? = IdmBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tinyDB = TinyDB(this)
        maxDownloadSize = tinyDB.getInt("max_fetch_size", 4)
        idm.maxSpeed = maxDownloadSize * 1024

        isRunning = true
        idmDatabase = Room.databaseBuilder(this, IdmDatabase::class.java, "IdmDatabase")
            .fallbackToDestructiveMigration().build()
        notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
        idm.idmListener = localIdmListener

        val resultIntent = Intent(this, MainActivity::class.java)
        resultIntent.action = Intent.ACTION_MAIN
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            resultIntent, 0
        )

        notification
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Initializing...")
            .setProgress(100, 0, true)
            .setOnlyAlertOnce(true)
            .priority = NotificationCompat.PRIORITY_MAX

        startForeground(NOTIFICATION_ID, notification.build())
        return START_NOT_STICKY
    }

    private fun endService() {
        serviceListener?.onDestroy()
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        for ((_, snap) in snapshots) {
            updateSnapshot(snap, IdmSnapshot.STATUS_PAUSED)
        }
        super.onDestroy()
    }

    /* Methods */
    fun download(detect: Detect) {
        idm.download(detect)
    }

    fun pause(uId: String) {
        idm.pause(uId)
    }

    fun resume(idmSnapshot: IdmSnapshot) {
        val responseHeaders = HashMap<String, String>()
        val data = HashMap<String, String>()

        for ((k, v) in idmSnapshot.responseHeaders) {
            responseHeaders[k] = v
        }
        for ((k, v) in idmSnapshot.data) {
            data[k] = v
        }

        val snapshot = if (idmSnapshot.type == Detect.TYPE_STREAM) {
            Snapshot(
                uId = idmSnapshot.uId,
                fileName = idmSnapshot.fileName,
                downloadedSize = 0,
                contentSize = idmSnapshot.contentSize,
                requestHeaders = hashMapOf(),
                responseHeaders = responseHeaders,
                cookies = hashMapOf(),
                isResumable = false,
                type = Detect.TYPE_STREAM,
                data = data,
                speed = "0Kb/s",
                remainingTime = "0sec",
                state = Snapshot.STATE_PROGRESS
            )
        } else {
            Snapshot(
                uId = idmSnapshot.uId,
                fileName = idmSnapshot.fileName,
                downloadedSize = idmSnapshot.downloadedSize,
                contentSize = idmSnapshot.contentSize,
                requestHeaders = hashMapOf(),
                responseHeaders = responseHeaders,
                cookies = hashMapOf(),
                isResumable = idmSnapshot.isResumable,
                type = idmSnapshot.type,
                data = data,
                speed = idmSnapshot.speed,
                remainingTime = idmSnapshot.remainingTime,
                state = idmSnapshot.state
            )
        }
        idm.resume(snapshot)
    }

    fun updateOnCompleteNotification(uId: String) {
        uIds.remove(uId)
        snapshots.remove(uId)
        if (uIds.isEmpty()) {
            endService()
        } else {
            notification.setContentTitle("${uIds.size} File(s) Downloading")
            startForeground(NOTIFICATION_ID, notification.build())
        }
    }

    private fun insertSnapshot(snapshot: Snapshot, status: String) {
        thread {
            val idmSnapshot = IdmSnapshot(
                uId = snapshot.uId,
                fileName = snapshot.fileName,
                downloadedSize = snapshot.downloadedSize,
                contentSize = snapshot.contentSize,
                requestHeaders = snapshot.responseHeaders,
                responseHeaders = snapshot.responseHeaders,
                cookies = snapshot.cookies,
                isResumable = snapshot.isResumable,
                type = snapshot.type,
                data = snapshot.data,
                speed = snapshot.speed,
                remainingTime = snapshot.remainingTime,
                state = snapshot.state,
                idmStatus = status
            )
            idmDatabase.idmDao().putSnapshot(idmSnapshot)
        }
    }

    private fun updateSnapshot(snapshot: Snapshot, status: String) {
        thread {
            val idmSnapshot = IdmSnapshot(
                uId = snapshot.uId,
                fileName = snapshot.fileName,
                downloadedSize = snapshot.downloadedSize,
                contentSize = snapshot.contentSize,
                requestHeaders = snapshot.responseHeaders,
                responseHeaders = snapshot.responseHeaders,
                cookies = snapshot.cookies,
                isResumable = snapshot.isResumable,
                type = snapshot.type,
                data = snapshot.data,
                speed = snapshot.speed,
                remainingTime = snapshot.remainingTime,
                state = snapshot.state,
                idmStatus = status
            )
            idmDatabase.idmDao().updateSnapshot(idmSnapshot)
        }
    }

    private fun updateSnapshot(uId: String, status: String) {
        thread {
            val idmSnapshot = idmDatabase.idmDao().getSnapshot(uId)
            idmSnapshot.idmStatus = status
            idmDatabase.idmDao().updateSnapshot(idmSnapshot)
        }
    }

    inner class IdmBinder: Binder() {
        val service: IdmService = this@IdmService
    }
}