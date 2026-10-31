package com.kidsshorts.di

import android.content.Context
import androidx.room.Room
import com.kidsshorts.BuildConfig
import com.kidsshorts.data.db.AppDatabase
import com.kidsshorts.data.repo.AuditRepository
import com.kidsshorts.data.repo.ChannelRepository
import com.kidsshorts.data.repo.EligibilityLogger
import com.kidsshorts.data.repo.FeedRepository
import com.kidsshorts.data.repo.ReviewRepository
import com.kidsshorts.data.repo.ScreenTimeRepository
import com.kidsshorts.data.repo.SettingsRepository
import com.kidsshorts.data.youtube.AppSignature
import com.kidsshorts.data.youtube.YouTubeApiClient
import com.kidsshorts.security.ApiKeyStore
import com.kidsshorts.security.PinManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Manual dependency injection. Chosen over Hilt so the project has no annotation-processing beyond
 * Room/KSP; every dependency is a plain constructor argument, so migrating to Hilt later is mechanical.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: () -> Long = System::currentTimeMillis

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val applicationId: String = appContext.packageName

    private val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "kidsshorts.db").build()

    val auditRepository = AuditRepository(db.auditDao(), clock)
    val settingsRepository = SettingsRepository(db.settingsDao(), auditRepository)
    val apiKeyStore = ApiKeyStore(appContext, BuildConfig.YOUTUBE_API_KEY)
    private val api = YouTubeApiClient(
        apiKeyProvider = { apiKeyStore.get() },
        packageName = appContext.packageName,
        certSha1Hex = AppSignature.sha1Hex(appContext),
    )
    val channelRepository = ChannelRepository(api, db.channelDao(), db.videoDao(), auditRepository, clock)
    val reviewRepository = ReviewRepository(db.decisionDao(), auditRepository, clock)
    val feedRepository = FeedRepository(db.channelDao(), db.videoDao(), db.decisionDao(), settingsRepository, EligibilityLogger())
    val screenTimeRepository = ScreenTimeRepository(db.sessionDao(), db.overrideDao(), settingsRepository, auditRepository, clock)
    val pinManager = PinManager(appContext)
    val parentSession = ParentSession()

    /** Parent-initiated "delete everything": database, PIN credential, API key. */
    suspend fun resetAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        pinManager.wipe()
        apiKeyStore.wipe()
        parentSession.lock()
    }
}
