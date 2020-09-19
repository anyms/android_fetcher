package app.spidy.fetcher.databases

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.spidy.fetcher.converters.StringMapConverter
import app.spidy.fetcher.data.IdmSnapshot
import app.spidy.fetcher.interfaces.IdmDao

@Database(entities = [IdmSnapshot::class], version = 9002, exportSchema = false)
@TypeConverters(StringMapConverter::class)
abstract class IdmDatabase: RoomDatabase() {
    abstract fun idmDao(): IdmDao
}