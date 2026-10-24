package zuiwan.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepState(
    val active: Boolean = false,
    val remainingMs: Long = 0L,
    val finishTrack: Boolean = false,
)

class SleepTimer(
    private val scope: CoroutineScope,
    private val onExpire: () -> Unit,
) {
    private var ticker: Job? = null
    private var targetMs: Long = 0L

    private val stateFlow = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = stateFlow.asStateFlow()

    fun startsleep(minutes: Int, finishCurrentTrack: Boolean = false) {
        ticker?.cancel()
        if (finishCurrentTrack && minutes <= 0) {
            stateFlow.value = SleepState(active = true, remainingMs = 0L, finishTrack = true)
            return
        }
        targetMs = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        stateFlow.value = SleepState(
            active = true,
            remainingMs = targetMs - System.currentTimeMillis(),
            finishTrack = finishCurrentTrack,
        )
        ticker = scope.launch {
            while (true) {
                val left = (targetMs - System.currentTimeMillis()).coerceAtLeast(0L)
                stateFlow.value = stateFlow.value.copy(remainingMs = left)
                if (left <= 0L) {
                    if (!stateFlow.value.finishTrack) {
                        cancelsleep()
                        onExpire()
                    }
                    break
                }
                delay(1_000L)
            }
        }
    }

    fun trackended(): Boolean {
        val current = stateFlow.value
        if (current.active && current.finishTrack && current.remainingMs <= 0L) {
            cancelsleep()
            onExpire()
            return true
        }
        return false
    }

    fun cancelsleep() {
        ticker?.cancel()
        ticker = null
        stateFlow.value = SleepState()
    }
}
