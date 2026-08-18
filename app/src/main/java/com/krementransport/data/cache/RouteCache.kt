package com.krementransport.data.cache

import android.content.Context
import com.krementransport.data.api.CachedPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The routes payload is 565 KB and the map must never wait for it. Raw bytes are kept on disk
 * next to their validators so a cold launch paints from the cache first and only then
 * revalidates — a 304 costs one round trip and no decode at all.
 */
class RouteCache(context: Context) {

    private val directory = File(context.filesDir, "routes").apply { mkdirs() }
    private val payloadFile = File(directory, "routes.v1.json")
    private val metaFile = File(directory, "routes.v1.meta.json")

    @Serializable
    private data class Meta(
        val etag: String? = null,
        val lastModified: String? = null,
        val fetchedAt: Long = 0L,
    )

    data class Entry(val payload: CachedPayload, val ageMillis: Long)

    suspend fun load(): Entry? = withContext(Dispatchers.IO) {
        if (!payloadFile.exists()) return@withContext null
        val bytes = runCatching { payloadFile.readBytes() }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        val meta = readMeta()
        Entry(
            payload = CachedPayload(bytes, meta.etag, meta.lastModified),
            ageMillis = System.currentTimeMillis() - meta.fetchedAt,
        )
    }

    suspend fun store(payload: CachedPayload) = withContext(Dispatchers.IO) {
        runCatching {
            payloadFile.writeBytes(payload.bytes)
            writeMeta(Meta(payload.etag, payload.lastModified, System.currentTimeMillis()))
        }
        Unit
    }

    /** A 304: the bytes are still current, only their age is not. */
    suspend fun touch() = withContext(Dispatchers.IO) {
        runCatching { writeMeta(readMeta().copy(fetchedAt = System.currentTimeMillis())) }
        Unit
    }

    private fun readMeta(): Meta = runCatching {
        Json.decodeFromString(Meta.serializer(), metaFile.readText())
    }.getOrDefault(Meta())

    private fun writeMeta(meta: Meta) {
        metaFile.writeText(Json.encodeToString(Meta.serializer(), meta))
    }
}
