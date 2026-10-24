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
    val profile: AccountProfile? = null,
) {
    val active: Boolean get() = cookie.contains("SAPISID")
}

enum class ThemeChoice(val label: String) {
    DARK("Dark Glass"),
    WHITE("White Glass"),
    BLACK("Pure Black"),
    SYSTEM("System Auto"),
}

enum class AccentChoice(val label: String, val hex: Long) {
    DYNAMIC("Artwork Dynamic", 0xFF38BDF8),
    AZURE("Crystal Azure", 0xFF38BDF8),
    VIOLET("Iris Violet", 0xFFA855F7),
    EMERALD("Mint Emerald", 0xFF10B981),
    ROSE("Velvet Rose", 0xFFF43F5E),
    AMBER("Warm Amber", 0xFFF59E0B),
    INDIGO("Deep Indigo", 0xFF6366F1),
    CORAL("Sunset Coral", 0xFFFB7185),
    CYAN("Electric Cyan", 0xFF06B6D4),
    GOLD("Champagne Gold", 0xFFEAB308),
}
