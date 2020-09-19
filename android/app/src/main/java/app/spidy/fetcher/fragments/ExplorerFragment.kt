package app.spidy.fetcher.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.room.Room
import app.spidy.fetcher.R
import app.spidy.fetcher.activities.MainActivity
import app.spidy.fetcher.adapters.SnapshotAdapter
import app.spidy.fetcher.data.IdmSnapshot
import app.spidy.fetcher.databases.IdmDatabase
import app.spidy.fetcher.interfaces.FragmentListener
import app.spidy.idm.data.Snapshot
import app.spidy.idm.interfaces.IdmListener
import app.spidy.kotlinutils.debug
import app.spidy.kotlinutils.onUiThread
import java.lang.Exception
import kotlin.concurrent.thread

class ExplorerFragment : Fragment(), FragmentListener, IdmListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var nothingFound: TextView
    private lateinit var adapter: SnapshotAdapter
    private lateinit var idmDatabase: IdmDatabase

    private val snapshots = ArrayList<IdmSnapshot>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_explorer, container, false)
        idmDatabase = Room.databaseBuilder(requireContext(), IdmDatabase::class.java, "IdmDatabase")
            .fallbackToDestructiveMigration().build()

        adapter = SnapshotAdapter(context, snapshots, pause = { uId ->
            pause(uId)
        }, resume = { snapshot ->
            resume(snapshot)
        }, updateNothingView = {
            updateNothingView()
        })
        recyclerView = v.findViewById(R.id.recyclerView)
        nothingFound = v.findViewById(R.id.nothingFound)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        (activity as? MainActivity)?.apply {
            if (!isLaunched) {
                if (isBound) {
                    idmService?.idmListener = this@ExplorerFragment
                } else {
                    idmListener = this@ExplorerFragment
                }
                isLaunched = true
            }
        }
        return v
    }

    override fun onResume() {
        updateRecyclerView()
        super.onResume()
    }

    override fun onShow() {
        thread {
            Thread.sleep(500)
            activity?.runOnUiThread { updateRecyclerView() }
        }

        (activity as? MainActivity)?.apply {
            if (isBound) {
                idmService?.idmListener = this@ExplorerFragment
            } else {
                idmListener = this@ExplorerFragment
            }
        }
    }

    override fun onHide() {
        (activity as? MainActivity)?.apply {
            if (isBound) {
                idmService?.idmListener = null
            } else {
                idmListener = null
            }
        }
    }

    override fun onComplete(snapshot: Snapshot) {
        updateSnapshot(snapshot, IdmSnapshot.STATUS_PROGRESS)
    }

    override fun onCopied(snapshot: Snapshot) {
        updateSnapshot(snapshot, IdmSnapshot.STATUS_COMPLETED)
    }

    override fun onCopy(snapshot: Snapshot, progress: Int) {
        updateFinalizing(snapshot, "($progress%) Finalizing...")
    }

    override fun onCopyError(e: Exception, snapshot: Snapshot) {
        updateSnapshot(snapshot.uId, IdmSnapshot.STATUS_FAILED)
    }

    override fun onError(e: Exception, uId: String) {
        updateSnapshot(uId, IdmSnapshot.STATUS_FAILED)
    }

    override fun onFail(snapshot: Snapshot) {
        updateSnapshot(snapshot, IdmSnapshot.STATUS_FAILED)
    }

    override fun onInit(uId: String, message: String) {
        val index = findSnapshotIndex(uId)
        if (index != null) {
            snapshots[index].apply {
                initStatus = message
                type = ""
                speed = "-"
                remainingTime = "-"
                idmStatus = IdmSnapshot.STATUS_PROGRESS
            }
            adapter.notifyItemChanged(index)
        }
    }

    override fun onPause(snapshot: Snapshot) {
        updateSnapshot(snapshot, IdmSnapshot.STATUS_PAUSED)
    }

    override fun onProgress(snapshot: Snapshot) {
        updateSnapshot(snapshot, IdmSnapshot.STATUS_PROGRESS)
    }

    override fun onStart(snapshot: Snapshot) {
        updateSnapshot(snapshot, IdmSnapshot.STATUS_PROGRESS)
    }

    /* Methods */

    private fun updateNothingView() {
        nothingFound.visibility = if (snapshots.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateRecyclerView() {
        snapshots.clear()
        thread {
            val snaps = idmDatabase.idmDao().getSnapshots()
            for (snap in snaps) snapshots.add(snap)
            onUiThread {
                (activity as? MainActivity)?.idmService?.apply {
                    for (id in uIds) {
                        val i = findSnapshotIndex(id)
                        if (i == null) {
                            snapshots.add(getBlankSnapshot(id))
                        }
                    }
                }
                snapshots.reverse()
                adapter.notifyDataSetChanged()
                updateNothingView()
            }
        }
    }

    private fun updateSnapshot(snapshot: Snapshot, status: String) {
        val index = findSnapshotIndex(snapshot.uId)
        if (index != null) {
            snapshots[index].apply {
                fileName = snapshot.fileName
                downloadedSize = snapshot.downloadedSize
                contentSize = snapshot.contentSize
                requestHeaders = snapshot.requestHeaders as Map<String, String>
                responseHeaders = snapshot.responseHeaders
                cookies = snapshot.cookies
                isResumable = snapshot.isResumable
                type = snapshot.type
                data = snapshot.data
                speed = snapshot.speed
                remainingTime = snapshot.remainingTime
                idmStatus = status
            }

            adapter.notifyItemChanged(index)
        }
    }

    private fun updateFinalizing(snapshot: Snapshot, status: String) {
        val index = findSnapshotIndex(snapshot.uId)
        if (index != null) {
            snapshots[index].apply {
                initStatus = status
                idmStatus = IdmSnapshot.STATUS_FINALIZING
                type = ""
            }
            adapter.notifyItemChanged(index)
        }
    }

    private fun updateSnapshot(uId: String, status: String) {
        val index = findSnapshotIndex(uId)
        if (index != null) {
            snapshots[index].apply {
                idmStatus = status
            }

            adapter.notifyItemChanged(index)
        }
    }

    private fun findSnapshotIndex(uId: String): Int? {
        var index: Int? = null
        for (i in snapshots.indices) {
            if (snapshots[i].uId == uId) {
                index = i
                break
            }
        }
        return index
    }

    private fun getBlankSnapshot(uId: String): IdmSnapshot {
        return IdmSnapshot(
            uId = uId,
            fileName = "-",
            downloadedSize = 0,
            contentSize = 0,
            requestHeaders = hashMapOf(),
            responseHeaders = hashMapOf(),
            cookies = hashMapOf(),
            isResumable = false,
            type = "",
            data = hashMapOf(),
            speed = "-",
            remainingTime = "-",
            state = Snapshot.STATE_PROGRESS,
            idmStatus = IdmSnapshot.STATUS_PROGRESS,
            initStatus = ""
        )
    }

    private fun pause(uId: String) {
        (activity as? MainActivity)?.idmService?.pause(uId)
        thread {
            Thread.sleep(1000)
            onUiThread {
                updateRecyclerView()
            }
        }
    }

    private fun resume(idmSnapshot: IdmSnapshot) {
        (activity as? MainActivity)?.apply {
            if (isBound) {
                idmService?.idmListener = this@ExplorerFragment
            } else {
                idmListener = this@ExplorerFragment
            }
            resume(idmSnapshot)
        }
    }
}
