package com.ljyh.mei

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.ljyh.mei.constants.DefaultImageCacheLimitMb
import com.ljyh.mei.constants.ImageCacheLimitMbKey
import com.ljyh.mei.utils.dataStore
import com.ljyh.mei.utils.log.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@HiltAndroidApp
class AppContext : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Install logging once per process. MainActivity is recreated for glass/HDR
        // changes; planting there would retain every old Activity and multiply logs.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLoggingTree(this))
    }

    companion object {
        @JvmStatic
        lateinit var instance: AppContext

    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val headerInterceptor = Interceptor { chain ->
            val newRequest = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
                .build()
            chain.proceed(newRequest)
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .build()

        return ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.1)
                    .build()
            }
            .diskCache {
                newDiskCache()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }
    private fun newDiskCache(): DiskCache {
        val limitMb = runBlocking {
            dataStore.data.first()[ImageCacheLimitMbKey] ?: DefaultImageCacheLimitMb
        }
        return DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(limitMb.coerceIn(50, 2048) * 1024L * 1024L)
            .build()
    }
}

