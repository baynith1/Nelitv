package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.ads.NeliAdMobManager
import com.example.player.NativeLogSuppressor
import com.example.ui.theme.NeliThemeManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application class for Neli TV.
 *
 * - Configures the AdMob test device ID for development and initializes the Google Mobile Ads SDK
 *   during app startup on a background thread so UI startup remains fast and responsive.
 * - Provides a high-performance singleton Coil [ImageLoader] with browser User-Agent headers
 *   and memory/disk caching so all Live TV channel logos, Movie posters, Series, and Adult
 *   thumbnails load reliably and quickly.
 */
class NeliApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        NativeLogSuppressor.suppressNonFatalNativeLogs()
        super.onCreate()
        NeliThemeManager.initialize(this)
        // Configure test device IDs for development and initialize Google Mobile Ads SDK at app startup
        NeliAdMobManager.initializeInApplication(this)
    }

    override fun newImageLoader(): ImageLoader {
        val imageHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    .header(
                        "Accept",
                        "image/png,image/jpeg,image/webp,image/*;q=0.8"
                    )
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageHttpClient)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("neli_image_cache"))
                    .maxSizeBytes(120L * 1024L * 1024L) // 120 MB disk cache
                    .build()
            }
            .allowHardware(false)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
