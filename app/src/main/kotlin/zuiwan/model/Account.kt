package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountProfile(
    val name: String,
    val handle: String = "",
    val email: String = "",
    val avatar: String = "",
)

@Serializable
data class SessionData(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val authUser: String = "0",
    val accountName: String = "",
    val accountHandle: String = "",
    val accountEmail: String = "",
    val accountAvatar: String = "",
) {
    val active: Boolean
        get() = cookie.contains("SAPISID")
}

@Serializable
enum class ThemeChoice(val label: String) {
    DARK("Dark"),
    WHITE("White"),
}
