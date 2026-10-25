package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterKind {
    PK,
    LSC,
    HSC,
    LPQ,
    HPQ,
}

@Serializable
data class EqBand(
    val frequency: Double,
    val gainDb: Double = 0.0,
    val q: Double = 1.2,
    val kind: FilterKind = FilterKind.PK,
    val enabled: Boolean = true,
)

@Serializable
data class EqProfile(
    val id: String = "flat",
    val name: String = "Flat",
    val enabled: Boolean = false,
    val preampDb: Double = 0.0,
    val bands: List<EqBand> = defaultbands(),
)

fun defaultbands(): List<EqBand> = listOf(
    EqBand(31.0, 0.0, 0.71, FilterKind.LSC),
    EqBand(62.0, 0.0, 1.0, FilterKind.PK),
    EqBand(125.0, 0.0, 1.1, FilterKind.PK),
    EqBand(250.0, 0.0, 1.2, FilterKind.PK),
    EqBand(500.0, 0.0, 1.2, FilterKind.PK),
    EqBand(1000.0, 0.0, 1.2, FilterKind.PK),
    EqBand(2000.0, 0.0, 1.2, FilterKind.PK),
    EqBand(4000.0, 0.0, 1.2, FilterKind.PK),
    EqBand(8000.0, 0.0, 1.2, FilterKind.PK),
    EqBand(16000.0, 0.0, 0.71, FilterKind.HSC),
)
