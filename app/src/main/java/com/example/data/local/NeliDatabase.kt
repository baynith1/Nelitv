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

@Entity(tableName = "downloaded_items")
data class DownloadedItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // "movie" or "series"
    val posterUrl: String,
    val backdropUrl: String,
    val streamUrl: String,
    val genre: String,
    val duration: String,
    val rating: String,
    val fileSizeLabel: String = "740 MB",
    val localFilePath: String = "",
    val downloadStatus: String = "COMPLETED", // "DOWNLOADING", "COMPLETED", "PAUSED_ERROR"
    val progressPercent: Int = 100,
    val timestamp: Long = System.currentTimeMillis()
) {
    val downloadedAt: Long
        get() = timestamp
}

@Entity(tableName = "watchlist_items")
data class WatchlistItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // "movie", "series", or "live"
    val posterUrl: String,
    val streamUrl: String,
    val genre: String,
    val duration: String,
    val rating: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "firebase_config")
data class FirebaseConfigEntity(
    @PrimaryKey val configId: Int = 1,
    val databaseUrl: String = "https://neliplay-default-rtdb.firebaseio.com",
    val projectId: String = "neliplay",
    val apiKey: String = "",
    val networkMode: String = "AUTO_ADAPTIVE", // "AUTO_ADAPTIVE", "LOW_DATA", "HIGH_HD"
    val allowMobileData: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val uid: String,
    val realName: String,
    val email: String,
    val passwordHash: String,
    val idToken: String = "",
    val refreshToken: String = "",
    val isLoggedIn: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

@Dao
interface NeliMediaDao {
    @Query("SELECT * FROM downloaded_items ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadedItemEntity>>

    @Query("SELECT * FROM downloaded_items WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadedItemEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_items WHERE id = :id)")
    suspend fun isDownloaded(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownloadIgnoreDuplicate(item: DownloadedItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: DownloadedItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultDownloads(items: List<DownloadedItemEntity>)

    @Query("DELETE FROM downloaded_items WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("SELECT COUNT(*) FROM downloaded_items")
    suspend fun getDownloadsCount(): Int

    @Query("SELECT * FROM watchlist_items ORDER BY timestamp DESC")
    fun getAllWatchlist(): Flow<List<WatchlistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist_items WHERE id = :id")
    suspend fun deleteWatchlistById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE id = :id)")
    suspend fun isInWatchlist(id: String): Boolean

    @Query("SELECT * FROM firebase_config WHERE configId = 1 LIMIT 1")
    fun getFirebaseConfig(): Flow<FirebaseConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFirebaseConfig(config: FirebaseConfigEntity)

    @Query("SELECT * FROM user_accounts WHERE isLoggedIn = 1 ORDER BY lastLoginAt DESC LIMIT 1")
    fun getActiveUser(): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE isLoggedIn = 1 ORDER BY lastLoginAt DESC LIMIT 1")
    suspend fun getActiveUserOnce(): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getAccountByEmail(email: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserAccount(account: UserAccountEntity)

    @Query("UPDATE user_accounts SET isLoggedIn = 0")
    suspend fun logoutAllUsers()

    @Query("UPDATE user_accounts SET isLoggedIn = 1, lastLoginAt = :lastLoginAt WHERE uid = :uid")
    suspend fun markUserLoggedIn(uid: String, lastLoginAt: Long = System.currentTimeMillis())
}

@Database(
    entities = [
        DownloadedItemEntity::class,
        WatchlistItemEntity::class,
        FirebaseConfigEntity::class,
        UserAccountEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NeliDatabase : RoomDatabase() {
    abstract fun mediaDao(): NeliMediaDao

    companion object {
        @Volatile
        private var INSTANCE: NeliDatabase? = null

        fun getInstance(context: Context): NeliDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NeliDatabase::class.java,
                    "neli_tv_media.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
