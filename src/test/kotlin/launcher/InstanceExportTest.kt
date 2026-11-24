package launcher

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstanceExportTest {

    @TempDir
    var tempDir: File? = null

    @Test
    fun `importPack rejects zip-slip paths with directory traversal`() {
        val temp = tempDir!!
        val maliciousZip = File(temp, "malicious.zip")

        // Create a ZIP with a path traversal entry
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            // Normal entry
            zip.putNextEntry(ZipEntry("instance.json"))
            val json = JsonObject().apply {
                addProperty("name", "Test")
                addProperty("mc", "1.20.1")
                addProperty("loader", "VANILLA")
            }
            zip.write(json.toString().toByteArray())
            zip.closeEntry()

            // Malicious entry with directory traversal
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("malicious".toByteArray())
            zip.closeEntry()
        }

        // This should throw SecurityException
        assertThrows(SecurityException::class.java) {
            InstanceExport.importPack(maliciousZip) { }
        }
    }

    @Test
    fun `importPack rejects absolute paths`() {
        val temp = tempDir!!
        val maliciousZip = File(temp, "malicious_absolute.zip")

        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            zip.putNextEntry(ZipEntry("instance.json"))
            val json = JsonObject().apply {
                addProperty("name", "Test")
                addProperty("mc", "1.20.1")
                addProperty("loader", "VANILLA")
            }
            zip.write(json.toString().toByteArray())
            zip.closeEntry()

            // Absolute path (Windows style)
            zip.putNextEntry(ZipEntry("C:\\Windows\\system32\\malicious.txt"))
            zip.write("malicious".toByteArray())
            zip.closeEntry()
        }

        assertThrows(SecurityException::class.java) {
            InstanceExport.importPack(maliciousZip) { }
        }
    }

    @Test
    fun `importPack rejects paths with drive letters`() {
        val temp = tempDir!!
        val maliciousZip = File(temp, "malicious_drive.zip")

        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            zip.putNextEntry(ZipEntry("instance.json"))
            val json = JsonObject().apply {
                addProperty("name", "Test")
                addProperty("mc", "1.20.1")
                addProperty("loader", "VANILLA")
            }
            zip.write(json.toString().toByteArray())
            zip.closeEntry()

            // Path with drive letter
            zip.putNextEntry(ZipEntry("D:/evil.txt"))
            zip.write("malicious".toByteArray())
            zip.closeEntry()
        }

        assertThrows(SecurityException::class.java) {
            InstanceExport.importPack(maliciousZip) { }
        }
    }

    @Test
    fun `importPack accepts valid package`() {
        val temp = tempDir!!
        val validZip = File(temp, "valid.zip")

        ZipOutputStream(FileOutputStream(validZip)).use { zip ->
            zip.putNextEntry(ZipEntry("instance.json"))
            val json = JsonObject().apply {
                addProperty("name", "Valid Instance")
                addProperty("mc", "1.20.1")
                addProperty("loader", "FABRIC")
                addProperty("loaderVersion", "0.15.11")
            }
            zip.write(json.toString().toByteArray())
            zip.closeEntry()

            // Normal mod file
            zip.putNextEntry(ZipEntry("mods/some-mod.jar"))
            zip.write("fake mod content".toByteArray())
            zip.closeEntry()
        }

        // This should NOT throw
        val instance = InstanceExport.importPack(validZip) { }
        assertEquals("Valid Instance", instance.name)
        assertEquals("1.20.1", instance.mc)
        assertEquals(LoaderKind.FABRIC, instance.loader)
    }

    @Test
    fun `importPack rejects empty path entries`() {
        val temp = tempDir!!
        val maliciousZip = File(temp, "malicious_empty.zip")

        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            zip.putNextEntry(ZipEntry("instance.json"))
            val json = JsonObject().apply {
                addProperty("name", "Test")
                addProperty("mc", "1.20.1")
                addProperty("loader", "VANILLA")
            }
            zip.write(json.toString().toByteArray())
            zip.closeEntry()

            // Empty path entry
            zip.putNextEntry(ZipEntry(""))
            zip.write("malicious".toByteArray())
            zip.closeEntry()
        }

        assertThrows(SecurityException::class.java) {
            InstanceExport.importPack(maliciousZip) { }
        }
    }

    @Test
    fun `safeResolve handles nested directory traversal`() {
        val temp = tempDir!!
        val maliciousZip = File(temp, "malicious_nested.zip")

        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            zip.putNextEntry(ZipEntry("instance.json"))
            val json = JsonObject().apply {
                addProperty("name", "Test")
                addProperty("mc", "1.20.1")
                addProperty("loader", "VANILLA")
            }
            zip.write(json.toString().toByteArray())
            zip.closeEntry()

            // Nested traversal
            zip.putNextEntry(ZipEntry("mods/../../../escape.txt"))
            zip.write("malicious".toByteArray())
            zip.closeEntry()
        }

        assertThrows(SecurityException::class.java) {
            InstanceExport.importPack(maliciousZip) { }
        }
    }
}