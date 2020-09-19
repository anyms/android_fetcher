package app.spidy.fetcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.spidy.fetcher.converters.StringMapConverter

@Entity(tableName = "idmsnapshot")
data class IdmSnapshot(
    @PrimaryKey
    val uId: String,
    var fileName: String,
    var downloadedSize: Long,
    var contentSize: Long,
    @TypeConverters(StringMapConverter::class)
    var requestHeaders: Map<String, String>,
    @TypeConverters(StringMapConverter::class)
    var responseHeaders: Map<String, String>,
    @TypeConverters(StringMapConverter::class)
    var cookies: Map<String, String>,
    var isResumable: Boolean,
    var type: String,
    @TypeConverters(StringMapConverter::class)
    var data: Map<String, String>,
    var speed: String,
    var remainingTime: String,
    var state: String,
    var idmStatus: String,
    var initStatus: String = ""
) {
    companion object {
        const val STATUS_PAUSED = "app.spidy.fetcher.data.STATUS_PAUSED"
        const val STATUS_FAILED = "app.spidy.fetcher.data.STATUS_FAILED"
        const val STATUS_COMPLETED = "app.spidy.fetcher.data.STATUS_COMPLETED"
        const val STATUS_PROGRESS = "app.spidy.fetcher.data.STATUS_PROGRESS"
        const val STATUS_FINALIZING = "app.spidy.fetcher.data.STATUS_FINALIZING"
    }
}