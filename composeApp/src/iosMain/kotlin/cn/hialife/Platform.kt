@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cn.hialife

import cn.hialife.data.APP_SECRET
import cn.hialife.ui.App
import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*

actual fun createHttpClient() = HttpClient(Darwin) {
    install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
}
actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
actual fun randomId(): String = NSUUID().UUIDString

private fun ByteArray.asData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
private fun NSData.asBytes(): ByteArray = ByteArray(length.toInt()).also { target ->
    if (target.isNotEmpty()) target.usePinned { getBytes(it.addressOf(0), length) }
}
actual fun md5Base64(bytes: ByteArray): String {
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
    bytes.usePinned { input -> digest.usePinned { output -> CC_MD5(input.addressOf(0), bytes.size.toUInt(), output.addressOf(0).reinterpret()) } }
    return digest.asData().base64EncodedStringWithOptions(0u)
}
actual fun encryptLogin(value: String): String {
    val key = APP_SECRET.encodeToByteArray()
    val input = value.encodeToByteArray()
    val output = ByteArray(input.size + kCCBlockSizeAES128.toInt())
    return memScoped {
        val count = alloc<ULongVar>()
        val result = key.usePinned { k -> input.usePinned { i -> output.usePinned { o ->
            CCCrypt(kCCEncrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding, k.addressOf(0), key.size.toULong(),
                k.addressOf(0), if (input.isNotEmpty()) i.addressOf(0) else null, input.size.toULong(),
                o.addressOf(0), output.size.toULong(), count.ptr)
        } } }
        check(result == kCCSuccess) { "登录加密失败" }
        output.copyOf(count.value.toInt()).asData().base64EncodedStringWithOptions(0u)
    }
}

class IosSessionStore : SessionStore {
    private fun dictionary(): CFMutableDictionaryRef = CFDictionaryCreateMutable(null, 0,
        kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
    private fun query(): CFMutableDictionaryRef {
        val attrs = dictionary()
        CFDictionarySetValue(attrs, kSecClass, kSecClassGenericPassword)
        val service = CFStringCreateWithCString(null, "cn.hialife.session", kCFStringEncodingUTF8)
        val account = CFStringCreateWithCString(null, "current", kCFStringEncodingUTF8)
        CFDictionarySetValue(attrs, kSecAttrService, service)
        CFDictionarySetValue(attrs, kSecAttrAccount, account)
        CFRelease(service); CFRelease(account)
        return attrs
    }
    override fun read(): String? = memScoped {
        val attrs = query()
        try {
            CFDictionarySetValue(attrs, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(attrs, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(attrs, result.ptr)
            if (status == errSecItemNotFound) return null
            check(status == errSecSuccess) { "无法读取钥匙串" }
            val data = CFBridgingRelease(result.value) as NSData
            data.asBytes().decodeToString()
        } finally { CFRelease(attrs) }
    }
    override fun write(value: String) {
        val base = query()
        val updated = dictionary()
        val data = CFBridgingRetain(value.encodeToByteArray().asData())
        try {
            CFDictionarySetValue(updated, kSecValueData, data)
            val status = SecItemUpdate(base, updated)
            if (status == errSecItemNotFound) {
                CFDictionarySetValue(base, kSecValueData, data)
                CFDictionarySetValue(base, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                check(SecItemAdd(base, null) == errSecSuccess) { "无法保存钥匙串" }
            } else check(status == errSecSuccess) { "无法更新钥匙串" }
        } finally { CFRelease(data); CFRelease(updated); CFRelease(base) }
    }
    override fun clear() {
        val attrs = query()
        try {
            val status = SecItemDelete(attrs)
            check(status == errSecSuccess || status == errSecItemNotFound) { "无法清除钥匙串" }
        } finally { CFRelease(attrs) }
    }
}

fun MainViewController() = ComposeUIViewController { App(IosSessionStore()) }
