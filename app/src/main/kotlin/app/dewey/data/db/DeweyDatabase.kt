package app.dewey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentRow::class, ChunkRow::class],
    version = 1,
    exportSchema = true,
)
abstract class DeweyDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao

    companion object {
        fun open(context: Context): DeweyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DeweyDatabase::class.java,
                "dewey.db",
            ).build()
    }
}
