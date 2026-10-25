package zuiwan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhCommitInfo(
    val id: String = "",
    val message: String = "",
)

@Serializable
data class GhWorkflowRun(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("display_title") val displayTitle: String = "",
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("head_branch") val headBranch: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("head_commit") val headCommit: GhCommitInfo? = null,
)

@Serializable
data class GhRunsPayload(
    @SerialName("workflow_runs") val runs: List<GhWorkflowRun> = emptyList(),
)

data class ReleaseNote(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val apkSizeBytes: Long = 0L,
    val isNightly: Boolean = false,
)

data class UpdateStatus(
    val checking: Boolean = false,
    val hasUpdate: Boolean = false,
    val latest: ReleaseNote? = null,
    val releases: List<ReleaseNote> = emptyList(),
    val lastCheckedAt: Long = 0L,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val readyTag: String = "",
    val readyApkPath: String? = null,
    val error: String? = null,
)
