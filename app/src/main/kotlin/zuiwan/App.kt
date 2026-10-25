package zuiwan

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import zuiwan.state.Hub
import java.io.File
import java.util.concurrent.TimeUnit

class App : Application(), SingletonImageLoader.Factory {
    lateinit var hub: Hub
        private set

    override fun onCreate() {
        super.onCreate()
        hub = Hub(this)
        hub.updater.initchannel()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imgHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
                    )
                    .header("Referer", "https://music.youtube.com/")
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = imgHttp))
            }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
