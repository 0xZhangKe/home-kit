package cn.hialife.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class Session(
    val token: String,
    val customerId: String,
    val refreshToken: String = "",
    val tokenExpiredTime: Long = 259200,
    val obtainedAt: Double = 0.0,
)

data class Device(
    val id: String,
    val name: String,
    val model: String,
    val productCode: String,
    val category: String,
    val partnerId: String,
    val states: Map<String, String>,
) {
    val online get() = states["onlineStatus"] == "1"
    fun state(key: String) = states[key].orEmpty()
}

data class Choice(val value: String, val label: String)
data class Attribute(val code: String, val choices: List<Choice>, val range: ClosedFloatingPointRange<Float>?)
data class Capability(
    val code: String,
    val name: String,
    val command: String,
    val attributes: List<Attribute>,
    val stateKeys: Map<String, String>,
) {
    fun attribute(code: String) = attributes.find { it.code == code }
}
data class Action(val command: String, val params: Map<String, String>)

internal fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())

internal fun parseDevice(d: JsonObject): Device {
    val product = d.obj("product")
    return Device(d.string("deviceId"), d.string("nickname").ifBlank { "我的空调" },
        product.string("name"), product.string("code"), d.string("categoryCode"),
        d.obj("partner").string("id").ifBlank { d.obj("partner").string("partnerId").ifBlank { "1001" } },
        d.obj("states").mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() })
}

internal fun parseCapability(f: JsonObject): Capability? {
    val command = f.obj("action").string("command")
    if (command.isBlank()) return null
    val params = f.obj("action").array("params").map { it.jsonObject.string("attribute") }.toSet()
    return Capability(f.string("code").substringAfterLast('.'), f.string("name"), command,
        f.array("attributes").mapNotNull {
            val a = it.jsonObject
            val code = a.string("code")
            if (code !in params) return@mapNotNull null
            val values = a["value"]
            val choices = (values as? JsonArray)?.map { item ->
                Choice(item.jsonObject.string("value"), item.jsonObject.string("name"))
            }.orEmpty()
            val bounds = (values as? JsonPrimitive)?.contentOrNull?.trim('[', ']')
                ?.split(',')?.mapNotNull { n -> n.trim().toFloatOrNull() }
            Attribute(code, choices, bounds?.takeIf { it.size == 2 }?.let { it[0]..it[1] })
        }, f.array("states").associate { it.jsonObject.string("attribute") to it.jsonObject.string("code") })
}
