package com.example.hisab.ui.components.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.ui.theme.HisabTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    currentEmoji: String,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = HisabTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<EmojiCategory?>(null) }

    val recentStore = remember { RecentEmojiStore(context) }
    val recentEmojis by recentStore.recentEmojis.collectAsState(initial = emptyList())

    val trimmedQuery = searchQuery.trim()

    val displayList: List<EmojiEntry> = remember(trimmedQuery, selectedCategory, recentEmojis) {
        if (trimmedQuery.isNotEmpty()) {
            EmojiCatalog.search(trimmedQuery)
        } else if (selectedCategory == null) {
            // Default view: Recent on top if available, then Smileys as preview? Actually show Smileys by default.
            // To keep compact, show Recent section implicitly via category bar; default grid shows Smileys.
            EmojiCatalog.byCategory[EmojiCategory.SMILEYS].orEmpty()
        } else {
            EmojiCatalog.byCategory[selectedCategory].orEmpty()
        }
    }

    val showRecentRow = trimmedQuery.isEmpty() && selectedCategory == null && recentEmojis.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // ── Header ──
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.cardBorder.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Choose Emoji",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to select • Search to filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Search ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("🔍  Search emojis", color = colors.textTertiary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedContainerColor = colors.innerSurface,
                    unfocusedContainerColor = colors.innerSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Category bar (compact icons) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val categories: List<Pair<String, EmojiCategory?>> = buildList {
                    add("Recent" to null)
                    EmojiCategory.all.forEach { cat -> add(cat.icon to cat) }
                }
                // For Recent we use history icon text "🕘"
                categories.forEachIndexed { index, (label, cat) ->
                    val isSelected = when {
                        trimmedQuery.isNotEmpty() -> false
                        cat == null && selectedCategory == null -> true
                        cat != null && selectedCategory == cat -> true
                        else -> false
                    }
                    val iconText = if (index == 0) "🕘" else cat!!.icon
                    val contentDesc = if (index == 0) "Recent" else cat!!.label

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else colors.surfaceCard
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.7.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else colors.cardBorder.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(role = Role.Button) {
                                if (trimmedQuery.isNotEmpty()) {
                                    searchQuery = ""
                                }
                                selectedCategory = cat
                            }
                            .semantics {
                                this.contentDescription = contentDesc
                                this.selected = isSelected
                                this.role = Role.Button
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = iconText,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category label
            if (trimmedQuery.isEmpty()) {
                val label = when {
                    selectedCategory == null -> if (recentEmojis.isNotEmpty()) "Recent" else EmojiCategory.SMILEYS.label
                    else -> selectedCategory!!.label
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Text(
                    text = if (displayList.isEmpty()) "No results for \"$trimmedQuery\"" else "${displayList.size} results",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            // ── Grid / Empty ──
            when {
                trimmedQuery.isNotEmpty() && displayList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔍", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No emojis found",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try a different keyword",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                else -> {
                    // Show recent row above grid when in default state? We already have category bar for Recent.
                    // If selectedCategory == null and not searching, the grid shows Smileys; recent is accessible via category.
                    // To satisfy "Recent emojis" requirement more visibly, show a compact recent strip above grid when default.
                    Column {
                        if (showRecentRow) {
                            Text(
                                text = "Recently used",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentEmojis.take(10).forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceCard)
                                            .border(0.7.dp, colors.cardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                scope.launch {
                                                    recentStore.addRecent(emoji)
                                                }
                                                onEmojiSelected(emoji)
                                                onDismiss()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(displayList) { entry ->
                                val isCurrent = entry.emoji == currentEmoji
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            else colors.surfaceCard
                                        )
                                        .border(
                                            width = if (isCurrent) 1.4.dp else 0.6.dp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else colors.cardBorder.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                recentStore.addRecent(entry.emoji)
                                            }
                                            onEmojiSelected(entry.emoji)
                                            onDismiss()
                                        }
                                        .semantics {
                                            contentDescription = "${entry.name} ${entry.emoji}"
                                            selected = isCurrent
                                            role = Role.Button
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = entry.emoji,
                                        fontSize = 22.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer hint
            Text(
                text = "Choose broadly — Hisab renders your emoji everywhere, from dashboard to notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }
    }
}
