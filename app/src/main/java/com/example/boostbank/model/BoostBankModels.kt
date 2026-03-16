package com.example.boostbank.model

enum class MainPage(val label: String, val shortLabel: String) {
    EARN("赚取积分", "赚"),
    REWARD("购买奖励", "奖"),
    OVERVIEW("分数概览", "览"),
    ME("我的", "我")
}

enum class ItemCategory {
    EARN,
    REWARD
}

enum class LogType {
    EARN,
    SPEND,
    ADJUST
}

data class ScoreItem(
    val id: Long,
    val name: String,
    val points: Int,
    val category: ItemCategory,
    val imageUri: String?,
    val imageBiasX: Float = 0f,
    val imageBiasY: Float = 0f,
    val imageScale: Float = 1f
)

data class ScoreLog(
    val id: Long,
    val source: String,
    val delta: Int,
    val note: String,
    val type: LogType,
    val afterScore: Int,
    val createdAt: Long
)

data class AppSettings(
    val language: String = "简体中文",
    val confirmBeforeReward: Boolean = true,
    val confirmBeforeEarn: Boolean = true,
    val useWarmBackground: Boolean = false,
    val backgroundMaskOpacity: Float = 0.92f,
    val avatarUri: String? = null,
    val earnBackgroundUri: String? = null,
    val rewardBackgroundUri: String? = null,
    val overviewBackgroundUri: String? = null,
    val meBackgroundUri: String? = null
) {
    fun backgroundFor(page: MainPage): String? {
        return when (page) {
            MainPage.EARN -> earnBackgroundUri
            MainPage.REWARD -> rewardBackgroundUri
            MainPage.OVERVIEW -> overviewBackgroundUri
            MainPage.ME -> meBackgroundUri
        }
    }
}