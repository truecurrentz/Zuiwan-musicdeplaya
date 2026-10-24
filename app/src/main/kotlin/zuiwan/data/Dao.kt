package zuiwan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun gettrack(id: String): TrackEntity?

    @Upsert
    suspend fun puttrack(entity: TrackEntity)

    @Query("SELECT * FROM tracks WHERE liked = 1 ORDER BY COALESCE(likedAt, 0) DESC")
    fun likedtracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET liked = :liked, likedAt = :at WHERE id = :id")
    suspend fun setliked(id: String, liked: Boolean, at: Long?)

    @Query("SELECT * FROM tracks WHERE downloaded = 1 ORDER BY COALESCE(downloadedAt, 0) DESC")
    fun downloadedtracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET downloaded = :downloaded, downloadedAt = :at WHERE id = :id")
    suspend fun setdownloaded(id: String, downloaded: Boolean, at: Long?)

    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 80")
    fun recenttracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY totalPlayTimeMs DESC, playCount DESC LIMIT 50")
    fun mostplayed(): Flow<List<TrackEntity>>

    @Query(
        """
        UPDATE tracks
        SET playCount = playCount + 1,
            totalPlayTimeMs = totalPlayTimeMs + :playedMs,
            lastPlayedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun recordplay(id: String, playedMs: Long, now: Long)

    @Query("UPDATE tracks SET lastPlayedAt = NULL")
    suspend fun clearhistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addevent(event: PlayEventEntity)

    @Query("DELETE FROM play_events")
    suspend fun clearevents()

    @Query("SELECT SUM(playTimeMs) FROM play_events WHERE timestamp >= :since")
    suspend fun timesince(since: Long): Long?

    @Query("SELECT COUNT(*) FROM play_events WHERE timestamp >= :since")
    suspend fun countsince(since: Long): Int

    @Query(
        """
        SELECT t.*, COUNT(e.id) AS plays, COALESCE(SUM(e.playTimeMs), 0) AS playTime
        FROM play_events e
        INNER JOIN tracks t ON t.id = e.trackId
        WHERE e.timestamp >= :since
        GROUP BY t.id
        ORDER BY playTime DESC
        LIMIT :limit
        """,
    )
    suspend fun toptracks(since: Long, limit: Int): List<TopTrackRow>

    @Query("SELECT * FROM albums WHERE saved = 1 ORDER BY savedAt DESC")
    fun savedalbums(): Flow<List<AlbumEntity>>

    @Upsert
    suspend fun putalbum(entity: AlbumEntity)

    @Query("SELECT * FROM artists WHERE saved = 1 ORDER BY savedAt DESC")
    fun savedartists(): Flow<List<ArtistEntity>>

    @Upsert
    suspend fun putartist(entity: ArtistEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun allplaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getplaylist(id: String): PlaylistEntity?

    @Upsert
    suspend fun putplaylist(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteplaylist(id: String)

    @Query("SELECT COUNT(*) FROM playlist_tracks")
    fun linkcount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun playlistcount(playlistId: String): Int

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxpos(playlistId: String): Int?

    @Upsert
    suspend fun linktrack(entity: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun unlinktrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun unlinkall(playlistId: String)

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt
        INNER JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """,
    )
    suspend fun playlisttracks(playlistId: String): List<TrackEntity>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 25")
    fun recentsearches(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun putsearch(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deletesearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearsearches()

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getlyrics(trackId: String): LyricsEntity?

    @Upsert
    suspend fun putlyrics(entity: LyricsEntity)

    @Query("SELECT * FROM formats WHERE id = :id LIMIT 1")
    suspend fun getformat(id: String): FormatEntity?

    @Upsert
    suspend fun putformat(entity: FormatEntity)

    @Query("SELECT * FROM saved_queue ORDER BY slot ASC")
    suspend fun loadqueue(): List<QueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savequeue(rows: List<QueueEntity>)

    @Query("DELETE FROM saved_queue")
    suspend fun clearqueue()
}
