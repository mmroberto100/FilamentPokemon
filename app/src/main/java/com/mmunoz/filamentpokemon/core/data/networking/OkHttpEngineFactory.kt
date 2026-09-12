package com.mmunoz.filamentpokemon.core.data.networking

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Protocol

object OkHttpEngineFactory {

    /**
     * Sketchfab sits behind AWS WAF Bot Control, which answers OkHttp's HTTP/2 requests from
     * Android with `202 + x-amzn-waf-action: challenge` (empty body). The same requests over
     * HTTP/1.1 pass, so the engine is pinned to HTTP/1.1. Cost is negligible for this app:
     * a handful of JSON calls plus one large .glb stream per model.
     */
    fun create(): HttpClientEngine = OkHttp.create {
        config {
            protocols(listOf(Protocol.HTTP_1_1))
        }
    }
}
