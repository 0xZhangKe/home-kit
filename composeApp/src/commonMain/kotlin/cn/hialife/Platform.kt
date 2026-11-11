package cn.hialife

import io.ktor.client.HttpClient

interface SessionStore {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

expect fun createHttpClient(): HttpClient
expect fun epochMillis(): Long
expect fun randomId(): String
expect fun md5Base64(bytes: ByteArray): String
expect fun encryptLogin(value: String): String
