package com.fluxie.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryEntry::class, BookmarkEntry::class, DownloadEntry::class, BlockedDomain::class],
    version = 5,
    exportSchema = false
)
abstract class FluxieDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun blockedDomainDao(): BlockedDomainDao

    companion object {
        @Volatile
        private var INSTANCE: FluxieDatabase? = null

        /** v3 only adds the bookmarks table — history must survive the upgrade. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`url` TEXT, " +
                        "`isFolder` INTEGER NOT NULL, " +
                        "`parentId` INTEGER, " +
                        "`position` INTEGER NOT NULL, " +
                        "`favicon` BLOB, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`parentId`) REFERENCES `bookmarks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_parentId` ON `bookmarks` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
            }
        }

        /** v4 only adds the downloads table — history and bookmarks must survive the upgrade. */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`downloadId` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`mimeType` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloads_downloadId` " +
                        "ON `downloads` (`downloadId`)"
                )
            }
        }

        /** v5 only adds the blocked_domains table — everything else must survive the upgrade. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_domains` (" +
                        "`domain` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`domain`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_blocked_domains_category` " +
                        "ON `blocked_domains` (`category`)"
                )
            }
        }

        /** Closes the database and forgets it; the next [getInstance] opens a fresh one. */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): FluxieDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FluxieDatabase::class.java,
                    "fluxie_browser.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // ponytail: kept as the backstop for pre-v2 installs only; every
                    // future version bump needs its own Migration added above.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
