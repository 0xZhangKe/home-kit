package cn.hialife.data

import cn.hialife.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

// Published web-client key; never place account credentials or user tokens here.
const val APP_SECRET = "MORZRbkuiWxjp+SM4vR_GxY4pZxLZ6rn"
private const val IOT = "https://iot-aihome.hismarttv.com"
class ApiFailure(message: String) : Exception(message)

class HiLifeApi(private val store: SessionStore, private val http: HttpClient = createHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }
    var session: Session? = runCatching { store.read()?.let { json.decodeFromString<Session>(it) } }.getOrNull()
        private set

    private fun accept(data: JsonObject, previous: Session? = null) {
        val token = data.string("token").ifBlank { data.string("accessToken") }
        val customer = data.string("customerId").ifBlank { previous?.customerId.orEmpty() }
        if (token.isBlank() || customer.isBlank()) throw ApiFailure("会话不完整，请重新登录。")
        val next = Session(token, customer, data.string("refreshToken").ifBlank { previous?.refreshToken.orEmpty() },
            data.string("tokenExpiredTime").toLongOrNull() ?: 259200,
            epochMillis() / 1000.0)
        // Persist rotated tokens before publishing the new session.
        store.write(json.encodeToString(next))
        session = next
    }

    fun importSession(raw: String) {
        val data = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw ApiFailure("请输入有效的会话 JSON。")
        // Force renewal on first request so an old imported access token isn't treated as new.
        accept(data)
        val imported = session!!.copy(obtainedAt = data.string("obtainedAt").toDoubleOrNull() ?: if (session!!.refreshToken.isNotBlank()) 0.0 else epochMillis() / 1000.0)
        store.write(json.encodeToString(imported))
        session = imported
    }

    fun logout() { store.clear(); session = null }
    fun close() = http.close()

    private suspend fun signed(url: String, body: JsonObject, query: Map<String, String> = emptyMap()): Pair<Int, JsonObject> {
        val raw = body.toString().encodeToByteArray()
        val response = http.post(url) {
            query.forEach { (k, v) -> parameter(k, v) }
            contentType(ContentType.Application.Json)
            header("appKey", "commonweb")
            header("X-Sign-For", md5Base64(raw + APP_SECRET.encodeToByteArray()))
            setBody(raw)
        }
        val parsed = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
            ?: throw ApiFailure("服务暂时不可用（HTTP ${response.status.value}）。")
        return response.status.value to parsed
    }

    suspend fun login(username: String, password: String) = withContext(Dispatchers.Default) {
        val (code, response) = signed("https://portal-account.hismarttv.com/mobile/se/signon", buildJsonObject {
            put("loginName", encryptLogin(username.trim())); put("signature", encryptLogin(password))
            put("serverCode", "9501"); put("distributeId", "2001"); put("termType", 2)
        }, mapOf("lastUpdateTime" to "0", "version" to "1.0", "deviceType" to "2", "appType" to "100",
            "versionCode" to "101", "adaptertRank" to "720", "_" to epochMillis().toString()))
        val data = response.obj("data")
        if (code !in 200..299 || data.string("resultCode") != "0")
            throw ApiFailure("登录失败，请核对账号和密码；该入口可能需要官方客户端验证。")
        val info = JsonObject(data.obj("tokenInfo") + ("customerId" to JsonPrimitive(
            data.obj("tokenInfo").string("customerId").ifBlank { data.string("customerId") })))
        accept(info)
    }

    private suspend fun refresh() {
        val old = session ?: throw ApiFailure("请先登录。")
        if (old.refreshToken.isBlank()) throw ApiFailure("会话已到期，请重新登录或导入新会话。")
        val response = http.submitForm("https://bas-wg.hismarttv.com/aaa/refresh_token2", parameters {
            append("refreshToken", old.refreshToken); append("appKey", "2"); append("format", "1")
        })
        val root = runCatching { json.parseToJsonElement(response.bodyAsText()) }.getOrNull()
        val data = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root["data"] as? JsonObject ?: root
            else -> null
        } ?: throw ApiFailure("续期服务响应异常，请稍后重试。")
        if (response.status.value !in 200..299 || data.string("resultCode").ifBlank { "0" } != "0" || data.string("token").isBlank())
            throw ApiFailure("会话续期失败，请重新登录或导入新会话。")
        accept(data, old)
    }

    private suspend fun post(path: String, payload: JsonObject, execute: Boolean = false): JsonObject {
        val auth = session ?: throw ApiFailure("请先登录。")
        if (epochMillis() / 1000.0 - auth.obtainedAt > (auth.tokenExpiredTime - 3600).coerceAtLeast(0)) refresh()
        repeat(2) { attempt ->
            val (httpCode, response) = signed(IOT + path, buildJsonObject {
                put("appVersion", "7.0.9.3"); put("appType", 1)
                putJsonObject("header") { put("messageId", randomId()); put("timestamp", epochMillis()); put("languageId", 0) }
                put("accessToken", session!!.token); put("payload", payload)
                if (execute) put("sourceName", "smartlifeAppH5")
            })
            val state = response.obj("payload")
            if (httpCode in 200..299 && state.string("status") == "SUCCESS") return state
            val reason = listOf(response, state).joinToString { node ->
                listOf("errorDesc", "message", "errorCode").joinToString { node.string(it) }
            }
            val authError = httpCode == 401 || httpCode == 403 || response["tokenErrorCode"] is JsonPrimitive || reason.contains("token", true)
            if (attempt == 0 && authError) refresh()
            else throw ApiFailure(if (authError) "会话无效，请重新登录。" else "请求失败（HTTP $httpCode），请稍后重试。")
        }
        throw ApiFailure("认证重试失败。")
    }

    suspend fun devices(): List<Device> = post("/4.0/iot/devices/list", buildJsonObject {
        put("sourceType", 20); put("customerId", session!!.customerId)
        put("withState", true); put("withMultiSplit", true); put("whitelist", 1)
    }).array("devices").map { parseDevice(it.jsonObject) }.filter { it.category.contains("aircondition", true) }

    suspend fun capabilities(device: Device): List<Capability> = post("/4.0/iot/devices/function/getByProduct", buildJsonObject {
        put("sourceType", 20)
        putJsonArray("products") { addJsonObject {
            put("productCode", device.productCode); put("categoryCode", device.category)
            put("partnerId", device.partnerId); put("deviceId", device.id)
        } }
    }).array("products").flatMap { it.jsonObject.array("functions") }.mapNotNull { parseCapability(it.jsonObject) }

    suspend fun requestState(device: Device) {
        post("/4.0/iot/state/refreshDeviceState", buildJsonObject {
            putJsonArray("deviceIds") { addJsonObject { put("partnerId", device.partnerId); put("deviceId", device.id) } }
        })
    }

    suspend fun execute(device: Device, actions: List<Action>) {
        post("/4.0/iot/devices/execute", buildJsonObject {
            put("sourceType", "20"); put("exclusionFlag", 1); put("customerId", session!!.customerId)
            put("deviceId", device.id); put("partnerId", device.partnerId)
            putJsonArray("actions") { actions.forEach { action -> addJsonObject {
                put("command", action.command)
                putJsonObject("params") { action.params.forEach { (k, v) -> put(k, v) } }
            } } }
        }, execute = true)
    }
}
