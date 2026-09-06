package app.dewey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentRow::class, ChunkRow::class],
    version = 3,
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

        /**
         * Adds the extracted-fields columns (vendor, amount, currency, the two
         * dates).
         *
         * Same reasoning as MIGRATION_1_2: destructive would mean re-running OCR
         * over every scanned file in the library on upgrade, which is minutes of
         * work the user has already paid for once.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN vendor TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN amount REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN currency TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN issue_date INTEGER")
                db.execSQL("ALTER TABLE documents ADD COLUMN due_date INTEGER")
            }
        }

        fun open(context: Context): DeweyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DeweyDatabase::class.java,
                "dewey.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
