package launcher

import java.util.UUID

/**
 * Phase 1: offline accounts only (can't join online-mode servers).
 * Phase 2: plug Zalith's Microsoft login code in here.
 */
data class Account(
    val name: String,
    val uuid: String,
    val accessToken: String,
    val userType: String,
) {
    companion object {
        fun offline(name: String): Account {
            val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString()
            return Account(name, uuid, "0", "legacy")
        }
    }
}
