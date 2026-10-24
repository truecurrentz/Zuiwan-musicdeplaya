package zuiwan.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val db: MusicDb,
) {
    suspend fun exportbackup(uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                val dbFile = context.getDatabasePath("zuiwan.db")
                val prefsFile = File(context.filesDir, "datastore/zuiwan_prefs.preferences_pb")

                context.contentResolver.openOutputStream(uri)?.use { rawOut ->
                    ZipOutputStream(rawOut).use { zip ->
                        if (dbFile.exists()) {
                            zip.putNextEntry(ZipEntry("zuiwan.db"))
                            dbFile.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        if (prefsFile.exists()) {
                            zip.putNextEntry(ZipEntry("zuiwan_prefs.preferences_pb"))
                            prefsFile.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Cannot open output stream")
            }
        }

    suspend fun restorebackup(uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dbFile = context.getDatabasePath("zuiwan.db")
                val prefsFile = File(context.filesDir, "datastore/zuiwan_prefs.preferences_pb")
                prefsFile.parentFile?.mkdirs()
                db.close()

                context.contentResolver.openInputStream(uri)?.use { rawIn ->
                    ZipInputStream(rawIn).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                "zuiwan.db" -> {
                                    File("${dbFile.absolutePath}-wal").delete()
                                    File("${dbFile.absolutePath}-shm").delete()
                                    dbFile.outputStream().use { zip.copyTo(it) }
                                }
                                "zuiwan_prefs.preferences_pb" -> {
                                    prefsFile.outputStream().use { zip.copyTo(it) }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: error("Cannot open backup archive")
            }
        }
}
