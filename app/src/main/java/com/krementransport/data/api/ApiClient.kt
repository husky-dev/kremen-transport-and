package com.krementransport.data.api

import com.krementransport.domain.model.Prediction
import com.krementransport.domain.model.Route
import com.krementransport.domain.model.Vehicle
import com.krementransport.domain.model.VehicleFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Everything the backend exposes that this app uses. Read-only and unauthenticated. */
sealed class Endpoint(val path: String) {
    data object Routes : Endpoint("transport/routes")
    data object Buses : Endpoint("transport/buses")
    data object Locations : Endpoint("transport/buses/locations")
    data class StopPrediction(val sid: Int) : Endpoint("transport/stations/$sid/prediction")
}

/** Payload plus the validators needed for a conditional refetch. */
data class CachedPayload(
    val bytes: ByteArray,
    val etag: String?,
    val lastModified: String?,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is CachedPayload && bytes.contentEquals(other.bytes) &&
            etag == other.etag && lastModified == other.lastModified)

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + etag.hashCode()) * 31 + lastModified.hashCode()
}

class ApiException(message: String, val status: Int? = null) : Exception(message)

/**
 * There is no websocket and no server-side marker-image service. The 1.4 app used
 * `wss://api.kremen.dev/transport/realtime` and `/img/transport/bus/pin`; that host is dead and
 * this one 404s both. All movement comes from polling, all markers are drawn client-side.
 */
class ApiClient(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BaseUrl,
) {

    suspend fun routes(): List<Route> =
        decodeRoutes(get(Endpoint.Routes, noCache = false))

    suspend fun buses(): List<Vehicle> = withContext(Dispatchers.Default) {
        TransportJson.decodeFromString(ListSerializer(VehicleDto.serializer()), get(Endpoint.Buses).decodeToString())
            .map { it.toDomain() }
    }

    /** 14 KB against the roster's 78 KB — this is what the 5-second tick actually fetches. */
    suspend fun locations(): Map<String, VehicleFix> = withContext(Dispatchers.Default) {
        val raw: Map<String, List<Double>> = TransportJson.decodeFromString(
            MapSerializer(String.serializer(), ListSerializer(Double.serializer())),
            get(Endpoint.Locations).decodeToString(),
        )
        raw.mapNotNull { (tid, values) -> VehicleFix.orNull(values)?.let { tid to it } }.toMap()
    }

    suspend fun prediction(sid: Int): List<Prediction> = withContext(Dispatchers.Default) {
        TransportJson.decodeFromString(
            ListSerializer(PredictionDto.serializer()),
            get(Endpoint.StopPrediction(sid)).decodeToString(),
        ).map { it.toDomain() }.sortedBy { it.seconds }
    }

    /**
     * Fetches routes honouring `If-None-Match` / `If-Modified-Since`. Returns `null` on 304, so
     * the caller can touch its cache timestamp instead of re-decoding 565 KB.
     */
    suspend fun routesIfChanged(cached: CachedPayload?): CachedPayload? {
        val builder = Request.Builder().url(baseUrl + Endpoint.Routes.path)
        cached?.etag?.let { builder.header("If-None-Match", it) }
        cached?.lastModified?.let { builder.header("If-Modified-Since", it) }

        val response = execute(builder.build())
        response.use {
            if (it.code == 304) return null
            if (!it.isSuccessful) throw errorFor(it)
            return CachedPayload(
                bytes = it.body.bytes(),
                etag = it.header("ETag"),
                lastModified = it.header("Last-Modified"),
            )
        }
    }

    /** Decoding 565 KB is not main-thread work. */
    suspend fun decodeRoutes(bytes: ByteArray): List<Route> = withContext(Dispatchers.Default) {
        TransportJson.decodeFromString(ListSerializer(RouteDto.serializer()), bytes.decodeToString())
            .map { it.toDomain() }
    }

    // MARK: - Plumbing

    private suspend fun get(endpoint: Endpoint, noCache: Boolean = true): ByteArray {
        val builder = Request.Builder().url(baseUrl + endpoint.path)
        // Live positions must never come from a cache.
        if (noCache) builder.cacheControl(CacheControl.FORCE_NETWORK)
        val response = execute(builder.build())
        response.use {
            if (!it.isSuccessful) throw errorFor(it)
            return it.body.bytes()
        }
    }

    private fun errorFor(response: Response): ApiException {
        val body = runCatching {
            TransportJson.decodeFromString(ApiErrorBody.serializer(), response.body.string())
        }.getOrNull()
        val detail = body?.message ?: body?.code
        return ApiException(detail ?: "HTTP ${response.code}", response.code)
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(ApiException(e.message ?: "network error"))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }

    companion object {
        const val BaseUrl = "https://api.husky-dev.me/"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
