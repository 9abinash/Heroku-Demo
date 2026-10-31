package com.kidsshorts

import android.app.Application
import com.kidsshorts.di.AppContainer
import kotlinx.coroutines.launch

class KidsShortsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep approved channels reasonably fresh (also re-checks availability / Made-for-Kids status).
        container.appScope.launch { runCatching { container.channelRepository.syncStale() } }
    }
}
