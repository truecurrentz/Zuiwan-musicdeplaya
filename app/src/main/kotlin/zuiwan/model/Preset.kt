package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterKind {
    PEAKING,
    LOWSHELF,
    HIGHSHELF,
}

@Serializable
data class EqBand(
    val frequency: Double,
    val gain: Double,
    val q: Double = 1.41,
    val kind: FilterKind = FilterKind.PEAKING,
)

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val preamp: Double = 0.0,
    val bands: List<EqBand>,
)

fun defaultpresets(): List<EqPreset> {
    val freqs = listOf(32.0, 64.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    fun build(id: String, name: String, preamp: Double, gains: List<Double>): EqPreset =
        EqPreset(
            id = id,
            name = name,
            preamp = preamp,
            bands = freqs.mapIndexed { idx, hz ->
                val kind = when (idx) {
                    0 -> FilterKind.LOWSHELF
                    freqs.lastIndex -> FilterKind.HIGHSHELF
                    else -> FilterKind.PEAKING
                }
                EqBand(frequency = hz, gain = gains.getOrElse(idx) { 0.0 }, q = 1.2, kind = kind)
            },
        )

    return listOf(
        build("flat", "Flat", 0.0, listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        build("bass", "Deep Bass", -2.5, listOf(6.0, 5.2, 3.8, 1.5, 0.0, -0.5, 0.2, 1.0, 1.5, 1.8)),
        build("vocal", "Vocal Clarity", -1.5, listOf(-2.0, -1.5, -0.5, 1.2, 3.2, 4.0, 3.4, 1.8, 0.6, 0.0)),
        build("studio", "Studio Reference", -1.0, listOf(1.5, 1.0, 0.4, -0.4, -0.6, 0.2, 1.2, 2.0, 2.4, 2.0)),
        build("acoustic", "Acoustic", -1.5, listOf(3.0, 2.6, 1.6, 0.6, 1.4, 1.6, 2.4, 3.0, 2.6, 1.6)),
        build("electronic", "Electronic", -2.5, listOf(5.0, 4.4, 1.8, -0.6, -1.4, 0.8, 2.2, 3.4, 4.2, 4.5)),
        build("rock", "Rock", -2.0, listOf(4.2, 3.4, 1.8, -0.8, -1.2, 0.8, 2.6, 3.6, 3.8, 3.6)),
        build("jazz", "Warm Jazz", -1.5, listOf(3.2, 2.5, 1.2, 1.8, -1.0, -1.0, 0.4, 1.6, 2.4, 2.8)),
        build("classical", "Classical", -1.5, listOf(3.4, 2.6, 1.6, 0.4, -0.8, -0.8, 0.2, 1.8, 2.8, 3.4)),
        build("night", "Late Night", -1.0, listOf(2.4, 2.0, 1.0, 0.5, 1.2, 1.5, 0.6, -0.8, -1.6, -2.2)),
    )
}
