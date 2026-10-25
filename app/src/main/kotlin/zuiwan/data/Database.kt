package zuiwan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val migration1to2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN setVideoId TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recognized (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                artwork TEXT NOT NULL,
                genre TEXT,
                releaseDate TEXT,
                videoId TEXT,
                recognizedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayEventEntity::class,
        SearchHistoryEntity::class,
        LyricsEntity::class,
        FormatEntity::class,
        QueueEntity::class,
        RecognizedEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MusicDb : RoomDatabase() {
    abstract fun dao(): MusicDao

    companion object {
        @Volatile
        private var instance: MusicDb? = null

        fun open(context: Context): MusicDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDb::class.java,
                    "zuiwan.db",
                ).addMigrations(migration1to2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
    }
}
