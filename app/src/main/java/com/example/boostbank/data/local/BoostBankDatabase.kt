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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "score_items")
data class ScoreItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val points: Int,
    val category: String,
    val imageUri: String? = null,
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

@Dao
interface BoostBankDao {
    @Query("SELECT * FROM score_items WHERE category = :category ORDER BY updatedAt DESC, id DESC")
    fun observeItems(category: String): Flow<List<ScoreItemEntity>>

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
}

@Database(
    entities = [ScoreItemEntity::class, ScoreLogEntity::class, ScoreAccountEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BoostBankDatabase : RoomDatabase() {
    abstract fun boostBankDao(): BoostBankDao

    companion object {
        @Volatile
        private var INSTANCE: BoostBankDatabase? = null

        fun getInstance(context: Context): BoostBankDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BoostBankDatabase::class.java,
                    "boostbank.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}