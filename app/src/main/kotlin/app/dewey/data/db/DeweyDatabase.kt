package app.dewey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentRow::class, ChunkRow::class],
    version = 2,
    exportSchema = true,
)
abstract class DeweyDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao

    companion object {

        /**
         * Adds the classification columns.
         *
         * A real migration rather than a destructive one: indexing a large
         * folder takes minutes and re-runs OCR over every scanned file, so
         * throwing the index away on upgrade is not a small inconvenience.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN review_reason TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN classify_margin REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN sorted_folder TEXT")
            }
        }

        fun open(context: Context): DeweyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DeweyDatabase::class.java,
                "dewey.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}
