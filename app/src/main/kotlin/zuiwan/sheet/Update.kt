package zuiwan.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.zuiwan.BuildConfig
import zuiwan.model.ReleaseNote
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.glass
import zuiwan.ui.iconbtn
import zuiwan.ui.pillbtn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun changelogsheet(
    releases: List<ReleaseNote>,
    loading: Boolean,
    onInstall: (ReleaseNote) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    val uriHandler = LocalUriHandler.current
    val repoReleasesUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glassTokens.glassStrong,
        contentColor = glassTokens.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Changelog",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = glassTokens.ink,
                    )
                    Text(
                        text = "Release notes & version history",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.inkSecondary,
                    )
                }
                iconbtn(
                    icon = ZIcons.Refresh,
                    onClick = onRefresh,
                )
            }

            Spacer(Modifier.height(14.dp))

            when {
                loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        CircularProgressIndicator(color = glassTokens.accent)
                    }
                }

                releases.isEmpty() -> {
                    glass(
                        shape = RoundedCornerShape(22.dp),
                        elevated = true,
                        contentPadding = PaddingValues(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = ZIcons.Notes,
                                contentDescription = null,
                                tint = glassTokens.accent,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Zuiwan v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = glassTokens.ink,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "You are on the latest release build. Future release notes will appear here automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassTokens.inkSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(releases, key = { it.tagName }) { rel ->
                            releasecard(rel, onInstall = { onInstall(rel) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            pillbtn(
                text = "View on GitHub",
                icon = ZIcons.External,
                primary = false,
                onClick = { runCatching { uriHandler.openUri(repoReleasesUrl) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun releasecard(
    release: ReleaseNote,
    onInstall: () -> Unit,
) {
    val glassTokens = LocalGlass.current

    glass(
        shape = RoundedCornerShape(22.dp),
        elevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glassTokens.accentSoft)
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = release.tagName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = glassTokens.accent,
                        )
                    }
                    Text(
                        text = release.publishedAt.substringBefore("T"),
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.inkMuted,
                    )
                }

                iconbtn(
                    icon = ZIcons.Download,
                    size = 32.dp,
                    iconSize = 16.dp,
                    tint = glassTokens.accent,
                    onClick = onInstall,
                )
            }

            notebody(
                raw = release.body.ifBlank { release.title },
            )
        }
    }
}

@Composable
fun notebody(raw: String) {
    val glassTokens = LocalGlass.current
    val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            when {
                line.startsWith("#") -> {
                    val clean = line.trimStart('#').trim()
                    if (clean.isNotBlank()) {
                        Text(
                            text = clean,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = glassTokens.ink,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                line.startsWith("- ") || line.startsWith("* ") -> {
                    val clean = line.substring(2).trim()
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(glassTokens.accent),
                        )
                        Text(
                            text = clean,
                            style = MaterialTheme.typography.bodySmall,
                            color = glassTokens.inkSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                else -> {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.inkSecondary,
                    )
                }
            }
        }
    }
}
