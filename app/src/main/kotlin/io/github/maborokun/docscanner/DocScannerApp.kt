package io.github.maborokun.docscanner

import android.app.Application
import io.github.maborokun.docscanner.appfunctions.AppFunctionsController
import io.github.maborokun.docscanner.appfunctions.AppFunctionsHost
import io.github.maborokun.docscanner.appfunctions.DocumentSearch
import io.github.maborokun.docscanner.domain.settings.SettingsRepository
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DocScannerApp : Application() {
    // Lives as long as the process; its only job is to keep the system's view of app functions in step with settings.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                androidContext(this@DocScannerApp)
                modules(appModule)
            }.koin
        // The assistant service is created by the system, possibly in a fresh process: give it what it needs now.
        AppFunctionsHost.search = DocumentSearch(koin.get<ObserveDocuments>())
        appScope.launch {
            koin
                .get<SettingsRepository>()
                .settings
                .map { it.appFunctionsEnabled }
                .distinctUntilChanged()
                .collect { AppFunctionsController.apply(this@DocScannerApp, it) }
        }
    }
}
