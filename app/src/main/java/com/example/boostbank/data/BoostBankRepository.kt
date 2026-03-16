package com.example.boostbank.data

import android.content.Context
import androidx.room.withTransaction
import com.example.boostbank.data.local.BoostBankDatabase
import com.example.boostbank.data.local.ScoreAccountEntity
import com.example.boostbank.data.local.ScoreItemEntity
import com.example.boostbank.data.local.ScoreLogEntity
import com.example.boostbank.data.settings.SettingsStore
import com.example.boostbank.model.AppSettings
import com.example.boostbank.model.ItemCategory
import com.example.boostbank.model.LogType
import com.example.boostbank.model.MainPage
import com.example.boostbank.model.ScoreItem
import com.example.boostbank.model.ScoreLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BoostBankRepository(context: Context) {
    private val database = BoostBankDatabase.getInstance(context)
    private val dao = database.boostBankDao()
    private val settingsStore = SettingsStore(context)

    val earnItems: Flow<List<ScoreItem>> = dao.observeItems(ItemCategory.EARN.name)
        .map { list -> list.map { it.toModel() } }

    val rewardItems: Flow<List<ScoreItem>> = dao.observeItems(ItemCategory.REWARD.name)
        .map { list -> list.map { it.toModel() } }

    val logs: Flow<List<ScoreLog>> = dao.observeLogs()
        .map { list -> list.map { it.toModel() } }

    val totalScore: Flow<Int> = dao.observeTotalScore()
        .map { it ?: 0 }

    val settings: Flow<AppSettings> = settingsStore.settings

    suspend fun seedDefaultsIfNeeded() {
        database.withTransaction {
            val account = dao.getAccount()
            if (account == null) {
                dao.upsertAccount(ScoreAccountEntity(totalScore = 0))
                dao.insertItem(ScoreItemEntity(name = "晨间运动", points = 10, category = ItemCategory.EARN.name))
                dao.insertItem(ScoreItemEntity(name = "读书 30 分钟", points = 8, category = ItemCategory.EARN.name))
                dao.insertItem(ScoreItemEntity(name = "完成今日计划", points = 15, category = ItemCategory.EARN.name))
                dao.insertItem(ScoreItemEntity(name = "看一集喜欢的剧", points = 20, category = ItemCategory.REWARD.name))
                dao.insertItem(ScoreItemEntity(name = "买一杯奶茶", points = 30, category = ItemCategory.REWARD.name))
            }
        }
    }

    suspend fun addItem(
        category: ItemCategory,
        name: String,
        points: Int,
        imageUri: String?,
        imageBiasX: Float,
        imageBiasY: Float,
        imageScale: Float
    ) {
        dao.insertItem(
            ScoreItemEntity(
                name = name,
                points = points,
                category = category.name,
                imageUri = imageUri,
                imageBiasX = imageBiasX,
                imageBiasY = imageBiasY,
                imageScale = imageScale
            )
        )
    }

    suspend fun updateItem(
        id: Long,
        name: String,
        points: Int,
        imageUri: String?,
        imageBiasX: Float,
        imageBiasY: Float,
        imageScale: Float
    ) {
        val existing = dao.getItemById(id) ?: return
        dao.updateItem(
            existing.copy(
                name = name,
                points = points,
                imageUri = imageUri,
                imageBiasX = imageBiasX,
                imageBiasY = imageBiasY,
                imageScale = imageScale,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteItem(id: Long) {
        val existing = dao.getItemById(id) ?: return
        dao.deleteItem(existing)
    }

    suspend fun completeEarn(itemId: Long) {
        database.withTransaction {
            val item = dao.getItemById(itemId) ?: return@withTransaction
            val account = dao.getAccount() ?: ScoreAccountEntity(totalScore = 0)
            val newTotal = account.totalScore + item.points
            dao.upsertAccount(account.copy(totalScore = newTotal, updatedAt = System.currentTimeMillis()))
            dao.insertLog(
                ScoreLogEntity(
                    source = item.name,
                    delta = item.points,
                    note = "完成事务",
                    type = LogType.EARN.name,
                    afterScore = newTotal
                )
            )
        }
    }

    suspend fun redeemReward(itemId: Long): Boolean {
        return database.withTransaction {
            val item = dao.getItemById(itemId) ?: return@withTransaction false
            val account = dao.getAccount() ?: ScoreAccountEntity(totalScore = 0)
            val newTotal = account.totalScore - item.points
            dao.upsertAccount(account.copy(totalScore = newTotal, updatedAt = System.currentTimeMillis()))
            dao.insertLog(
                ScoreLogEntity(
                    source = item.name,
                    delta = -item.points,
                    note = "兑换奖励",
                    type = LogType.SPEND.name,
                    afterScore = newTotal
                )
            )
            true
        }
    }

    suspend fun adjustTotalScore(newScore: Int) {
        database.withTransaction {
            val account = dao.getAccount() ?: ScoreAccountEntity(totalScore = 0)
            val delta = newScore - account.totalScore
            dao.upsertAccount(account.copy(totalScore = newScore, updatedAt = System.currentTimeMillis()))
            dao.insertLog(
                ScoreLogEntity(
                    source = "手动调整",
                    delta = delta,
                    note = "校准总积分",
                    type = LogType.ADJUST.name,
                    afterScore = newScore
                )
            )
        }
    }

    suspend fun setLanguage(language: String) {
        settingsStore.setLanguage(language)
    }

    suspend fun setConfirmBeforeReward(enabled: Boolean) {
        settingsStore.setConfirmBeforeReward(enabled)
    }

    suspend fun setUseWarmBackground(enabled: Boolean) {
        settingsStore.setUseWarmBackground(enabled)
    }

    suspend fun setBackgroundMaskOpacity(opacity: Float) {
        settingsStore.setBackgroundMaskOpacity(opacity)
    }

    suspend fun setPageBackground(page: MainPage, uri: String?) {
        settingsStore.setPageBackground(page, uri)
    }

    suspend fun setAvatarUri(uri: String?) {
        settingsStore.setAvatarUri(uri)
    }

    suspend fun setConfirmBeforeEarn(enabled: Boolean) {
        settingsStore.setConfirmBeforeEarn(enabled)
    }

    private fun ScoreItemEntity.toModel(): ScoreItem {
        return ScoreItem(
            id = id,
            name = name,
            points = points,
            category = ItemCategory.valueOf(category),
            imageUri = imageUri,
            imageBiasX = imageBiasX,
            imageBiasY = imageBiasY,
            imageScale = imageScale
        )
    }

    private fun ScoreLogEntity.toModel(): ScoreLog {
        return ScoreLog(
            id = id,
            source = source,
            delta = delta,
            note = note,
            type = LogType.valueOf(type),
            afterScore = afterScore,
            createdAt = createdAt
        )
    }
}