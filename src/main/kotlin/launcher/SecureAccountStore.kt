package launcher

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Windows Data Protection API (DPAPI) bindings for secure credential storage.
 * Uses CryptProtectData/CryptUnprotectData to encrypt/decrypt data with the user's logon credentials.
 */
interface Crypt32 : StdCallLibrary {
    companion object {
        val INSTANCE: Crypt32 = Native.load("crypt32", Crypt32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    // dwFlags
    const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    // DATA_BLOB structure
    class DATA_BLOB : com.sun.jna.Structure {
        var cbData: Int
        var pbData: Pointer

        constructor() : this(0, Pointer.NULL)
        constructor(data: ByteArray) : this() {
            cbData = data.size
            if (cbData > 0) {
                pbData = com.sun.jna.Memory(cbData.toLong())
                pbData.write(0, data, 0, cbData)
            }
        }

        fun toByteArray(): ByteArray {
            if (cbData <= 0 || pbData == Pointer.NULL) return ByteArray(0)
            return pbData.getByteArray(0, cbData)
        }

        override fun getFieldOrder(): MutableList<String> = mutableListOf("cbData", "pbData")
    }

    /**
     * Encrypts data using DPAPI (user's logon credentials).
     * @param pDataIn Data to encrypt
     * @param szDataDescr Optional description (not used)
     * @param pOptionalEntropy Optional additional entropy (not used)
     * @param pvReserved Reserved (must be null)
     * @param pPromptStruct Prompt structure (not used with CRYPTPROTECT_UI_FORBIDDEN)
     * @param dwFlags Flags (use CRYPTPROTECT_UI_FORBIDDEN)
     * @param pDataOut Encrypted data output
     * @return true on success
     */
    fun CryptProtectData(
        pDataIn: DATA_BLOB,
        szDataDescr: String?,
        pOptionalEntropy: DATA_BLOB?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DATA_BLOB
    ): Boolean

    /**
     * Decrypts data encrypted with CryptProtectData.
     * @param pDataIn Encrypted data
     * @param ppszDataDescr Description output (not used)
     * @param pOptionalEntropy Optional additional entropy (not used)
     * @param pvReserved Reserved (must be null)
     * @param pPromptStruct Prompt structure (not used with CRYPTPROTECT_UI_FORBIDDEN)
     * @param dwFlags Flags (use CRYPTPROTECT_UI_FORBIDDEN)
     * @param pDataOut Decrypted data output
     * @return true on success
     */
    fun CryptUnprotectData(
        pDataIn: DATA_BLOB,
        ppszDataDescr: PointerByReference?,
        pOptionalEntropy: DATA_BLOB?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DATA_BLOB
    ): Boolean

    /**
     * Frees memory allocated by CryptProtectData/CryptUnprotectData.
     */
    fun LocalFree(hMem: Pointer): Pointer
}

/**
 * Secure account store using Windows DPAPI.
 * Stores the refresh token encrypted with the user's logon credentials.
 * The encrypted blob is tied to the user profile and machine (optionally).
 */
object SecureAccountStore {
    private val file = File(Dirs.root, "microsoft_account_secure.dat")
    private val metadataFile = File(Dirs.root, "microsoft_account.json")
    private val gson = Gson()

    data class StoredAccount(
        val name: String,
        val uuid: String,
        val refreshToken: String
    )

    data class AccountMetadata(
        val name: String,
        val uuid: String
    )

    fun load(): StoredAccount? {
        // Try loading from secure store first
        if (file.isFile) {
            val encrypted = file.readBytes()
            val decrypted = try {
                decrypt(encrypted)
            } catch (e: Exception) {
                return null // Failed to decrypt, don't fall back to plaintext
            }
            return try {
                val json = JsonParser.parseString(decrypted.toString(StandardCharsets.UTF_8)).asJsonObject
                StoredAccount(
                    name = json.get("name").asString,
                    uuid = json.get("uuid").asString,
                    refreshToken = json.get("refreshToken").asString
                )
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun save(a: StoredAccount) {
        Dirs.root.mkdirs()

        // Save encrypted refresh token + metadata together
        val json = JsonObject().apply {
            addProperty("name", a.name)
            addProperty("uuid", a.uuid)
            addProperty("refreshToken", a.refreshToken)
        }
        val plaintext = gson.toJson(json).toByteArray(StandardCharsets.UTF_8)
        val encrypted = encrypt(plaintext)
        file.writeBytes(encrypted)

        // Save non-sensitive metadata separately for display without decryption
        val metaJson = JsonObject().apply {
            addProperty("name", a.name)
            addProperty("uuid", a.uuid)
        }
        metadataFile.writeText(gson.toJson(metaJson))
    }

    fun clear() {
        file.delete()
        metadataFile.delete()
    }

    /**
     * Gets account metadata (name, uuid) without decrypting the refresh token.
     * Useful for displaying account info in UI without exposing credentials.
     */
    fun loadMetadata(): AccountMetadata? {
        if (!metadataFile.isFile) return null
        return try {
            val json = JsonParser.parseString(metadataFile.readText()).asJsonObject
            AccountMetadata(
                name = json.get("name").asString,
                uuid = json.get("uuid").asString
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val inBlob = Crypt32.DATA_BLOB(data)
        val outBlob = Crypt32.DATA_BLOB()
        val success = Crypt32.INSTANCE.CryptProtectData(
            inBlob,
            null,
            null,
            Pointer.NULL,
            Pointer.NULL,
            Crypt32.CRYPTPROTECT_UI_FORBIDDEN,
            outBlob
        )
        if (!success) {
            val error = com.sun.jna.Native.getLastError()
            throw SecurityException("DPAPI encryption failed (error $error)")
        }
        return outBlob.toByteArray()
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        val inBlob = Crypt32.DATA_BLOB(encrypted)
        val outBlob = Crypt32.DATA_BLOB()
        val success = Crypt32.INSTANCE.CryptUnprotectData(
            inBlob,
            null,
            null,
            Pointer.NULL,
            Pointer.NULL,
            Crypt32.CRYPTPROTECT_UI_FORBIDDEN,
            outBlob
        )
        if (!success) {
            val error = com.sun.jna.Native.getLastError()
            throw SecurityException("DPAPI decryption failed (error $error) - the encrypted data may be corrupted or from a different user/machine")
        }
        return outBlob.toByteArray()
    }
}