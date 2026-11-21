package com.archangel.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class HttpResult(val code: Int, val body: String) {
    val ok get() = code in 200..299
}

/** Понятная пользователю ошибка: текст сразу можно показать в чате. */
class FriendlyException(message: String) : Exception(message)

class HttpBytes(val code: Int, val bytes: ByteArray) {
    val ok get() = code in 200..299
}

/** Минимальный HTTP-клиент на HttpURLConnection — без сторонних библиотек. */
object Http {
    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        readTimeoutMs: Int = 60_000,
    ): HttpResult = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResult(code, text)
        } catch (e: FriendlyException) {
            throw e
        } catch (e: UnknownHostException) {
            throw FriendlyException("Нет интернета или сервер недоступен.")
        } catch (e: SocketTimeoutException) {
            throw FriendlyException("Сервер долго не отвечает. Попробуйте ещё раз.")
        } catch (e: IOException) {
            throw FriendlyException("Ошибка сети: ${e.message ?: "неизвестно"}")
        } finally {
            conn.disconnect()
        }
    }

    /** То же, но ответ — двоичные данные (например, mp3 озвучки). */
    suspend fun requestBytes(
        url: String,
        method: String = "POST",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpBytes = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            HttpBytes(code, stream?.use { it.readBytes() } ?: ByteArray(0))
        } catch (e: UnknownHostException) {
            throw FriendlyException("Нет интернета или сервер недоступен.")
        } catch (e: SocketTimeoutException) {
            throw FriendlyException("Сервер долго не отвечает.")
        } catch (e: IOException) {
            throw FriendlyException("Ошибка сети: ${e.message ?: "неизвестно"}")
        } finally {
            conn.disconnect()
        }
    }
}
