package com.apexos.repoguardian

import android.app.Application
import com.apexos.repoguardian.data.llm.LlamaService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RepoGuardianApp : Application() {

    @Inject
    lateinit var llamaService: LlamaService

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            llamaService.autoStartService()
        }
    }
}
