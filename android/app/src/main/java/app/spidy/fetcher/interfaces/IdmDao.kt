package app.spidy.fetcher.interfaces

import androidx.room.*
import app.spidy.fetcher.data.IdmSnapshot

@Dao
interface IdmDao {
    @Query("SELECT * FROM idmsnapshot")
    fun getSnapshots(): List<IdmSnapshot>

    @Query("SELECT * FROM idmsnapshot WHERE idmStatus = :status")
    fun getSnapshots(status: String): List<IdmSnapshot>

    @Query("SELECT * FROM idmsnapshot WHERE uId = :uId")
    fun getSnapshot(uId: String): IdmSnapshot

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSnapshot(snap: IdmSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateSnapshot(snap: IdmSnapshot)

    @Delete
    fun removeSnapshot(snap: IdmSnapshot)

    @Query("DELETE FROM idmsnapshot")
    fun clearAllSnapshots()
}