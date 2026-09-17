package com.branchphotovault

import android.content.Context
import com.branchphotovault.data.local.AppDatabase
import com.branchphotovault.data.remote.AppsScriptService
import com.branchphotovault.data.repository.BranchRepository
import com.branchphotovault.data.repository.PhotoRepository
import com.branchphotovault.data.repository.SettingsRepository
import com.branchphotovault.util.AppConfig
import com.branchphotovault.util.ImageProcessor
import com.branchphotovault.util.MediaStoreExporter
import com.branchphotovault.worker.BranchPhotoVaultWorkerFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.RETROFIT_PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val database: AppDatabase = AppDatabase.getInstance(appContext)
    private val imageProcessor = ImageProcessor(appContext)
    private val mediaStoreExporter = MediaStoreExporter(appContext)

    val settingsRepository = SettingsRepository(appContext)
    val appsScriptService: AppsScriptService = retrofit.create(AppsScriptService::class.java)

    val branchRepository = BranchRepository(
        branchDao = database.branchDao(),
        photoDao = database.photoDao(),
        service = appsScriptService,
        settingsRepository = settingsRepository
    )

    val photoRepository = PhotoRepository(
        photoDao = database.photoDao(),
        branchDao = database.branchDao(),
        settingsRepository = settingsRepository,
        imageProcessor = imageProcessor,
        mediaStoreExporter = mediaStoreExporter,
        context = appContext
    )

    val workerFactory = BranchPhotoVaultWorkerFactory(
        photoRepository = photoRepository,
        settingsRepository = settingsRepository
    )
}

