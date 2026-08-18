package com.krementransport.screenshot

import android.content.res.AssetManager
import com.krementransport.data.api.ApiClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Serves the API from the payloads captured in `app/src/test/resources`, so a screenshot run
 * touches no network and produces the same pixels every time.
 *
 * The live API is unusable for store screenshots: vehicles move every 5 s, roughly a third of the
 * fleet is offline at any moment, and arrival predictions expire in minutes. Re-shooting for the
 * next release would silently change the whole listing.
 *
 * Debug-only — this file is never compiled into a release build.
 */
object FixtureApi {

    fun client(assets: AssetManager): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(FixtureInterceptor(assets))
        .build()

    fun apiClient(assets: AssetManager): ApiClient = ApiClient(client = client(assets))
}

private class FixtureInterceptor(private val assets: AssetManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimStart('/')

        val asset = when {
            path == "transport/routes" -> "routes.json"
            path == "transport/buses" -> "buses.json"
            path == "transport/buses/locations" -> "locations.json"
            path.startsWith("transport/stations/") && path.endsWith("/prediction") -> "prediction.json"
            else -> null
        }

        val body = asset?.let { freshen(assets.open(it).bufferedReader().use { r -> r.readText() }) }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (body == null) 404 else 200)
            .message(if (body == null) "Not Found" else "OK")
            // A stable validator: RouteCache stores it and revalidates conditionally, and a
            // changing ETag would re-download 565 KB on every launch of the harness.
            .header("ETag", "\"fixtures-1\"")
            .body((body ?: "{}").toResponseBody(Json))
            .build()
    }

    /**
     * Moves the fixtures' clock to now.
     *
     * The captures are dated 2026-08-13. Served verbatim every vehicle reads as days stale, and
     * `MapScreen` renders the offline chip over an otherwise perfectly good map. Arrival times
     * need no such treatment: `prediction` is a duration in seconds, not a wall-clock instant.
     */
    private fun freshen(json: String): String {
        val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        return json.replace(UpdatedAt) { "\"updated_at\":\"$nowIso\"" }
    }

    private companion object {
        val Json = "application/json; charset=utf-8".toMediaType()
        val UpdatedAt = Regex("\"updated_at\"\\s*:\\s*\"[^\"]*\"")
    }
}
