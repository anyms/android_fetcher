package app.spidy.fetcher

import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import app.spidy.fetcher.activities.BrowserActivity
import app.spidy.fetcher.utils.UrlValidator
import app.spidy.idm.Detector
import app.spidy.idm.data.Detect
import app.spidy.idm.interfaces.DetectListener
import app.spidy.kookaburra.controllers.Browser
import app.spidy.kotlinutils.debug
import app.spidy.kotlinutils.ignore
import app.spidy.kotlinutils.onUiThread

class BrowserListener(
    private val listener: BrowserActivity.DetectListener,
    private val toLoadUrl: String?,
    private val download: (Detect) -> Unit
) : Browser.Listener {
    private var isFirstLaunch = true
    private val blacklist = Blacklist()
    private var currentUrl: String = ""

    private val detectListener = object : DetectListener {
        override fun onDetect(detect: Detect) {
            if (detects.containsKey(currentTabId)) {
                if (detect.type == Detect.TYPE_GOOGLE) {
                    if (detect.data["audio"] != null) {
                        val hasIt = alreadyDetected(detect, "audio")
                        if (!hasIt) {
                            detects[currentTabId]!!.add(detect)
                        }
                    }
                } else if (detect.type == Detect.TYPE_FACEBOOK) {
                    val hasIt = alreadyDetected(detect, "id")
                    if (!hasIt) {
                        detects[currentTabId]!!.add(detect)
                    }
                } else {
                    val hasIt = alreadyDetected(detect, "url")
                    if (!hasIt) {
                        detects[currentTabId]!!.add(detect)
                    }
                }
                listener.onDetect(getDetects())
            }
        }
    }
    private val detector = Detector(detectListener)
    private val urlValidator = UrlValidator()
    private val cookieManager = CookieManager.getInstance()
    private var cookies = HashMap<String, String>()
    private var pageUrl: String? = null
    private val detects = HashMap<String, ArrayList<Detect>>()
    private var currentTabId = ""


    private fun alreadyDetected(detect: Detect, key: String): Boolean {
        var hasIt = false
        for (d in detects[currentTabId]!!) {
            if (d.data[key]!! == detect.data[key]) {
                hasIt = true
            }
        }
        return hasIt
    }

    fun getDetects(): List<Detect> {
        val ds = ArrayList<Detect>()
        if (detects[currentTabId] != null) {
            for (d in detects[currentTabId]!!) {
                ds.add(d)
            }
        }
        return ds
    }

    override fun shouldInterceptRequest(view: WebView, activity: FragmentActivity?, url: String, request: WebResourceRequest?) {
        if (urlValidator.validate(url)) {
            ignore {
                if (currentUrl == "") {
                    onUiThread {
                        currentUrl = view.url
                    }
                }
                if (!blacklist.isBlocked(currentUrl) && currentUrl != "") {
                    detector.detect(url, request?.requestHeaders, cookies, pageUrl, view, activity)
                }
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favIcon: Bitmap?) {
        if (isFirstLaunch && toLoadUrl != null) {
            view.loadUrl(toLoadUrl)
            isFirstLaunch = false
        }
        detects[currentTabId]?.clear()
        listener.onDetect(getDetects())
        currentUrl = view.url
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (url.contains("tamilian.net/")) {
            view.evaluateJavascript("""
                    (function() {
                        var parent = document.querySelector("input[name='id']").parentNode;
                        parent.removeAttribute("target");
                        var button = document.createElement("input");
                        button.setAttribute("type", "submit");
                        button.value = "PLAY THE VIDEO";
                        parent.appendChild(button);
                    })();
                """.trimIndent()){}
        }
    }

    override fun onNewTab(tabId: String) {
        detects[tabId] = ArrayList()
        currentTabId = tabId
    }

    override fun onSwitchTab(fromTabId: String, toTabId: String) {
        currentTabId = toTabId
        listener.onDetect(getDetects())
        currentUrl = ""
    }

    override fun onCloseTab(tabId: String) {
        detects.remove(tabId)
    }

    override fun onRestoreTab(tabId: String, isActive: Boolean) {
        detects[tabId] = ArrayList()
        if (isActive) currentTabId = tabId
    }

    override fun onNewUrl(view: WebView, url: String) {
        pageUrl = url
        cookies = HashMap()
        val cooks = cookieManager.getCookie(url)?.split(";")

        if (cooks != null) {
            for (cook in cooks) {
                val nodes = cook.trim().split("=")
                cookies[nodes[0].trim()] = nodes[1].trim()
            }
        }
    }

    override fun onNewDownload(
        view: WebView,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        contentLength: Long
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val detect = Detect(
            data = hashMapOf("url" to url, "filename" to fileName, "title" to view.title),
            cookies = cookies,
            requestHeaders = hashMapOf("user-agent" to userAgent),
            responseHeaders = hashMapOf(),
            type = Detect.TYPE_FILE,
            isResumable = false
        )
        download(detect)
    }
}