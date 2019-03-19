package io.github.cgh

import com.google.common.hash.Hashing.murmur3_32
import org.apache.commons.validator.UrlValidator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

@RestController
@SpringBootApplication
class UrlShortener(connectionFactory: ReactiveRedisConnectionFactory) {

    private val redis = ReactiveRedisTemplate(connectionFactory, RedisSerializationContext.string()).opsForValue()

    @PostMapping("/{*url}")
    fun save(req: ServerRequest) = req.pathVariable("url").substring(1).let { url ->
        if (UrlValidator(arrayOf("http", "https")).isValid(url)) {
            murmur3_32().hashString(url, UTF_8).toString().let { id ->
                redis.set(id, url).flatMap { ServerResponse.ok().syncBody("localhost:8080/$id") }
            }
        } else ServerResponse.badRequest().build()
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String) = redis.get(id)
            .flatMap { ServerResponse.permanentRedirect(URI(it)).build() }
            .switchIfEmpty(ServerResponse.notFound().build())
}

fun main(args: Array<String>) {
    runApplication<UrlShortener>(*args)
}