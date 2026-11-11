package cn.hialife

import cn.hialife.data.APP_SECRET
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun createHttpClient() = HttpClient(OkHttp) {
    install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
    engine { config { retryOnConnectionFailure(false) } }
}
actual fun epochMillis(): Long = System.currentTimeMillis()
actual fun randomId(): String = UUID.randomUUID().toString()
actual fun md5Base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(bytes))
actual fun encryptLogin(value: String): String = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
    val key = APP_SECRET.toByteArray(Charsets.UTF_8)
    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key.copyOfRange(0, 16)))
    Base64.getEncoder().encodeToString(doFinal(value.toByteArray(Charsets.UTF_8)))
}
