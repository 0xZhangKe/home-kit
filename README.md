已找到海信爱家的设备查询、新风控制、请求签名和令牌续期接口，并用你的已登录会话完成了查询和续期验证。目标设备确认为 **KFR-35G/X520U-X1(1X18)**，产品编码 `1B000001357`。

**目前完成到什么程度**

| 项目 | 结果 |
| --- | --- |
| 设备列表、实时状态、型号和能力 | 本机 Python 直接请求成功，返回 `SUCCESS` |
| 请求签名 | 已从应用内网页脚本提取，实际通过服务端校验 |
| 令牌刷新 | 已成功换取新 accessToken 和 refreshToken，并用新令牌重新读取设备 |
| 新风开关与内外循环取值 | 由该型号的能力接口确认，与应用控制面板代码相符 |
| 控制 HTTP 请求结构 | 从 APK 的 Flutter 编译代码还原，尚未向空调发送 |
| 密码登录 | 已找到可参考的公开实现并写入脚本；未用你的账号密码实测 |
| 每天 17:00—20:00 自动运行 | 尚未部署、启用或创建云端规则 |

**接口及鉴权**

物联网接口的共同主机是 `https://iot-aihome.hismarttv.com`，以下物联网接口均使用 `POST` 和 JSON 请求体。

| 用途 | 地址 | 验证状态 |
| --- | --- | --- |
| 设备列表及状态 | `/4.0/iot/devices/list` | 实际成功 |
| 型号能力 | `/4.0/iot/devices/function/getByProduct` | 实际成功 |
| 请求设备上报最新状态 | `/4.0/iot/state/refreshDeviceState` | 实际成功；稍后通过列表回读 |
| 发送控制指令 | `/4.0/iot/devices/execute` | 静态代码确认，未发送控制 |
| 控制面板资源 | `/4.0/iot/layout/controlFile/list` | 实际成功 |
| 云定时列表、配置 | `/4.0/iot/cloudTimers/list`、`/4.0/iot/cloudTimers/config/get` | 实际成功 |
| 云定时增改、启停 | `/4.0/iot/cloudTimers/add`、`update`、`updateStatus` | 面板代码确认，未写入 |

请求头中的 `appKey` 使用公开网页客户端的 `commonweb`。`X-Sign-For` 的算法是：

```text
Base64(MD5(实际发送的紧凑 JSON UTF-8 字节 + 网页客户端 appSecret 字节))
```

MD5 使用原始的 16 字节摘要，再做 Base64；不是将十六进制字符串做 Base64。计算签名后必须发送完全相同的 JSON 字节。客户端常量已写在 `hialife.py`，没有把用户令牌写入源码。

验证证据：设备列表请求不带签名时返回 HTTP 403 / `no signature found.`；正确签名但不带令牌时返回 `accessToken is lost`；带本地会话令牌后返回 `payload.status = SUCCESS`，且型号与用户提供的信息一致。

常见 JSON 外层如下，控制请求还带 `sourceName: "smartlifeAppH5"`：

```json
{
  "appVersion": "7.0.9.3",
  "appType": 1,
  "header": {"messageId": "<随机 UUID>", "timestamp": 0, "languageId": 0},
  "accessToken": "<本地会话令牌>",
  "payload": {}
}
```

`timestamp` 应使用当前 Unix 毫秒时间。`deviceId` 应从列表返回值读取，不要用产品编码、Wi-Fi MAC 或机身序列号替代。

**新风外循环的具体指令**

该型号能力接口返回：

| 功能 | command 后缀 | params 字段 | 取值 |
| --- | --- | --- | --- |
| 新风开关 | `SetVentilationWindSwitch` | `ventilationWindSwitch` | `"0"` 关闭，`"1"` 开启 |
| 新风模式 | `SetVentilationModes` | `ventilationModes` | `"1"` 外循环，`"2"` 内循环 |
| 新风风速 | `SetVentilationWindSpeed` | `autoVentilationWind`、`ventilationWindSpeed` | 自动风开关；低/中/高风分别为 `"1"`/`"2"`/`"3"` |

所有 command 都有前缀 `connector.device.command.`。

17:00 的控制 `payload` 草案如下。应用面板在新风关闭时设置新风模式，会先追加开启新风指令；脚本沿用这个顺序。此请求体已根据代码还原，但其设备执行效果尚未实测。

```json
{
  "sourceType": "20",
  "exclusionFlag": 1,
  "customerId": "<用户 ID>",
  "deviceId": "<设备列表返回的 deviceId>",
  "partnerId": "1001",
  "actions": [
    {
      "command": "connector.device.command.SetVentilationWindSwitch",
      "params": {"ventilationWindSwitch": "1"}
    },
    {
      "command": "connector.device.command.SetVentilationModes",
      "params": {"ventilationModes": "1"}
    }
  ]
}
```

20:00 只发送下面这一项 action：

