package app.spidy.fetcher.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import app.spidy.fetcher.R
import app.spidy.fetcher.adapters.PagerAdapter
import app.spidy.fetcher.data.IdmSnapshot
import app.spidy.fetcher.interfaces.FragmentListener
import app.spidy.fetcher.interfaces.ServiceListener
import app.spidy.fetcher.services.IdmService
import app.spidy.fetcher.utils.Ads
import app.spidy.hiper.Hiper
import app.spidy.idm.data.Detect
import app.spidy.idm.interfaces.IdmListener
import app.spidy.idm.utils.StringUtil
import app.spidy.kotlinutils.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    /* Fields */
    private lateinit var permission: Permission
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private lateinit var pagerAdapter: PagerAdapter
    private lateinit var tinyDB: TinyDB
    private lateinit var hiper: Hiper.Async
    private lateinit var navView: NavigationView

    private lateinit var newDownloadOverlay: FrameLayout
    private lateinit var newDownloadUrlView: EditText
    private lateinit var newDownloadUserAgentSpinner: Spinner
    private lateinit var newDownloadOpenInBrowserCheckBox: CheckBox

    var isBound = false
    var idmService: IdmService? = null
    var isLaunched = false
    var idmListener: IdmListener? = null
    private val resumeQueue = ArrayList<IdmSnapshot>()
    private val queue = ArrayList<Detect>()
    private var currentFragmentIndex: Int = 0


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
            idmService?.idmListener = idmListener
            isBound = true

            for (idmSnapshot in resumeQueue) idmService?.resume(idmSnapshot)
            resumeQueue.clear()

            for (detect in queue) idmService?.download(detect)
            queue.clear()
            (pagerAdapter.fragments[currentFragmentIndex] as? FragmentListener)?.onShow()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    /* Overrides */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tinyDB = TinyDB(this)

        if (!tinyDB.getBoolean("is_pro")) {
            Ads.initInterstitial(this)
            Ads.initReward(this)
            Ads.loadInterstitial()
            Ads.loadReward()

            findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())
        }

        isLaunched = false
        hiper = Hiper.getAsyncInstance()
        currentFragmentIndex = 0

        permission = Permission(this)

        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawerLayout)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        navView = findViewById(R.id.navView)


        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START, true)
        }
        navView.setNavigationItemSelectedListener(this)

        tabLayout.setupWithViewPager(viewPager)
        tabLayout.addTab(tabLayout.newTab())
        tabLayout.addTab(tabLayout.newTab())
        tabLayout.addTab(tabLayout.newTab())
        tabLayout.addTab(tabLayout.newTab())
        tabLayout.addTab(tabLayout.newTab())
        pagerAdapter = PagerAdapter(supportFragmentManager, tabLayout.tabCount, titles = listOf(
            getString(R.string.explorer),
            getString(R.string.progress),
            getString(R.string.failed),
            getString(R.string.paused),
            getString(R.string.completed)
        ))
        viewPager.adapter = pagerAdapter
        tabLayout.getTabAt(0)?.setIcon(R.drawable.ic_explorer)
        tabLayout.getTabAt(1)?.setIcon(R.drawable.ic_download)
        tabLayout.getTabAt(2)?.setIcon(R.drawable.ic_warning)
        tabLayout.getTabAt(3)?.setIcon(R.drawable.ic_pause)
        tabLayout.getTabAt(4)?.setIcon(R.drawable.ic_done)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.apply {
                    (pagerAdapter.fragments[position] as? FragmentListener)?.onHide()
                }
            }
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.apply {
                    currentFragmentIndex = position
                    try {
                        (pagerAdapter.fragments[position] as? FragmentListener)?.onShow()
                    } catch (e: Exception) {
                        thread {
                            Thread.sleep(1000)
                            runOnUiThread {
                                (pagerAdapter.fragments[position] as? FragmentListener)?.onShow()
                            }
                        }
                    }
                }
            }
        })
    }

    override fun onPause() {
        idmService?.idmListener = null
        isLaunched = false
        unbind()
        super.onPause()
    }

    override fun onResume() {
        if (IdmService.isRunning) {
            bind()
        }
        super.onResume()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permission.execute(requestCode, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuOpenBrowser -> {
                startActivity(Intent(this, BrowserActivity::class.java))
                true
            }
            R.id.menuFeedback -> {
                val uri = Uri.parse("market://details?id=$packageName");
                val goToMarket = Intent(Intent.ACTION_VIEW, uri)
                goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                try {
                    startActivity(goToMarket)
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("http://play.google.com/store/apps/details?id=$packageName"))
                    )
                }
                true
            }
            R.id.menuSettings -> {
                showSettingsDialog()
                true
            }
            R.id.menuNewDownload -> {
                val newDownloadDialog = createNewDownloadDialog()
                newDownloadDialog.show()
                newDownloadDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val url = newDownloadUrlView.text.toString().trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        toast("Invalid URL \uD83D\uDE25")
                    } else {
                        newDownloadOverlay.visibility = View.VISIBLE
                        newDownloadDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                        hiper.head(url, headers = hashMapOf("range" to "bytes=0-")).then { response ->
                            newDownloadDialog.dismiss()
                            val mime = response.headers.get("content-type").toString()
                            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                                val detect = Detect(
                                    data = hashMapOf("url" to url, "title" to "", "filename" to getFileName(url, mime)),
                                    cookies = hashMapOf(),
                                    requestHeaders = hashMapOf(),
                                    responseHeaders = response.headers.toHashMap(),
                                    type = if (mime.startsWith("video/")) Detect.TYPE_VIDEO else Detect.TYPE_AUDIO,
                                    isResumable = response.statusCode == 206
                                )
                                runOnUiThread {
                                    download(detect)
                                }
                            } else {
                                val intent = Intent(this, BrowserActivity::class.java)
                                intent.putExtra("url", url)
                                startActivity(intent)
                            }
                        }.catch {
                            debug("Err: $it")
                            newDownloadDialog.dismiss()
                            val intent = Intent(this, BrowserActivity::class.java)
                            intent.putExtra("url", url)
                            startActivity(intent)
                        }
                    }
                }
                newDownloadDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    newDownloadDialog.dismiss()
                }
                return true
            }
            else -> false
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuBugReport -> {
                try {
                    val emailBuilder = StringBuilder("mailto:" + Uri.encode("mytellee@gmail.com"))
                    val operator = '?'
                    emailBuilder.append(operator + "subject=" + Uri.encode("Bug Report"));
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(emailBuilder.toString()))
                    startActivity(intent)
                } catch (e: Exception) {
                    toast("Unable to find an email client.")
                }
            }
            R.id.menuMoreApps -> {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/dev?id=7688311086018430980")
                        )
                    )
                } catch (anfe: ActivityNotFoundException) {
                    toast("Unable to open Googleplay")
                }
            }
            R.id.menuFeedback -> {
                val uri = Uri.parse("market://details?id=$packageName");
                val goToMarket = Intent(Intent.ACTION_VIEW, uri)
                goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                try {
                    startActivity(goToMarket);
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("http://play.google.com/store/apps/details?id=$packageName"))
                    )
                }
            }
            R.id.menuShare -> {
                ignore {
                    val shareIntent = Intent(Intent.ACTION_SEND);
                    shareIntent.type = "text/plain";
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GIF and image meme creator with templates.");
                    var shareMessage = "\nShow your humor with images and GIFs\n\n";
                    shareMessage = shareMessage + "https://play.google.com/store/apps/details?id=" + packageName +"\n\n";
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
                    startActivity(Intent.createChooser(shareIntent, "Share with"));
                }
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun createNewDownloadDialog(): AlertDialog {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle("New Download")
        builder.setIcon(R.drawable.ic_download)
        val viewGroup: ViewGroup? = null
        val view = LayoutInflater.from(this).inflate(R.layout.layout_new_download_dialog, viewGroup, false)

        newDownloadOverlay = view.findViewById(R.id.overlayView)
        newDownloadUrlView = view.findViewById(R.id.urlView)
        newDownloadUserAgentSpinner = view.findViewById(R.id.userAgentSpinner)
        newDownloadOpenInBrowserCheckBox = view.findViewById(R.id.openInBrowserCheckBox)

        builder.setView(view)
        builder.setPositiveButton("Fetch") { _, _ -> }
        builder.setNegativeButton("Cancel") { _, _ -> }
        return builder.create()
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

    fun resume(idmSnapshot: IdmSnapshot) {
        if (!tinyDB.getBoolean("is_pro")) {
            Ads.showInterstitial()
            Ads.loadInterstitial()
        }

        if (!IdmService.isRunning) {
            startIdmService()
        }

        if (isBound) {
            idmService?.resume(idmSnapshot)
        } else {
            resumeQueue.add(idmSnapshot)
            bind()
        }
    }

    private fun download(detect: Detect) {
        permission.request(Manifest.permission.WRITE_EXTERNAL_STORAGE, listener = object : Permission.Listener {
            override fun onGranted() {
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
            override fun onRejected() {}
        })
    }


    @SuppressLint("SetTextI18n")
    private fun showSettingsDialog() {
        val viewGroup: ViewGroup? = null
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.layout_settings_dialog, viewGroup)

        val downloadLimitView: TextView = view.findViewById(R.id.downloadLimitView)
        val downloadLimitSeekBar: SeekBar = view.findViewById(R.id.downloadLimitSeekBar)
        val numOfThreadView: TextView = view.findViewById(R.id.numOfThreadView)
        val numOfThreadSeekBar: SeekBar = view.findViewById(R.id.numOfThreadSeekBar)

        val maxDownloadSize = tinyDB.getInt("max_fetch_size", 4)
        val maxThreadSize = tinyDB.getInt("max_thread_size", 10)
        downloadLimitView.text = "Download Limit for Every Cycle • ${maxDownloadSize}MB"
        numOfThreadView.text = "Number of Thread(s) • $maxThreadSize"
        downloadLimitSeekBar.progress = maxDownloadSize - 1
        numOfThreadSeekBar.progress = maxThreadSize - 1

        downloadLimitSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) downloadLimitView.text = "Download Limit for Every Cycle • ${progress + 1}MB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        numOfThreadSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) numOfThreadView.text = "Number of Thread(s) • ${progress + 1}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setPositiveButton(getString(R.string.close), null)
            .create()
        dialog.setCancelable(false)

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Apply") { _, _ ->
            tinyDB.putInt("max_fetch_size", downloadLimitSeekBar.progress + 1)
            tinyDB.putInt("max_thread_size", numOfThreadSeekBar.progress + 1)
        }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") {d, _ ->
            d.dismiss()
        }

        dialog.setView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.show()
    }

    private fun getFileName(url: String, mimetype: String?): String {
        val name = url.split("?")[0].split("#")[0].split("/").last().split(".")[0]
        val singleton = MimeTypeMap.getSingleton()
        val ext = singleton.getExtensionFromMimeType(mimetype)
        debug(ext)
        if (ext != null) {
            return "${name}_${StringUtil.randomUUID()}.$ext"
        } else if (mimetype.toString().startsWith("audio/") || mimetype.toString().startsWith("audio/")) {
            return "${name}_${StringUtil.randomUUID()}.${mimetype.toString().split("/").last()}"
        }
        return "${name}_${StringUtil.randomUUID()}"
    }
}
