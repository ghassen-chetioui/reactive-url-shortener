package io.github.cgh

import com.google.common.hash.Hashing.murmur3_32
import org.apache.commons.validator.UrlValidator
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.fu.kofu.configuration
import org.springframework.fu.kofu.web.server
import org.springframework.fu.kofu.webApplication
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

fun main() {
    app.run()
}

val app = webApplication {
    configuration {
        beans {
            bean<UrlShortenerHandler>()
            bean {
                val handler = ref<UrlShortenerHandler>()
                router {
                    GET("/{id}", handler::get)
                    POST("/", handler::save)
                }
            }
        }
    }
    server {}
}

class UrlShortenerHandler(connectionFactory: ReactiveRedisConnectionFactory) {
    private val redis = ReactiveRedisTemplate(connectionFactory, RedisSerializationContext.string()).opsForValue()

    fun save(req: ServerRequest): Mono<ServerResponse> {
        val httpRequest = req as ServerHttpRequest
        val url = httpRequest.uri.toASCIIString().substringAfter("${httpRequest.uri.authority}/")
        return if (UrlValidator(arrayOf("http", "https")).isValid(url)) {
            val id = murmur3_32().hashString(url, UTF_8).toString()
            redis.set(id, url).flatMap { ServerResponse.ok().syncBody("localhost:8080/$id") }
        } else ServerResponse.badRequest().build()
    }

    fun get(req: ServerRequest): Mono<ServerResponse> {
        return redis.get(req.pathVariable("id"))
                .flatMap { ServerResponse.permanentRedirect(URI(it)).build() }
                .switchIfEmpty(ServerResponse.notFound().build())
    }

}