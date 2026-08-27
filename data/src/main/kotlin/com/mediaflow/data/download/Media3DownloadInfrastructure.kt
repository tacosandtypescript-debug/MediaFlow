package com.mediaflow.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Owns the one process-wide Media3 cache and DownloadManager.
 *
 * The cache uses NoOpCacheEvictor deliberately: completed downloads are not
 * evicted automatically. The app's private files directory is used, so no
 * storage permission is needed.
 */
@OptIn(markerClass = [UnstableApi::class])
object Media3DownloadInfrastructure {
    private const val CACHE_DIRECTORY = "media3_download_cache"

    @Volatile
    private var holder: Holder? = null

    fun get(context: Context): Holder {
        return holder ?: synchronized(this) {
            holder ?: create(context.applicationContext).also { holder = it }
        }
    }

    private fun create(context: Context): Holder {
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            File(context.filesDir, CACHE_DIRECTORY),
            NoOpCacheEvictor(),
            databaseProvider,
        )
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
        val executor: Executor = Executors.newFixedThreadPool(3)
        val downloadManager = DownloadManager(
            context,
            databaseProvider,
            cache,
            cacheDataSourceFactory,
            executor,
        ).apply {
            maxParallelDownloads = 2
            minRetryCount = 3
        }
        return Holder(
            cache = cache,
            cacheDataSourceFactory = cacheDataSourceFactory,
            downloadManager = downloadManager,
        )
    }

    data class Holder(
        val cache: SimpleCache,
        val cacheDataSourceFactory: CacheDataSource.Factory,
        val downloadManager: DownloadManager,
    )
}
