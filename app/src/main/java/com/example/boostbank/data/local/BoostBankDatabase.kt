package com.example.boostbank.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "score_items")
data class ScoreItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val points: Int,
    val category: String,
    val imageUri: String? = null,
    val imageBiasX: Float = 0f,
    val imageBiasY: Float = 0f,
    val imageScale: Float = 1f,
    val isMilestone: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "score_logs")
data class ScoreLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val delta: Int,
    val note: String,
    val type: String,
    val afterScore: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "score_account")
data class ScoreAccountEntity(
    @PrimaryKey val id: Int = 1,
    val totalScore: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stat_trackers")
data class StatTrackerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val trackedSources: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface BoostBankDao {
    @Query("SELECT * FROM score_items WHERE category = :category AND isMilestone = 0 ORDER BY updatedAt DESC, id DESC")
    fun observeItems(category: String): Flow<List<ScoreItemEntity>>

    @Query("SELECT * FROM score_items WHERE isMilestone = 1 ORDER BY (CASE WHEN completedAt IS NULL THEN 0 ELSE 1 END), updatedAt DESC, id DESC")
    fun observeMilestones(): Flow<List<ScoreItemEntity>>

    @Query("SELECT COUNT(*) FROM score_items WHERE category = :category")
    suspend fun countItems(category: String): Int

    @Query("SELECT * FROM score_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): ScoreItemEntity?

    @Insert
    suspend fun insertItem(item: ScoreItemEntity): Long

    @Update
    suspend fun updateItem(item: ScoreItemEntity)

    @Delete
    suspend fun deleteItem(item: ScoreItemEntity)

    @Query("SELECT * FROM score_logs ORDER BY createdAt DESC, id DESC")
    fun observeLogs(): Flow<List<ScoreLogEntity>>

    @Insert
    suspend fun insertLog(log: ScoreLogEntity)

    @Query("SELECT * FROM score_account WHERE id = 1 LIMIT 1")
    suspend fun getAccount(): ScoreAccountEntity?

    @Query("SELECT totalScore FROM score_account WHERE id = 1")
    fun observeTotalScore(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: ScoreAccountEntity)

    @Query("SELECT * FROM stat_trackers ORDER BY createdAt DESC")
    fun observeTrackers(): Flow<List<StatTrackerEntity>>

    @Insert
    suspend fun insertTracker(tracker: StatTrackerEntity): Long

    @Query("DELETE FROM stat_trackers WHERE id = :id")
    suspend fun deleteTracker(id: Long)
}

@Database(
    entities = [ScoreItemEntity::class, ScoreLogEntity::class, ScoreAccountEntity::class, StatTrackerEntity::class],
    version = 5,
    exportSchema = false
)
abstract class BoostBankDatabase : RoomDatabase() {
    abstract fun boostBankDao(): BoostBankDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE score_items ADD COLUMN imageBiasX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE score_items ADD COLUMN imageBiasY REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE score_items ADD COLUMN imageScale REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS stat_trackers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, trackedSources TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE score_items ADD COLUMN isMilestone INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE score_items ADD COLUMN completedAt INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: BoostBankDatabase? = null

        fun getInstance(context: Context): BoostBankDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BoostBankDatabase::class.java,
                    "boostbank.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }
    }
}