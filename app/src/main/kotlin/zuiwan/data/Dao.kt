package zuiwan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class HistoryJoin(
    val entryId: Long,
    val playedAt: Long,
    val listenMs: Long,
    val id: String,
    val title: String,
    val artistsJson: String,
    val albumId: String?,
    val albumName: String?,
    val duration: Int,
    val artwork: String,
    val explicit: Boolean,
    val video: Boolean,
    val liked: Boolean,
    val downloaded: Boolean,
) {
    fun torow(): SongRow =
        SongRow(
            id = id,
            title = title,
            artistsJson = artistsJson,
            albumId = albumId,
            albumName = albumName,
            duration = duration,
            artwork = artwork,
            explicit = explicit,
            video = video,
            liked = liked,
            downloaded = downloaded,
        )
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun findsong(id: String): SongRow?

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    fun observesong(id: String): Flow<SongRow?>

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun likedtracks(): Flow<List<SongRow>>

    @Query("SELECT * FROM songs WHERE downloaded = 1 ORDER BY downloadedAt DESC")
    fun downloadedtracks(): Flow<List<SongRow>>

    @Query("SELECT * FROM songs WHERE cachedAt > 0 ORDER BY cachedAt DESC")
    fun cachedtracks(): Flow<List<SongRow>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, totalListenMs DESC LIMIT 100")
    fun toptracks(): Flow<List<SongRow>>

    @Query("SELECT * FROM songs ORDER BY updatedAt DESC")
    fun allsongs(): Flow<List<SongRow>>

    @Query("SELECT * FROM songs")
    suspend fun snapshotsongs(): List<SongRow>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :q || '%' OR artistsJson LIKE '%' || :q || '%' OR albumName LIKE '%' || :q || '%' ORDER BY liked DESC, playCount DESC LIMIT 80")
    suspend fun searchsongs(q: String): List<SongRow>

    @Upsert
    suspend fun upsertsong(row: SongRow)

    @Upsert
    suspend fun upsertsongs(rows: List<SongRow>)

    @Query("UPDATE songs SET liked = :liked, likedAt = :stamp, updatedAt = :stamp WHERE id = :id")
    suspend fun setliked(id: String, liked: Boolean, stamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET downloaded = :downloaded, downloadedAt = :stamp, updatedAt = :stamp WHERE id = :id")
    suspend fun setdownloaded(id: String, downloaded: Boolean, stamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET cachedAt = :stamp, updatedAt = :stamp WHERE id = :id")
    suspend fun setcached(id: String, stamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET cachedAt = 0")
    suspend fun clearcachedflags()

    @Query("UPDATE songs SET playCount = playCount + 1, totalListenMs = totalListenMs + :listenMs, updatedAt = :stamp WHERE id = :id")
    suspend fun bumpplay(id: String, listenMs: Long, stamp: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserthistory(entry: HistoryRow)

    @Query(
        """
        SELECT h.entryId, h.playedAt, h.listenMs,
               s.id, s.title, s.artistsJson, s.albumId, s.albumName,
               s.duration, s.artwork, s.explicit, s.video, s.liked, s.downloaded
        FROM history h
        INNER JOIN songs s ON s.id = h.trackId
        ORDER BY h.playedAt DESC
        LIMIT :limit
        """,
    )
    fun recenthistory(limit: Int = 150): Flow<List<HistoryJoin>>

    @Query(
        """
        SELECT h.entryId, h.playedAt, h.listenMs,
               s.id, s.title, s.artistsJson, s.albumId, s.albumName,
               s.duration, s.artwork, s.explicit, s.video, s.liked, s.downloaded
        FROM history h
        INNER JOIN songs s ON s.id = h.trackId
        WHERE h.playedAt >= :sinceMs
        ORDER BY h.playedAt DESC
        """,
    )
    suspend fun historysince(sinceMs: Long): List<HistoryJoin>

    @Query("SELECT * FROM history")
    suspend fun snapshothistory(): List<HistoryRow>

    @Query("DELETE FROM history WHERE entryId = :entryId")
    suspend fun removehistory(entryId: Long)

    @Query("DELETE FROM history")
    suspend fun clearhistory()

    @Query("SELECT * FROM albums WHERE saved = 1 ORDER BY savedAt DESC")
    fun savedalbums(): Flow<List<AlbumRow>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observealbum(id: String): Flow<AlbumRow?>

    @Query("SELECT * FROM albums")
    suspend fun snapshotalbums(): List<AlbumRow>

    @Upsert
    suspend fun upsertalbum(row: AlbumRow)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deletealbum(id: String)

    @Query("SELECT * FROM artists WHERE saved = 1 ORDER BY savedAt DESC")
    fun savedartists(): Flow<List<ArtistRow>>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    fun observeartist(id: String): Flow<ArtistRow?>

    @Query("SELECT * FROM artists")
    suspend fun snapshotartists(): List<ArtistRow>

    @Upsert
    suspend fun upsertartist(row: ArtistRow)

    @Query("DELETE FROM artists WHERE id = :id")
    suspend fun deleteartist(id: String)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun localplaylists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun observeplaylist(id: String): Flow<PlaylistRow?>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun findplaylist(id: String): PlaylistRow?

    @Query("SELECT * FROM playlists")
    suspend fun snapshotplaylists(): List<PlaylistRow>

    @Upsert
    suspend fun upsertplaylist(row: PlaylistRow)

    @Query("UPDATE playlists SET title = :title, updatedAt = :stamp WHERE id = :id")
    suspend fun renameplaylist(id: String, title: String, stamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteplaylistrow(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearplaylisttracks(playlistId: String)

    @Transaction
    suspend fun dropplaylist(id: String) {
        clearplaylisttracks(id)
        deleteplaylistrow(id)
    }

    @Query(
        """
        SELECT s.* FROM playlist_tracks pt
        INNER JOIN songs s ON s.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC, pt.addedAt ASC
        """,
    )
    fun playlisttracks(playlistId: String): Flow<List<SongRow>>

    @Query(
        """
        SELECT s.* FROM playlist_tracks pt
        INNER JOIN songs s ON s.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC, pt.addedAt ASC
        """,
    )
    suspend fun getplaylisttracks(playlistId: String): List<SongRow>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun countplaylisttracks(playlistId: String): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextposition(playlistId: String): Int

    @Query("SELECT * FROM playlist_tracks")
    suspend fun snapshotplaylisttracks(): List<PlaylistTrackRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertplaylisttrack(row: PlaylistTrackRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertplaylisttracks(rows: List<PlaylistTrackRow>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeplaylisttrack(playlistId: String, trackId: String)

    @Query("SELECT * FROM queries ORDER BY searchedAt DESC LIMIT 30")
    fun recentqueries(): Flow<List<QueryRow>>

    @Upsert
    suspend fun upsertquery(row: QueryRow)

    @Query("DELETE FROM queries WHERE query = :query")
    suspend fun deletequery(query: String)

    @Query("DELETE FROM queries")
    suspend fun clearqueries()

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun findlyrics(trackId: String): LyricRow?

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    fun observelyrics(trackId: String): Flow<LyricRow?>

    @Upsert
    suspend fun upsertlyrics(row: LyricRow)

    @Query("UPDATE lyrics SET offsetMs = :offsetMs WHERE trackId = :trackId")
    suspend fun setlyricsoffset(trackId: String, offsetMs: Long)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deletelyrics(trackId: String)

    @Query("SELECT * FROM formats WHERE id = :id LIMIT 1")
    suspend fun findformat(id: String): FormatRow?

    @Query("SELECT * FROM formats WHERE id = :id LIMIT 1")
    fun observeformat(id: String): Flow<FormatRow?>

    @Upsert
    suspend fun upsertformat(row: FormatRow)
}
