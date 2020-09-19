package app.spidy.fetcher.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import app.spidy.fetcher.fragments.*
import app.spidy.fetcher.interfaces.FragmentListener
import app.spidy.kotlinutils.debug

class PagerAdapter(
    private val fragmentManager: FragmentManager,
    private val tabCount: Int,
    private val titles: List<String>
): FragmentStatePagerAdapter(fragmentManager, tabCount) {
    val fragments = HashMap<Int, Fragment>()

    override fun getPageTitle(position: Int): CharSequence? {
        return titles[position]
    }

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> {
                fragments[position] = ExplorerFragment()
                fragments[position]!!
            }
            1 -> {
                fragments[position] = DownloadingFragment()
                fragments[position]!!
            }
            2 -> {
                fragments[position] = FailedFragment()
                fragments[position]!!
            }
            3 -> {
                fragments[position] = PausedFragment()
                fragments[position]!!
            }
            4 -> {
                fragments[position] = CompletedFragment()
                fragments[position]!!
            }
            else -> ExplorerFragment()
        }
    }

    override fun getCount(): Int {
        return tabCount
    }
}