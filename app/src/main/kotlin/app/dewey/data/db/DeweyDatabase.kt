package app.dewey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentRow::class, ChunkRow::class, NoteRow::class],
    version = 4,
    exportSchema = true,
)
abstract class DeweyDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun noteDao(): NoteDao

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

        /**
         * Adds the `notes` table.
         *
         * Unlike [MIGRATION_1_2] and [MIGRATION_2_3] this is a fresh
         * `CREATE TABLE` rather than an `ALTER TABLE` — notes are new data with
         * nothing to preserve — but it is still a real migration and not a
         * destructive fallback, for the same reason those two are: throwing the
         * whole database away on upgrade would take the indexed library and its
         * classification work down with it, not just the (empty, at this point)
         * notes table.
         *
         * The SQL below has to match column-for-column what Room's own schema
         * export generates for [NoteRow] — column types, NOT NULL, the index
         * name, the foreign key clause — because Room diffs a real upgrade
         * against its exported schema, not against this migration's intent. A
         * mismatch here compiles fine and only fails the first time a real
         * device upgrades from version 3; check it against
         * app/schemas/app.dewey.data.db.DeweyDatabase/4.json once a build has
         * regenerated it. `internal` rather than private so a future migration
         * test can run it directly against `MigrationTestHelper` without also
         * asserting on 1→2 or 2→3, which already ship — no such test exists
         * yet here, and adding one needs a `sourceSets` entry bundling
         * `app/schemas` as test assets that this change does not make.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`billDocumentId` INTEGER, " +
                        "`pinned` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`billDocumentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_billDocumentId` ON `notes` (`billDocumentId`)"
                )
            }
        }

        fun open(context: Context): DeweyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DeweyDatabase::class.java,
                "dewey.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
