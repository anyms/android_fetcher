package app.spidy.fetcher.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.spidy.fetcher.R
import app.spidy.idm.data.Detect

class DetectAdapter(
    private val context: Context,
    private val detects: List<Detect>,
    private val download: (Detect) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.layout_detect_item, parent, false)
        return MainHolder(v)
    }

    override fun getItemCount(): Int = detects.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mainHolder = holder as MainHolder

        mainHolder.fileNameView.text = detects[position].data["filename"]

        when (detects[position].type) {
            Detect.TYPE_FACEBOOK -> {
                mainHolder.fileUrlView.text = "https://www.facebook.com/watch?v=${detects[position].data["id"]}"
            }
            Detect.TYPE_GOOGLE -> {
                mainHolder.fileUrlView.text = detects[position].data["audio"]
            }
            else -> {
                mainHolder.fileUrlView.text = detects[position].data["url"]
            }
        }

        when (detects[position].type) {
            Detect.TYPE_AUDIO,
            Detect.TYPE_GOOGLE -> {
                mainHolder.fileImageView.setImageResource(R.drawable.ic_audio_file)
            }
            Detect.TYPE_VIDEO,
            Detect.TYPE_FACEBOOK -> {
                mainHolder.fileImageView.setImageResource(R.drawable.ic_video_file)
            }
            Detect.TYPE_STREAM -> {
                mainHolder.fileImageView.setImageResource(R.drawable.ic_stream_file)
            }
            else -> {
                mainHolder.fileImageView.setImageResource(R.drawable.ic_file)
            }
        }

        mainHolder.fileDownloadView.setOnClickListener {
            download(detects[position])
        }
    }


    inner class MainHolder(v: View): RecyclerView.ViewHolder(v) {
        val fileImageView: ImageView = v.findViewById(R.id.fileImageView)
        val fileNameView: TextView = v.findViewById(R.id.fileNameView)
        val fileUrlView: TextView = v.findViewById(R.id.fileUrlView)
        val fileDownloadView: ImageView = v.findViewById(R.id.fileDownloadView)
    }
}