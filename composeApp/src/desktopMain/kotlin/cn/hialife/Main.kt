package cn.hialife

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import cn.hialife.ui.App
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "HiALife · 舒适生活",
        state = rememberWindowState(width = 1100.dp, height = 820.dp)) {
        App(DesktopSessionStore())
    }
}

/** Desktop tokens are private local files; account passwords are never persisted. */
class DesktopSessionStore : SessionStore {
    private val directory = Path.of(System.getProperty("user.home"), ".hialife")
    private val file = directory.resolve("session.json")
    override fun read(): String? = if (Files.isRegularFile(file)) Files.readString(file) else null
    override fun write(value: String) {
        Files.createDirectories(directory)
        val posix = Files.getFileStore(directory).supportsFileAttributeView("posix")
        if (posix) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val tmp = if (posix) Files.createTempFile(directory, "session", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        else Files.createTempFile(directory, "session", ".tmp")
        try {
            Files.writeString(tmp, value)
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(tmp) }
    }
    override fun clear() { Files.deleteIfExists(file) }
}
