package zuiwan.model

enum class NoticeKind {
    SYNC,
    LOADING,
    SUCCESS,
    ERROR,
    INFO,
    UPDATE,
}

data class NoticeState(
    val id: Long = 0L,
    val kind: NoticeKind = NoticeKind.INFO,
    val title: String = "",
    val subtitle: String = "",
    val progress: Float? = null,
    val visible: Boolean = false,
    val actionLabel: String = "",
    val onAction: (() -> Unit)? = null,
)
