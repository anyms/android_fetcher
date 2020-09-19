package app.spidy.fetcher

import app.spidy.kotlinutils.debug
import java.net.URL

class Blacklist {
    private val domains = arrayListOf(
        "youtube.com"
    )

    fun isBlocked(url: String): Boolean {
        val uri = URL(url)
        val nodes = uri.host.split(".")
        val domain = "${nodes[nodes.lastIndex - 1]}.${nodes.last()}"
        return domains.contains(domain)
    }
}