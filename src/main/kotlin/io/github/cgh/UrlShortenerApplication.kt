package io.github.cgh

import com.google.common.hash.Hashing.murmur3_32
import org.apache.commons.validator.UrlValidator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.http.HttpHeaders.LOCATION
import org.springframework.http.HttpStatus.MOVED_PERMANENTLY
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets.UTF_8

@RestController
@SpringBootApplication
class UrlShortener(connectionFactory: ReactiveRedisConnectionFactory) {

    private val redis = ReactiveRedisTemplate(connectionFactory, RedisSerializationContext.string()).opsForValue()

    @PostMapping
    fun save(req: ServerHttpRequest) = req.path.toString().substring(1).let { url ->
        if (UrlValidator(arrayOf("http", "https")).isValid(url)) {
            murmur3_32().hashString(url, UTF_8).toString().let { id -> redis.set(id, url).map { ResponseEntity.ok("localhost:8080/$id") } }
        } else Mono.just(ResponseEntity.badRequest().build())
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String) = redis.get(id)
            .map { ResponseEntity.status(MOVED_PERMANENTLY).header(LOCATION, it).build<Void>() }
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
}

fun main(args: Array<String>) {
    runApplication<UrlShortener>(*args)
}