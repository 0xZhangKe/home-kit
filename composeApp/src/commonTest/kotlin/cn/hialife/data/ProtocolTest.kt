package cn.hialife.data

import cn.hialife.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

private class MemoryStore : SessionStore {
    var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
    override fun clear() { value = null }
}

class ProtocolTest {
    @Test fun signatureUsesRawMd5DigestAndUtf8() {
        // Independent reference vector generated with Python hashlib, not the client implementation.
        assertEquals("aPAsYNAylfwMYpfC+zNXXw==", md5Base64(("{\"name\":\"空调\"}" + APP_SECRET).encodeToByteArray()))
    }

    @Test fun passwordEncryptionMatchesOpenSslAes256Cbc() {
        assertEquals("1s56PEnq2/vABFV/83L2YJAsktaQdLGtzeZcbuTx4LM=", encryptLogin("海信-user@example.com"))
    }

    @Test fun modeUsesReportedStateKeyRatherThanCommandParameter() {
        val c = parseCapability(Json.parseToJsonElement("""{
            "code":"connector.device.function.Modes","name":"模式",
            "action":{"command":"connector.device.command.SetModes","params":[{"attribute":"modes"}]},
            "attributes":[{"code":"modes","value":[{"value":"2","name":"制冷"}]}],
            "states":[{"attribute":"modes","code":"workMode"}]}
        """).jsonObject)!!
        assertEquals("workMode", c.stateKeys["modes"])
        assertEquals(Choice("2", "制冷"), c.attribute("modes")!!.choices.single())
    }

    @Test fun loginSignsExactlyTheBytesSentAndPersistsNoPassword() = runTest {
        val store = MemoryStore()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/mobile/se/signon", request.url.encodedPath)
            val bytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
            assertEquals(md5Base64(bytes + APP_SECRET.encodeToByteArray()), request.headers["X-Sign-For"])
            assertEquals("commonweb", request.headers["appKey"])
            val body = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            assertEquals(encryptLogin("user"), body.string("loginName"))
            assertEquals(encryptLogin("secret"), body.string("signature"))
            respond("""{"data":{"resultCode":0,"customerId":"customer","tokenInfo":{"token":"access","refreshToken":"refresh"}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val api = HiLifeApi(store, client)
        try {
            api.login("user", "secret")
            assertEquals("access", api.session!!.token)
            assertEquals("customer", api.session!!.customerId)
            assertFalse(store.value!!.contains("secret"))
            assertFalse(store.value!!.contains("signature"))
        } finally { api.close() }
    }

    @Test fun expiredSessionRotatesBothTokensBeforeNextRequest() = runTest {
        val store = MemoryStore()
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            if (calls == 1) {
                assertEquals("/aaa/refresh_token2", request.url.encodedPath)
                respond("""[{"resultCode":0,"token":"new-access","refreshToken":"new-refresh","tokenExpiredTime":"259200"}]""")
            } else {
                assertTrue(store.value!!.contains("new-refresh"), "Rotated tokens must be saved before querying devices")
                val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                assertEquals("new-access", body.string("accessToken"))
                respond("""{"payload":{"status":"SUCCESS","devices":[]}}""")
            }
        })
        val api = HiLifeApi(store, client)
        try {
            api.importSession("""{"token":"old","customerId":"customer","refreshToken":"old-refresh","obtainedAt":1}""")
            assertTrue(api.devices().isEmpty())
            assertEquals(2, calls)
            assertEquals("new-refresh", api.session!!.refreshToken)
        } finally { api.close() }
    }

    @Test fun authenticationRetriesOnlyOnce() = runTest {
        val store = MemoryStore()
        var refreshes = 0
        var queries = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/aaa/refresh_token2") {
                refreshes++
                respond("""[{"resultCode":0,"token":"new","refreshToken":"rotated"}]""")
            } else {
                queries++
                respond("""{"payload":{"status":"FAIL","errorDesc":"token invalid"}}""", HttpStatusCode.Unauthorized)
            }
        })
        val api = HiLifeApi(store, client)
        try {
            api.importSession("""{"token":"old","customerId":"customer","refreshToken":"refresh","obtainedAt":${epochMillis()/1000.0}}""")
            assertFailsWith<ApiFailure> { api.devices() }
            assertEquals(1, refreshes)
            assertEquals(2, queries)
        } finally { api.close() }
    }

    @Test fun controlUsesDirectStringParametersAndDoesNotRetryLostResponse() = runTest {
        var submissions = 0
        val client = HttpClient(MockEngine { request ->
            submissions++
            assertEquals("/4.0/iot/devices/execute", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            assertEquals("smartlifeAppH5", body.string("sourceName"))
            val payload = body.obj("payload")
            assertEquals("device", payload.string("deviceId"))
            assertEquals("1", payload.array("actions").single().jsonObject.obj("params").string("ventilationWindSwitch"))
            throw IllegalStateException("Simulated lost response after submission")
        })
        val api = HiLifeApi(MemoryStore(), client)
        try {
            api.importSession("""{"token":"access","customerId":"customer","obtainedAt":${epochMillis()/1000.0}}""")
            val device = Device("device", "空调", "model", "product", "aircondition", "1001", emptyMap())
            assertFailsWith<IllegalStateException> {
                api.execute(device, listOf(Action("connector.device.command.SetVentilationWindSwitch", mapOf("ventilationWindSwitch" to "1"))))
            }
            assertEquals(1, submissions)
        } finally { api.close() }
    }
}
