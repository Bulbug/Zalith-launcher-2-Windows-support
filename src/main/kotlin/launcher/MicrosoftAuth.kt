package launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

class AuthException(message: String) : Exception(message)

class DeviceCode(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val intervalSec: Int,
    val expiresInSec: Int,
)

class MsTokens(val access: String, val refresh: String)
class McSession(val name: String, val uuid: String, val token: String)
class StoredAccount(val name: String, val uuid: String, val refreshToken: String)

/**
 * The Azure app (client) ID is NOT included in the code. Register your own and put it in
 * %APPDATA%\.desklauncher\oauth_client_id.txt, or set the DESK_OAUTH_CLIENT_ID environment variable.
 */
object OAuthClient {
    val file = File(Dirs.root, "oauth_client_id.txt")

    fun id(): String? {
        System.getenv("DESK_OAUTH_CLIENT_ID")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        for (f in listOf(file, File(".oauth_client_id.txt"))) {
            if (f.isFile) f.readText().trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }
}

/** Saved Microsoft login (refresh token). Stored as plain JSON in the app folder, like most launchers do. */
object AccountStore {
    private val file: File get() = File(Dirs.root, "microsoft_account.json")

    fun load(): StoredAccount? {
        if (!file.isFile) return null
        return try {
            val o = parseJson(file.readText())
            StoredAccount(o.str("name")!!, o.str("uuid")!!, o.str("refreshToken")!!)
        } catch (e: Exception) {
            null
        }
    }

    fun save(a: StoredAccount) {
        Dirs.root.mkdirs()
        val o = JsonObject()
        o.addProperty("name", a.name)
        o.addProperty("uuid", a.uuid)
        o.addProperty("refreshToken", a.refreshToken)
        file.writeText(o.toString())
    }

    fun clear() {
        file.delete()
    }
}

/** Microsoft device-code login -> Xbox Live -> XSTS -> Minecraft services. All functions block: call from a background thread. */
object MicrosoftAuth {
    private const val BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
    private const val SCOPE = "XboxLive.signin offline_access"
    private const val FORM = "application/x-www-form-urlencoded"
    private const val JSON = "application/json"

    private fun clientId(): String = OAuthClient.id() ?: throw AuthException(
        "No Microsoft client ID found. Put your Azure app's client ID in ${OAuthClient.file} (see README)."
    )

    fun requestDeviceCode(): DeviceCode {
        val (status, body) = Http.post(
            "$BASE/devicecode",
            Http.form("client_id" to clientId(), "scope" to SCOPE),
            FORM,
        )
        val o = parseJson(body)
        if (status !in 200..299) {
            throw AuthException(o.str("error_description") ?: "Microsoft sign-in failed (HTTP $status)")
        }
        return DeviceCode(
            userCode = o.str("user_code")!!,
            verificationUri = o.str("verification_uri")!!,
            deviceCode = o.str("device_code")!!,
            intervalSec = o.int("interval") ?: 5,
            expiresInSec = o.int("expires_in") ?: 900,
        )
    }

