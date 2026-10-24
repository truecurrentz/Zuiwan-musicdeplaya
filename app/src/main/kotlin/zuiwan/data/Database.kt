package zuiwan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongRow::class,
        AlbumRow::class,
        ArtistRow::class,
        PlaylistRow::class,
        PlaylistTrackRow::class,
        HistoryRow::class,
        QueryRow::class,
        LyricRow::class,
        FormatRow::class,
    ],
    version = 1,
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
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
    }
}