```json
{
  "command": "connector.device.command.SetVentilationWindSwitch",
  "params": {"ventilationWindSwitch": "0"}
}
```

这里关闭的是新风。脚本不发送整机 `Power` 指令，也不修改制冷、制热或温度设置。即时控制的 params 使用上面的直接字符串；云定时记录中的 params 则使用 `{ "value": "…" }` 嵌套格式，两者不能混用。

**登录和续期**

实际观察到 Android 应用启动时使用 `https://auth-mobile.hismarttv.com/aaa/sign_in_refreshtoken`；该 Android SDK 分支的请求体没有完整还原。

另一个续期入口已通过你的会话实测成功：

```text
POST https://bas-wg.hismarttv.com/aaa/refresh_token2
Content-Type: application/x-www-form-urlencoded

refreshToken=<当前刷新令牌>&appKey=2&format=1
```

本次返回是包含一个对象的 JSON 数组，`resultCode = 0`，有新的 `token` 和 `refreshToken`；两个令牌均发生了轮换。新令牌再次查询设备成功。本次返回的有效期字段分别为 259200 秒（3 天）和 15552000 秒（180 天）。客户端必须保存最新的一对令牌，不能一直使用最初抓到的值；不能据单次响应保证账号永久免登录。

密码登录的参考入口为 `POST https://portal-account.hismarttv.com/mobile/se/signon`。账号放在 `loginName`，密码放在 `signature`；两者先用公开客户端密钥做 AES-256-CBC / PKCS#7，再做 Base64。IV 为同一密钥的前 16 字节。JSON 还包含 `serverCode="9501"`、`distributeId="2001"`、`termType=2`，并使用前述 JSON 签名。完整查询参数见脚本 `login()`。

这两个替代账号入口来自公开实现的交叉核对，其中续期已在本机验证，密码登录没有实测：[HisenseHA 登录实现](https://github.com/manymuch/HisenseHA/blob/main/custom_components/hisense/pyhisenseapi.py)、[ha_hisense 续期实现](https://github.com/sfairy/ha_hisense/blob/main/custom_components/hisense/api.py)。首次需要密码时，脚本在本机终端交互输入，不保存密码。

**现成脚本**

工作目录：`/Users/zhangke/Personal/code/hialife`。

源码为 `hialife.py`；已将本次验证成功的会话保存在该目录的 `.local/session.json`，权限为 0600，并加入 `.gitignore`。中间日志、反编译产物在 `work/`，没有打包进可分享的交付文件。

在工作目录中可运行：

```bash
uv run hialife.py status
uv run hialife.py functions
uv run hialife.py plan
```

以上命令只读取信息；`plan` 输出每天北京时间 17:00、20:00 的脱敏控制请求草案。`status` 和 `plan` 已成功运行，读取结果为整机关机、新风关闭、模式为外循环。

以下命令会实际控制设备，本轮没有运行：

```bash
uv run hialife.py on
uv run hialife.py off
```

脚本支持令牌临期刷新、认证失败后一次续期重试、会话原子保存，以及控制后最长 20 秒的状态回读。`on/off` 路径尚需一次实机验证，不能把代码还原等同于设备效果验证。

**自动化接入的结论**

你的需求可以表达为每天北京时间 17:00 调用 `on`，20:00 调用 `off`。实际部署前还需要验证新风开关、外循环和关闭的状态回读，确定执行机器及其持续在线条件，再配置调度。

应用确实有云定时和云场景，但本次该型号的 `cloudTimers/config/get` 可选命令，以及场景配置的 action 命令列表，均没有列出新风命令。因此不能保证绕过界面直接提交新风云定时会被接受或执行。本轮没有新增、修改或启用任何云端定时。

**分析依据**

APK：`HIAILife.apk`，包名 `com.hisense.ms.fly2tv`，版本 `7.0.9.3`，SHA-256：`244dfe3efad1ef03de563d82b06f298db743c0e03aecb07ef35448c75b276265`。Java 层采用百度加固，但 Flutter ARM64 编译产物仍可解析；已用 JADX 和 [Blutter](https://github.com/worawit/blutter) 分析。

控制入口证据是 Flutter 的 `JhkDeviceNetworkAgent.execute`（地址 `0xc51490`，接口字符串在 `0xc51658`）；请求模型是 `JhkDeviceExecuteRequestPayload.toJson`（`0x18f3358`）；新风动作取值来自服务器针对产品 `1B000001357` 返回的能力定义。

另外取得该型号实际使用的 H5 控制面板 `3.1.14.7`，下载 ZIP 已通过服务端给出的 SHA-256 校验：`72ab3adb56132f0d48bdbe632ebc61255b2c4e524cd43b7a7957f25cfb471748`。签名实现来自应用引用的[网页脚本](https://wechattv-download.hismarttv.com/nodewebres/wechattvnew/aiot-service/6.3.36-3/js/chunk-vendors.2e03480d.js)。
