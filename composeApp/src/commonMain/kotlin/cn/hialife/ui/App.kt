package cn.hialife.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hialife.SessionStore
import cn.hialife.data.*

private val Green = Color(0xFF286653)
private val Background = Color(0xFFF4F6F2)
private val Muted = Color(0xFF6F7D76)
private val Outline = Color(0xFFE0E7DF)

@Composable
fun App(store: SessionStore) {
    val api = remember { HiLifeApi(store) }
    val scope = rememberCoroutineScope()
    val controller = remember { AppController(api, scope) }
    DisposableEffect(api) { onDispose { api.close() } }
    LaunchedEffect(Unit) { if (controller.authenticated) controller.refresh() }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Background,
        primaryContainer = Color(0xFFDCEBDD), secondaryContainer = Color(0xFFDCEBDD),
        onPrimaryContainer = Green, onSecondaryContainer = Green,
        surface = Color.White, onSurface = Color(0xFF243A30), outline = Outline)) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Box(Modifier.safeDrawingPadding().imePadding()) {
                if (controller.authenticated) Dashboard(controller) else Login(controller)
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Air, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text("HiALife", fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
    }
}

@Composable
private fun Notice(controller: AppController) {
    controller.notice?.let {
        Surface(color = if (controller.error) Color(0xFFFFEDE7) else Color(0xFFE5F0E8), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (controller.error) Icons.Outlined.Info else Icons.Outlined.CheckCircle, null, Modifier.size(20.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Login(controller: AppController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Column(Modifier.weight(1f).fillMaxHeight().background(Green).padding(56.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("HiALife / 智慧生活", color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(Icons.Outlined.Air, null, Modifier.size(88.dp), tint = Color(0xFFCADEC0))
                    Text("让舒适，\n恰到好处。", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light, lineHeight = 64.sp)
                    Text("从房间温度到每一缕新风\n在你熟悉的设备上，照顾家的日常。", color = Color.White.copy(alpha = .7f), lineHeight = 26.sp)
                }
                Text("ANDROID  /  iOS  /  DESKTOP", color = Color.White.copy(alpha = .6f), fontSize = 12.sp, letterSpacing = 2.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(if (wide) 48.dp else 28.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Brand()
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("欢迎回家", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        Text("登录海信账号，连接你的舒适生活", color = Muted, fontSize = 14.sp)
                    }
                    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !controller.busy,
                        label = { Text("海信账号") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) })
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !controller.busy,
                        label = { Text("密码") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = { IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏密码" else "显示密码")
                        } })
                    Notice(controller)
                    Button(onClick = {
                        controller.login(username, password)
                        password = ""
                    }, enabled = !controller.busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                        if (controller.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("登录", fontSize = 16.sp)
                    }
                    Text("密码仅用于本次登录，不会保存在设备上。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = Outline)
                    OutlinedButton(onClick = { controller.startDemo() }, enabled = !controller.busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text("先体验一下")
                    }
                    TextButton(onClick = { importing = true }, enabled = !controller.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("使用已有会话登录")
                    }
                }
            }
        }
    }
    if (importing) AlertDialog(onDismissRequest = { importing = false; session = "" },
        title = { Text("导入已有会话") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("粘贴本机 session.json 内容，需包含 token、customerId，建议包含 refreshToken。请勿使用他人会话。")
                OutlinedTextField(session, { session = it }, Modifier.fillMaxWidth().heightIn(max = 200.dp), label = { Text("会话 JSON") },
                    visualTransformation = PasswordVisualTransformation())
            }
        }, confirmButton = { TextButton(onClick = {
            controller.importSession(session); session = ""; importing = false
        }, enabled = session.isNotBlank()) { Text("导入并连接") } },
        dismissButton = { TextButton(onClick = { importing = false; session = "" }) { Text("取消") } })
}

@Composable
private fun Dashboard(c: AppController) {
    var logout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Brand()
            Spacer(Modifier.weight(1f))
            if (c.demo) SuggestionChip(onClick = {}, label = { Text("演示模式") })
            IconButton(onClick = { c.refresh() }, enabled = !c.busy) { Icon(Icons.Outlined.Refresh, "刷新状态") }
            IconButton(onClick = { logout = true }, enabled = !c.busy) { Icon(Icons.Outlined.Logout, "退出登录") }
        }
        HorizontalDivider(color = Outline)
        if (c.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green, trackColor = Outline)
        if (c.notice != null) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Notice(c)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Column(Modifier.width(248.dp).fillMaxHeight().background(Color(0xFFECF0E9)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("我的空间", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                    c.devices.forEach { DeviceButton(it, c) }
                    Spacer(Modifier.weight(1f))
                    Text("舒适，从这里开始。", color = Muted, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(if (wide) 32.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("家的舒适，由你掌控", fontSize = if (wide) 28.sp else 25.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (c.demo) "当前为演示数据，所有操作均不会控制真实设备。" else "查看设备状态，调节属于你的理想环境。", color = Muted, fontSize = 13.sp)
                    }
                    if (!wide && c.devices.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { c.devices.forEach { DeviceButton(it, c) } }
                    val device = c.selected
                    if (device == null) Panel {
                        Icon(Icons.Outlined.Devices, null, Modifier.size(40.dp), tint = Muted)
                        Text(if (c.busy) "正在连接你的设备…" else "暂未发现空调", fontSize = 20.sp)
                        Text("请确认海信账号已绑定空调，然后刷新。", color = Muted)
                        OutlinedButton(onClick = { c.refresh() }, enabled = !c.busy) { Text("重新加载") }
                    } else {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(device.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    Text(device.model, color = Muted, fontSize = 11.sp)
                                }
                                StatusDot(device.online)
                            }
                            Temperature(device, c)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("室内温度", color = Muted, fontSize = 12.sp)
                                    Text("${device.state("realTemperature").ifBlank { "—" }} °C", fontSize = 22.sp)
                                }
                                val power = c.capabilities.find { it.code == "PowerControl" }
                                if (power != null) FilledTonalButton(onClick = { c.command(power, mapOf("onAndOff" to if (device.state("powerStatus") == "1") "0" else "1")) },
                                    enabled = !c.busy && device.online, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(18.dp)) {
                                    Icon(Icons.Outlined.PowerSettingsNew, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (device.state("powerStatus") == "1") "关闭空调" else "开启空调")
                                }
                            }
                        }
                        val enabled = !c.busy && device.online
                        Panel {
                            SectionTitle(Icons.Outlined.Tune, "空调设置", "让温度与风感，都刚刚好")
                            CapabilityChoices(c, "Modes", "modes", "运行模式", enabled)
                            CapabilityChoices(c, "WindSpeed", "windSpeed", "风速", enabled, "autoWind")
                        }
                        if (c.capabilities.any { it.code == "VentilationWindSwitch" }) Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { SectionTitle(Icons.Outlined.Air, "新风", "给房间换一口新鲜空气") }
                                val switch = c.capabilities.first { it.code == "VentilationWindSwitch" }
                                Switch(checked = device.state("ventilationWindSwitch") == "1", onCheckedChange = {
                                    c.command(switch, mapOf("ventilationWindSwitch" to if (it) "1" else "0"))
                                }, enabled = enabled)
                            }
                            CapabilityChoices(c, "VentilationModes", "ventilationModes", "循环方式", enabled)
                            CapabilityChoices(c, "VentilationWindSpeed", "ventilationWindSpeed", "新风风速", enabled, "autoVentilationWind")
                        }
                        if (!device.online) Text("设备当前离线，连接恢复后即可操作。", color = MaterialTheme.colorScheme.error)
                        Text(if (c.demo) "HiALife · 演示空间" else "设备状态来自云端上报 · 操作后自动回读确认", color = Muted, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text(if (c.demo) "离开演示？" else "退出登录？") },
        text = { Text(if (c.demo) "返回账号登录页面。" else "将清除此设备保存的会话。") },
        confirmButton = { TextButton(onClick = { logout = false; c.logout() }) { Text("确认退出") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
}

@Composable
private fun DeviceButton(device: Device, c: AppController) {
    FilterChip(selected = c.selected?.id == device.id, onClick = { c.select(device) }, enabled = !c.busy,
        label = { Text(device.name) }, leadingIcon = { Icon(Icons.Outlined.AcUnit, null, Modifier.size(18.dp)) })
}

@Composable
private fun StatusDot(online: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(if (online) Green else Muted, CircleShape))
        Text(if (online) "在线" else "离线", color = if (online) Green else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).border(1.dp, Outline, RoundedCornerShape(24.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Green)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Temperature(device: Device, c: AppController) {
    val cap = c.capabilities.find { it.code == "TemperatureControl" }
    val range = cap?.attribute("setTemperature")?.range
    val current = device.state("setTemperature").toFloatOrNull()
    val enabled = !c.busy && device.online && current != null && range != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        OutlinedIconButton(onClick = { if (cap != null && current != null && range != null) c.command(cap, mapOf("setTemperature" to (current - 1).coerceIn(range).toString())) },
            enabled = enabled && current!! > range!!.start, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Remove, "降低温度") }
        Box(Modifier.weight(1f).widthIn(max = 300.dp).height(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(216.dp)) {
                val inset = 12.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Outline, 140f, 260f, false, Offset(inset, inset), arcSize, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                val progress = if (current != null && range != null && range.endInclusive > range.start)
                    ((current - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f) else 0f
                drawArc(Green, 140f, 260f * progress, false, Offset(inset, inset), arcSize, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("设定温度", color = Muted, fontSize = 12.sp)
                Text(current?.let { if (it % 1f == 0f) "${it.toInt()}°" else "$it°" } ?: "—", fontSize = 58.sp, fontWeight = FontWeight.Light)
                Text(if (device.state("powerStatus") == "1") "舒适运行中" else "空调已关闭", color = Green, fontSize = 12.sp)
            }
        }
        OutlinedIconButton(onClick = { if (cap != null && current != null && range != null) c.command(cap, mapOf("setTemperature" to (current + 1).coerceIn(range).toString())) },
            enabled = enabled && current!! < range!!.endInclusive, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Add, "升高温度") }
    }
}

@Composable
private fun CapabilityChoices(c: AppController, code: String, attribute: String, label: String, enabled: Boolean, autoAttribute: String? = null) {
    val capability = c.capabilities.find { it.code == code } ?: return
    val attr = capability.attribute(attribute) ?: return
    val device = c.selected ?: return
    val auto = autoAttribute != null && capability.attribute(autoAttribute) != null
    val current = device.state(capability.stateKeys[attribute] ?: attribute)
    val isAuto = auto && device.state(capability.stateKeys[autoAttribute] ?: autoAttribute!!) == "1"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (auto) FilterChip(selected = isAuto, onClick = { c.command(capability, mapOf(autoAttribute!! to "1")) }, label = { Text("自动") }, enabled = enabled)
            attr.choices.forEach { choice ->
                FilterChip(selected = !isAuto && current == choice.value, enabled = enabled,
                    onClick = { c.command(capability, buildMap {
                        put(attribute, choice.value); if (auto) put(autoAttribute!!, "0")
                    }) }, label = { Text(choice.label) })
            }
        }
    }
}
