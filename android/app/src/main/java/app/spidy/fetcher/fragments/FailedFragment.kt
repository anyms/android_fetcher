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
import app.spidy.kotlinutils.onUiThread
import java.lang.Exception
import kotlin.concurrent.thread

class FailedFragment : Fragment(), FragmentListener, IdmListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var nothingFound: TextView
    private lateinit var adapter: SnapshotAdapter
    private lateinit var idmDatabase: IdmDatabase

    private val snapshots = ArrayList<IdmSnapshot>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_failed, container, false)

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
                idmService?.idmListener = this@FailedFragment
            } else {
                idmListener = this@FailedFragment
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

    override fun onFail(snapshot: Snapshot) {
        updateRecyclerView()
    }

    override fun onComplete(snapshot: Snapshot) {}
    override fun onCopied(snapshot: Snapshot) {}
    override fun onCopy(snapshot: Snapshot, progress: Int) {}
    override fun onCopyError(e: Exception, snapshot: Snapshot) {}
    override fun onError(e: Exception, uId: String) {}
    override fun onInit(uId: String, message: String) {}
    override fun onPause(snapshot: Snapshot) {}
    override fun onProgress(snapshot: Snapshot) {}
    override fun onStart(snapshot: Snapshot) {}


    /* Methods */
    private fun updateNothingView() {
        nothingFound.visibility = if (snapshots.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateRecyclerView() {
        snapshots.clear()
        thread {
            val snaps = idmDatabase.idmDao().getSnapshots(IdmSnapshot.STATUS_FAILED)
            for (snap in snaps) snapshots.add(snap)

            onUiThread {
                snapshots.reverse()
                adapter.notifyDataSetChanged()
                updateNothingView()
            }
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
            state = "-",
            idmStatus = "-",
            initStatus = ""
        )
    }

    private fun pause(uId: String) {
        (activity as? MainActivity)?.idmService?.pause(uId)
    }
    private fun resume(idmSnapshot: IdmSnapshot) {
        (activity as? MainActivity)?.apply {
            if (isBound) {
                idmService?.idmListener = this@FailedFragment
            } else {
                idmListener = this@FailedFragment
            }
            resume(idmSnapshot)
        }
    }
}
