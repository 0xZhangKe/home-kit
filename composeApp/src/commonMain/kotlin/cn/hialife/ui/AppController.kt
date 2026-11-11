package cn.hialife.ui

import androidx.compose.runtime.*
import cn.hialife.data.*
import kotlinx.coroutines.*

class AppController(private val api: HiLifeApi, private val scope: CoroutineScope) {
    var authenticated by mutableStateOf(api.session != null); private set
    var demo by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var devices by mutableStateOf(emptyList<Device>()); private set
    var selected by mutableStateOf<Device?>(null); private set
    var capabilities by mutableStateOf(emptyList<Capability>()); private set
    var notice by mutableStateOf<String?>(null); private set
    var error by mutableStateOf(false); private set

    private fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        error = false
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = true
                notice = (e as? ApiFailure)?.message ?: "操作未完成，请检查网络连接或本机会话存储后重试。"
            } finally { busy = false }
        }
    }
    fun login(username: String, password: String) = work {
        api.login(username, password)
        authenticated = true
        loadDevices()
    }
    fun importSession(raw: String) = work {
        api.importSession(raw)
        authenticated = true
        loadDevices()
    }
    fun logout() = work {
        if (!demo) api.logout()
        demo = false; authenticated = false; devices = emptyList(); selected = null; capabilities = emptyList()
    }
    fun startDemo() = work {
        demo = true; authenticated = true
        devices = listOf(demoDevice)
        selected = demoDevice
        capabilities = demoCapabilities
    }
    fun refresh() = work {
        if (demo) { delay(300); notice = "演示状态已更新" }
        else {
            selected?.let { api.requestState(it) }
            loadDevices()
            notice = "设备状态已更新"
        }
    }
    private suspend fun loadDevices() {
        val previous = selected?.id
        devices = api.devices()
        selected = devices.find { it.id == previous } ?: devices.firstOrNull()
        capabilities = emptyList()
        selected?.let { capabilities = api.capabilities(it) }
    }
    fun select(device: Device) = work {
        selected = device; capabilities = emptyList()
        capabilities = if (demo) demoCapabilities else api.capabilities(device)
    }
    fun command(capability: Capability, params: Map<String, String>) = work {
        val device = selected ?: return@work
        if (!device.online) throw ApiFailure("设备离线，暂时无法控制。")
        val actions = mutableListOf<Action>()
        val expected = mutableMapOf<String, String>()
        if (capability.code in listOf("VentilationModes", "VentilationWindSpeed") && device.state("ventilationWindSwitch") != "1") {
            val enable = capabilities.find { it.code == "VentilationWindSwitch" } ?: throw ApiFailure("设备未提供新风开关能力。")
            actions += Action(enable.command, mapOf("ventilationWindSwitch" to "1"))
            expected["ventilationWindSwitch"] = "1"
        }
        // Commands and allowed values originate in the current device capability response.
        params.forEach { (key, value) ->
            val attribute = capability.attribute(key) ?: throw ApiFailure("设备不支持此设置。")
            if (attribute.choices.isNotEmpty() && attribute.choices.none { it.value == value }) throw ApiFailure("设置值不受支持。")
            attribute.range?.let { range -> if (value.toFloatOrNull()?.let { it in range } != true) throw ApiFailure("设置值超出设备范围。") }
            expected[capability.stateKeys[key] ?: key] = value
        }
        actions += Action(capability.command, params)
        if (demo) {
            delay(350)
            selected = device.copy(states = device.states + expected)
            devices = listOf(selected!!)
            notice = "演示：设置已更新，未发送设备指令"
            return@work
        }
        try {
            api.execute(device, actions)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            error = true
            notice = "未收到指令确认。请先刷新检查设备状态，再决定是否重新操作。"
            return@work
        }
        notice = "指令已提交，正在等待设备确认…"
        try {
            api.requestState(device)
            repeat(10) {
                delay(2_000)
                devices = api.devices()
                val current = devices.find { it.id == device.id }
                selected = current
                if (current != null && expected.all { (k, value) ->
                    current.state(k) == value || (value.toDoubleOrNull() != null && current.state(k).toDoubleOrNull() == value.toDoubleOrNull())
                }) {
                    notice = "已回读确认 · 设置已生效"
                    return@work
                }
            }
            error = true
            notice = "指令已提交，20 秒内未确认目标状态。请刷新后检查设备。"
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            error = true
            notice = "指令已提交，但状态回读失败。请刷新检查，避免重复发送。"
        }
    }
}
