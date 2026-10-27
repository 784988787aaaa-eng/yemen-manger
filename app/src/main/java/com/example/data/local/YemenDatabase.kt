package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val pathOrUri: String,
    val title: String,
    val providerType: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "path_history")
data class PathHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paneId: String,
    val pathOrUri: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "operation_logs")
data class OperationLogEntity(
    @PrimaryKey val operationId: String,
    val operationType: String,
    val status: String,
    val summary: String,
    val errorCode: String?,
    val warningCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "editor_drafts")
data class EditorDraftEntity(
    @PrimaryKey val sourcePathOrUri: String,
    val workingCopyPath: String,
    val encoding: String,
    val contentPreview: String,
    val byteLength: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface YemenManagerDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE pathOrUri = :pathOrUri")
    suspend fun deleteBookmark(pathOrUri: String)

    @Query("SELECT * FROM path_history ORDER BY visitedAt DESC LIMIT 50")
    fun observeRecentPaths(): Flow<List<PathHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPathHistory(entry: PathHistoryEntity)

    @Query("DELETE FROM path_history")
    suspend fun clearPathHistory()

    @Query("SELECT * FROM operation_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeOperationLogs(): Flow<List<OperationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperationLog(log: OperationLogEntity)

    @Query("DELETE FROM operation_logs")
    suspend fun clearOperationLogs()

    @Query("SELECT * FROM editor_drafts ORDER BY updatedAt DESC")
    fun observeDrafts(): Flow<List<EditorDraftEntity>>

    @Query("SELECT * FROM editor_drafts WHERE sourcePathOrUri = :sourcePath LIMIT 1")
    suspend fun getDraftForSource(sourcePath: String): EditorDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: EditorDraftEntity)

    @Query("DELETE FROM editor_drafts WHERE sourcePathOrUri = :sourcePath")
    suspend fun deleteDraft(sourcePath: String)
}

@Database(
    entities = [
        BookmarkEntity::class,
        PathHistoryEntity::class,
        OperationLogEntity::class,
        EditorDraftEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class YemenDatabase : RoomDatabase() {
    abstract fun dao(): YemenManagerDao

    companion object {
        @Volatile
        private var INSTANCE: YemenDatabase? = null

        fun getInstance(context: Context): YemenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    YemenDatabase::class.java,
                    "yemen_manager_local.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
