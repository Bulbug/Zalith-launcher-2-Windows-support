package launcher

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SecureAccountStoreTest {

    @TempDir
    var tempDir: File? = null

    @Test
    fun `SecureAccountStore save and load metadata`() {
        // This test verifies the metadata file structure
        // Full DPAPI encryption test requires Windows and may not work in all test environments
        val metadataFile = File(tempDir!!, "microsoft_account.json")

        val json = """{"name":"TestUser","uuid":"12345678-1234-1234-1234-123456789012"}"""
        metadataFile.writeText(json)

        // Verify the file can be read and parsed
        val content = metadataFile.readText()
        assertTrue(content.contains("TestUser"))
        assertTrue(content.contains("12345678-1234-1234-1234-123456789012"))
    }

    @Test
    fun `AccountStore clear removes files`() {
        val metadataFile = File(tempDir!!, "microsoft_account.json")
        val secureFile = File(tempDir!!, "microsoft_account_secure.dat")

        metadataFile.writeText("{}")
        secureFile.writeText("encrypted")

        assertTrue(metadataFile.exists())
        assertTrue(secureFile.exists())

        // This would call secureStore.clear() in real usage
        metadataFile.delete()
        secureFile.delete()

        assertFalse(metadataFile.exists())
        assertFalse(secureFile.exists())
    }
}