    fun pollForToken(dc: DeviceCode, cancelled: () -> Boolean): MsTokens {
        var interval = dc.intervalSec
        val deadline = System.currentTimeMillis() + dc.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L)
            if (cancelled()) throw AuthException("Sign-in cancelled")
            val (status, body) = Http.post(
                "$BASE/token",
                Http.form(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to clientId(),
                    "device_code" to dc.deviceCode,
                ),
                FORM,
            )
            val o = parseJson(body)
            if (status in 200..299) return MsTokens(o.str("access_token")!!, o.str("refresh_token") ?: "")
            when (o.str("error")) {
                "authorization_pending" -> {}
                "slow_down" -> interval += 5
                "authorization_declined" -> throw AuthException("Sign-in was declined")
                "expired_token" -> throw AuthException("The sign-in code expired, please try again")
                else -> throw AuthException(o.str("error_description") ?: "Sign-in failed (${o.str("error")})")
            }
        }
        throw AuthException("The sign-in code expired, please try again")
    }

    fun refresh(refreshToken: String): MsTokens {
        val (status, body) = Http.post(
            "$BASE/token",
            Http.form(
                "grant_type" to "refresh_token",
                "client_id" to clientId(),
                "refresh_token" to refreshToken,
                "scope" to SCOPE,
            ),
            FORM,
        )
        val o = parseJson(body)
        if (status !in 200..299) {
            throw AuthException(
                if (o.str("error") == "invalid_grant") "Your Microsoft login expired - please sign in again"
                else o.str("error_description") ?: "Couldn't refresh the Microsoft login"
            )
        }
        return MsTokens(o.str("access_token")!!, o.str("refresh_token") ?: refreshToken)
    }

    fun minecraftSession(msAccess: String): McSession {
        // 1) Xbox Live
        val xblReq = JsonObject().apply {
            add("Properties", JsonObject().apply {
                addProperty("AuthMethod", "RPS")
                addProperty("SiteName", "user.auth.xboxlive.com")
                addProperty("RpsTicket", "d=$msAccess")
            })
            addProperty("RelyingParty", "http://auth.xboxlive.com")
            addProperty("TokenType", "JWT")
        }
        val (s1, b1) = Http.post("https://user.auth.xboxlive.com/user/authenticate", xblReq.toString(), JSON)
        if (s1 !in 200..299) throw AuthException("Xbox Live login failed (HTTP $s1)")
        val xblToken = parseJson(b1).str("Token") ?: throw AuthException("Xbox Live gave no token")

        // 2) XSTS
        val xstsReq = JsonObject().apply {
            add("Properties", JsonObject().apply {
                addProperty("SandboxId", "RETAIL")
                add("UserTokens", JsonArray().apply { add(xblToken) })
            })
            addProperty("RelyingParty", "rp://api.minecraftservices.com/")
            addProperty("TokenType", "JWT")
        }
        val (s2, b2) = Http.post("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq.toString(), JSON)
        val xsts = parseJson(b2)
        if (s2 !in 200..299) throw AuthException(xstsError(xsts.get("XErr")?.asLong ?: 0L))
        val xstsToken = xsts.str("Token") ?: throw AuthException("Xbox gave no XSTS token")
        val uhs = xsts.obj("DisplayClaims")?.arr("xui")?.get(0)?.asJsonObject?.str("uhs")
            ?: throw AuthException("Xbox gave no user hash")

        // 3) Minecraft services
        val mcReq = JsonObject().apply { addProperty("identityToken", "XBL3.0 x=$uhs;$xstsToken") }
        val (s3, b3) = Http.post("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq.toString(), JSON)
        if (s3 == 403) {
            throw AuthException(
                "Minecraft refused this app (\"Invalid app registration\"). The Azure app ID has to be approved by Mojang first - see the README."
            )
        }
        if (s3 !in 200..299) throw AuthException("Minecraft login failed (HTTP $s3)")
        val mcToken = parseJson(b3).str("access_token") ?: throw AuthException("Minecraft gave no token")

        // 4) profile (also proves the account owns the game)
        val (s4, b4) = Http.getWithStatus(
            "https://api.minecraftservices.com/minecraft/profile",
            mapOf("Authorization" to "Bearer $mcToken"),
        )
        if (s4 == 404) throw AuthException("This account doesn't own Minecraft: Java Edition (or has no profile yet).")
        if (s4 !in 200..299) throw AuthException("Couldn't load the Minecraft profile (HTTP $s4)")
        val p = parseJson(b4)
        return McSession(p.str("name")!!, p.str("id")!!, mcToken)
    }

    /** Silent login with the saved refresh token. Saves the new (rotated) refresh token. */
    fun accountFromStored(a: StoredAccount): Account {
        val t = refresh(a.refreshToken)
        val s = minecraftSession(t.access)
        AccountStore.save(StoredAccount(s.name, s.uuid, t.refresh))
        return Account(s.name, s.uuid, s.token, "msa")
    }

    private fun xstsError(code: Long): String = when (code) {
        2148916233L -> "This Microsoft account has no Xbox profile yet. Sign in once at xbox.com to create it."
        2148916235L -> "Xbox Live isn't available in your country or region."
        2148916236L, 2148916237L -> "This account needs adult verification on the Xbox website."
        2148916238L -> "This is a child account. An adult has to add it to a Microsoft family first."
        else -> "Xbox login failed (error $code)"
    }
}
