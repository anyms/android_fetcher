package app.spidy.fetcher.adapters

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import app.spidy.fetcher.R
import app.spidy.fetcher.data.IdmSnapshot
import app.spidy.fetcher.databases.IdmDatabase
import app.spidy.idm.data.Detect
import app.spidy.kotlinutils.*
import java.io.File
import java.lang.Exception
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random


class SnapshotAdapter(
    private val context: Context?,
    private val snapshots: ArrayList<IdmSnapshot>,
    private val pause: (String) -> Unit,
    private val resume: (IdmSnapshot) -> Unit,
    private val updateNothingView: () -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var threadsProgressBar = ArrayList<ProgressBar>()
    private var threadsProgressBarRand = ArrayList<Int>()

    private var primaryProgressBar: ProgressBar? = null
    private var fileSizeField: TextView? = null
    private var downloadedField: TextView? = null
    private var speedField: TextView? = null
    private var remainingTimeField: TextView? = null
    private var resumeSupportField: TextView? = null
    private var isResumable = false
    private lateinit var idmDatabase: IdmDatabase

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.layout_download_item, parent, false)
        idmDatabase = Room.databaseBuilder(context!!, IdmDatabase::class.java, "IdmDatabase")
            .fallbackToDestructiveMigration().build()
        return MainHolder(v)
    }

    override fun getItemCount(): Int = snapshots.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mainHolder = holder as MainHolder
        val snapshot = snapshots[position]
        val progress = (snapshot.downloadedSize / snapshot.contentSize.toFloat() * 100).toInt()

        val params = mainHolder.rootView.layoutParams as RecyclerView.LayoutParams
        params.bottomMargin = if (snapshots.size - 1 == position) 40 else 0
        mainHolder.rootView.layoutParams = params

        updateProgressDialog(progress, snapshot)

        mainHolder.infoImageView.setOnClickListener {
            createProgressDialog(snapshot).show()
            updateProgressDialog(progress, snapshot)
        }

        mainHolder.rootView.setOnClickListener {
            if (snapshot.idmStatus == IdmSnapshot.STATUS_COMPLETED) {
                openFile(snapshot)
            }
        }

        mainHolder.fileNameView.text = snapshots[position].fileName
        mainHolder.speedView.text = snapshots[position].speed
        mainHolder.remainingTimeView.text = if (snapshot.remainingTime.trim() == "") "0sec" else snapshot.remainingTime

        mainHolder.controlImageView.setOnClickListener {
            debug(snapshot.idmStatus)
            when (snapshot.idmStatus) {
                IdmSnapshot.STATUS_PROGRESS -> {
                    pause(snapshot.uId)
                }
                IdmSnapshot.STATUS_PAUSED -> {
                    resume(snapshot)
                }
                else -> {
                    val ask = Ask(context!!).apply {
                        title = "Are you sure?"
                        message = "Do you really want to delete this file? this can not be undone."
                    }
                    ask.onOk {
                        deleteSnapshot(position, snapshot) {
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, snapshots.size)
                        }
                    }
                    ask.show()
                }
            }
        }

        if (snapshot.idmStatus == IdmSnapshot.STATUS_PROGRESS || snapshot.type == "") {
            mainHolder.progressBar.visibility = View.VISIBLE
            mainHolder.speedView.visibility = View.VISIBLE
            mainHolder.remainingTimeView.visibility = View.VISIBLE
        } else {
            mainHolder.progressBar.visibility = View.GONE
            mainHolder.speedView.visibility = View.GONE
            mainHolder.remainingTimeView.visibility = View.GONE
        }

        if (snapshot.type == "") {
            mainHolder.progressBar.isIndeterminate = true
            mainHolder.fileIconView.setImageResource(R.drawable.ic_file)
            mainHolder.controlImageView.setImageResource(R.drawable.ic_close)
            mainHolder.statusView.text = snapshot.initStatus
            mainHolder.urlView.text = "-"
        } else if (snapshot.idmStatus == IdmSnapshot.STATUS_FINALIZING) {
            mainHolder.statusView.text = snapshot.initStatus
        } else {
            mainHolder.progressBar.isIndeterminate = false
            mainHolder.progressBar.progress = progress
            mainHolder.statusView.text = "${formatBytes(snapshot.downloadedSize)} / ${formatBytes(snapshot.contentSize)} • $progress%"

            when (snapshot.type) {
                Detect.TYPE_FACEBOOK -> {
                    mainHolder.urlView.text =
                        "https://www.facebook.com/watch?v=${snapshot.data["id"]}"
                }
                Detect.TYPE_GOOGLE -> {
                    mainHolder.urlView.text = snapshot.data["audio"]
                }
                else -> {
                    mainHolder.urlView.text = snapshot.data["url"]
                }
            }

            when (snapshot.type) {
                Detect.TYPE_GOOGLE,
                Detect.TYPE_FACEBOOK,
                Detect.TYPE_VIDEO -> {
                    mainHolder.fileIconView.setImageResource(R.drawable.ic_video_file)
                }
                Detect.TYPE_STREAM -> {
                    mainHolder.fileIconView.setImageResource(R.drawable.ic_stream_file)
                }
                Detect.TYPE_AUDIO -> {
                    mainHolder.fileIconView.setImageResource(R.drawable.ic_audio_file)
                }
                else -> {
                    mainHolder.fileIconView.setImageResource(R.drawable.ic_file)
                }
            }

            when (snapshot.idmStatus) {
                IdmSnapshot.STATUS_PROGRESS -> {
                    mainHolder.controlImageView.setImageResource(R.drawable.ic_pause)
                }
                IdmSnapshot.STATUS_FAILED -> {
                    mainHolder.controlImageView.setImageResource(R.drawable.ic_delete)
                    if (snapshot.downloadedSize == snapshot.contentSize) {
                        mainHolder.statusView.text = "Unable to finalize \uD83D\uDE30"
                    } else {
                        mainHolder.statusView.text = "Failed \uD83D\uDE2D"
                    }
                }
                IdmSnapshot.STATUS_COMPLETED -> {
                    mainHolder.controlImageView.setImageResource(R.drawable.ic_delete)
                    mainHolder.statusView.text =
                        "Completed \uD83D\uDE42 • ${formatBytes(snapshot.contentSize)}"
                }
                IdmSnapshot.STATUS_PAUSED -> {
                    mainHolder.controlImageView.setImageResource(R.drawable.ic_download)
                }
            }
        }

        mainHolder.menuImageView.setOnClickListener {
            when (snapshot.idmStatus) {
                IdmSnapshot.STATUS_PROGRESS -> showOptionMenu(
                    position,
                    snapshot,
                    mainHolder.menuImageView,
                    R.menu.option_menu_progress
                )
                IdmSnapshot.STATUS_COMPLETED -> showOptionMenu(
                    position,
                    snapshot,
                    mainHolder.menuImageView,
                    R.menu.option_menu_completed
                )
                IdmSnapshot.STATUS_PAUSED -> showOptionMenu(
                    position,
                    snapshot,
                    mainHolder.menuImageView,
                    R.menu.option_menu_paused
                )
                IdmSnapshot.STATUS_FAILED -> showOptionMenu(
                    position,
                    snapshot,
                    mainHolder.menuImageView,
                    R.menu.option_menu_failed
                )
            }
        }
    }


    private fun showOptionMenu(position: Int, snapshot: IdmSnapshot, menuImageView: ImageView, id: Int) {
        val popupMenu =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                PopupMenu(
                    context,
                    menuImageView,
                    Gravity.NO_GRAVITY,
                    android.R.attr.actionOverflowMenuStyle,
                    0
                )
            } else {
                PopupMenu(context, menuImageView)
            }
        popupMenu.inflate(id)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuPause -> pause(snapshot.uId)
                R.id.menuShowProgress -> {
                    val progress = (snapshot.downloadedSize / snapshot.contentSize.toFloat() * 100).toInt()
                    createProgressDialog(snapshot).show()
                    updateProgressDialog(progress, snapshot)
                }
                R.id.menuCopyUrl -> {
                    val clipboardManager = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipData = if (snapshot.type == Detect.TYPE_FACEBOOK) {
                        ClipData.newPlainText("url", "https://www.facebook.com/watch?v=${snapshot.data["id"]}")
                    } else if (snapshot.type == Detect.TYPE_GOOGLE) {
                        ClipData.newPlainText("url", snapshot.data["audio"])
                    } else {
                        ClipData.newPlainText("url", snapshot.data["url"])
                    }
                    clipboardManager?.setPrimaryClip(clipData)

                    context?.toast("URL copied!")
                }
                R.id.menuOpen -> openFile(snapshot)
                R.id.menuDelete -> {
                    deleteSnapshot(position, snapshot) {
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, snapshots.size)
                    }
                }
                R.id.menuResume -> resume(snapshot)
                R.id.menuRetry -> resume(snapshot)
            }
            return@setOnMenuItemClickListener true
        }
        popupMenu.show()
    }

    private fun deleteSnapshot(position: Int, snapshot: IdmSnapshot, callback: () -> Unit) {
        val dir = context!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null) {
            val file = File(dir.absolutePath, snapshot.fileName)
            file.delete()

            thread {
                idmDatabase.idmDao().removeSnapshot(snapshot)
                onUiThread {
                    snapshots.removeAt(position)
                    callback()
                    updateNothingView()
                }
            }
        }
    }

    private fun openFile(snapshot: IdmSnapshot) {
        val dir = context!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null) {
            try {
                val file = File(dir.absolutePath, snapshot.fileName)
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val mime = context.contentResolver.getType(uri)

                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                intent.setDataAndType(uri, mime)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            } catch (e: Exception) {
                context.toast("Unable to open \uD83D\uDE1F")
            }
        }
    }

    private fun formatBytes(bytes: Long, isSpeed: Boolean = false): String {
        val unit = if (isSpeed) 1000.0 else 1024.0
        return when {
            bytes < unit * unit -> "${((bytes / unit).toFloat() * 100.0).roundToInt() / 100.0}KB"
            bytes < (unit.pow(2.0) * 1000) -> "${((bytes / unit.pow(2.0)).toFloat() * 100.0).roundToInt() / 100.0}MB"
            else -> "${((bytes / unit.pow(3.0)).toFloat() * 100.0).roundToInt() / 100.0}GB"
        }
    }

    private fun createProgressDialog(snapshot: IdmSnapshot): AlertDialog {
        threadsProgressBar.clear()

        val viewGroup: ViewGroup? = null
        val inflater = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.dialog_progress_thread_10, viewGroup)

        for (i in 1..10) {
            threadsProgressBar.add(view.findViewById(context.resources.getIdentifier("thread$i", "id", context.packageName)))
            threadsProgressBarRand.add(Random.nextInt(0, 10))
        }

        primaryProgressBar = view.findViewById(R.id.primaryProgressBar)
        fileSizeField = view.findViewById(R.id.fileSize)
        downloadedField = view.findViewById(R.id.downloaded)
        speedField = view.findViewById(R.id.speed)
        remainingTimeField = view.findViewById(R.id.remainingTime)
        resumeSupportField = view.findViewById(R.id.resumeSupport)

        val d = AlertDialog.Builder(context)
            .setNegativeButton(context.getString(R.string.close), null)
        var progressDialog: AlertDialog? = null
        if (snapshot.idmStatus == IdmSnapshot.STATUS_PAUSED) {
            d.setPositiveButton(context.getString(R.string.resume)) {_, _ ->
                resume(snapshot)
                progressDialog?.dismiss()
            }
        }

        progressDialog = d.create()
        progressDialog.setView(view)
        progressDialog.window?.setBackgroundDrawableResource(android.R.color.white)
        return progressDialog
    }

    private fun updateProgressDialog(progress: Int, snapshot: IdmSnapshot) {
        primaryProgressBar?.progress = progress
        fileSizeField?.text = formatBytes(snapshot.contentSize)
        downloadedField?.text = formatBytes(snapshot.downloadedSize)
        speedField?.text = snapshot.speed
        remainingTimeField?.text = if (snapshot.remainingTime.trim() == "") "0sec" else snapshot.remainingTime
        resumeSupportField?.text = if (snapshot.isResumable) context!!.getString(R.string.yes) else context!!.getString(R.string.no)
        ignore {
            for (i in 0 until threadsProgressBar.size) {
                threadsProgressBar[i].progress = progress + threadsProgressBarRand[i]
            }
        }
    }



    inner class MainHolder(v: View): RecyclerView.ViewHolder(v) {
        val fileIconView: ImageView = v.findViewById(R.id.fileIconView)
        val fileNameView: TextView = v.findViewById(R.id.fileNameView)
        val urlView: TextView = v.findViewById(R.id.urlView)
        val menuImageView: ImageView = v.findViewById(R.id.menuImageView)
        val progressBar: ProgressBar = v.findViewById(R.id.progressBar)
        val speedView: TextView = v.findViewById(R.id.speedView)
        val remainingTimeView: TextView = v.findViewById(R.id.remainingTimeView)
        val infoImageView: ImageView = v.findViewById(R.id.infoImageView)
        val statusView: TextView = v.findViewById(R.id.statusView)
        val controlImageView: ImageView = v.findViewById(R.id.controlImageView)
        val rootView: CardView = v.findViewById(R.id.rootView)
    }
}