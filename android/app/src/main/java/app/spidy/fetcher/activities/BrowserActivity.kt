package app.spidy.fetcher.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.spidy.fetcher.BrowserListener
import app.spidy.fetcher.R
import app.spidy.fetcher.adapters.DetectAdapter
import app.spidy.fetcher.interfaces.ServiceListener
import app.spidy.fetcher.services.IdmService
import app.spidy.fetcher.utils.Ads
import app.spidy.idm.data.Detect
import app.spidy.kookaburra.fragments.BrowserFragment
import app.spidy.kotlinutils.TinyDB
import app.spidy.kotlinutils.debug
import app.spidy.kotlinutils.toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class BrowserActivity : AppCompatActivity() {
    /* Fields */
    private var isBound = false
    private var idmService: IdmService? = null
    private val detects = ArrayList<Detect>()
    private val queue = Collections.synchronizedCollection(ArrayList<Detect>())
    private lateinit var browserFragment: BrowserFragment
    private lateinit var browserListener: BrowserListener
    private lateinit var tinyDB: TinyDB

    private lateinit var fab: FloatingActionButton

    private var detectAdapter: DetectAdapter? = null
    private var detectDialog: AlertDialog? = null

    /* Listeners */
    private val serviceListener = object : ServiceListener {
        override fun onDestroy() {
            unbind()
        }
    }

    private val idmConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            idmService = (service as? IdmService.IdmBinder)?.service
            idmService?.serviceListener = serviceListener
            isBound = true

            for (d in queue) idmService?.download(d)
            queue.clear()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            idmService = null
            isBound = false
        }
    }

    private val detectListener = object : DetectListener {
        override fun onDetect(detects: List<Detect>) {
            this@BrowserActivity.detects.clear()
            for (d in detects) this@BrowserActivity.detects.add(d)
            this@BrowserActivity.detects.reverse()

            if (detects.isEmpty()) {
                fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(
                    this@BrowserActivity, R.color.colorContent))
            } else {
                fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(
                    this@BrowserActivity, R.color.colorAccent))
                heartBeat(fab)
                debug(detectAdapter)
                detectAdapter?.notifyDataSetChanged()
            }
        }
    }

    /* Overrides */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        tinyDB = TinyDB(this)

        if (!tinyDB.getBoolean("is_pro")) {
            findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())
        }

        fab = findViewById(R.id.fab)
        fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorContent))

        browserListener = BrowserListener(detectListener, intent?.getStringExtra("url")) {
            download(it)
        }
        browserFragment = BrowserFragment()
        browserFragment.browserListener = browserListener
        supportFragmentManager.beginTransaction()
            .add(R.id.browserHolder, browserFragment)
            .commit()

        val helperDialog = createHelperDialog()
        fab.setOnClickListener {
            if (detects.isEmpty()) {
                helperDialog.show()
            } else {
                detectDialog = showDetectsDialog(browserFragment.currentTab?.title ?: "-")
            }
        }

        thread {
            Thread.sleep(10000)

            runOnUiThread {

            }
        }
    }

    override fun onPause() {
        unbind()
        super.onPause()
    }

    override fun onResume() {
        if (IdmService.isRunning) {
            bind()
        }
        super.onResume()
    }

    override fun onBackPressed() {
        if (!browserFragment.onBackPressed()) {
            super.onBackPressed()
        }
    }


    /* Methods */
    private fun unbind() {
        if (isBound) {
            unbindService(idmConnection)
            isBound = false
        }
    }

    private fun bind() {
        if (!isBound) {
            bindService(Intent(this, IdmService::class.java), idmConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startIdmService() {
        val intent = Intent(this, IdmService::class.java)
        startService(intent)
        bind()
        IdmService.isRunning = true
    }

    private fun download(detect: Detect) {
        if (!tinyDB.getBoolean("is_pro")) {
            if (detect.type == Detect.TYPE_STREAM) {
                Ads.showReward {
                    Ads.showInterstitial()
                    Ads.loadInterstitial()
                }
                Ads.loadReward()
            } else {
                Ads.showInterstitial()
                Ads.loadInterstitial()
            }
        }
        debug("Is service running: ${IdmService.isRunning}")
        if (!IdmService.isRunning) {
            startIdmService()
        }
        if (isBound) {
            idmService?.download(detect)
        } else {
            queue.add(detect)
        }
        toast("Downloading ${detect.data["filename"]}... \uD83D\uDE0E")
    }

    @SuppressLint("SetTextI18n")
    private fun showDetectsDialog(title: String): AlertDialog {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        val viewGroup: ViewGroup? = null
        val view = LayoutInflater.from(this).inflate(R.layout.layout_detects_dialog, viewGroup, false)
        val detectRecyclerView: RecyclerView = view.findViewById(R.id.detectedRecyclerView)
        val closeImageView: ImageView = view.findViewById(R.id.closeImageView)
        val titleView: TextView = view.findViewById(R.id.titleView)
        val detectNumView: TextView = view.findViewById(R.id.detectNumView)

        builder.setView(view)
        val dialog = builder.create()
        dialog.show()

        titleView.text = title
        closeImageView.setOnClickListener {
            dialog.dismiss()
        }
        detectAdapter = DetectAdapter(this, detects) {
            download(it)
        }
        detectRecyclerView.adapter = detectAdapter
        detectRecyclerView.layoutManager = LinearLayoutManager(this)
        detectNumView.text = "File(s) • ${detects.size}"
        dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
        return dialog
    }

    private fun createHelperDialog(): AlertDialog {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        val viewGroup: ViewGroup? = null
        val view = LayoutInflater.from(this).inflate(R.layout.layout_helper_dialog, viewGroup, false)
        builder.setView(view)
        val dialog = builder.create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { d, _ ->
            d.dismiss()
        }
        return dialog
    }

    private fun heartBeat(v: View) {
        val anim = ScaleAnimation(1f, 1.5f, 1f, 1.5f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 1f)
        anim.fillAfter = true
        anim.duration = 200
        fab.startAnimation(anim)
        val anim2 = ScaleAnimation(1.5f, 1f, 1.5f, 1f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 1f)
        anim2.fillAfter = true
        anim2.duration = 200
        v.startAnimation(anim2)
    }


    /* Interfaces */
    interface DetectListener {
        fun onDetect(detects: List<Detect>)
    }
}
