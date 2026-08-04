package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.hisab.ui.navigation.Screen
import com.example.hisab.ui.theme.HisabTheme
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

private val DockShape = RoundedCornerShape(26.dp)
private const val DockSurfaceAlpha = 0.88f
private val DockBlurRadius = 16.dp
private const val UnselectedForegroundAlpha = 0.55f

@Composable
fun FloatingGlassmorphicBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val colors = HisabTheme.colors
    val colorScheme = MaterialTheme.colorScheme

    // Theme-aware frosted fill: high opacity obscures scroll content; blur handles the rest.
    val dockSurfaceColor = colorScheme.surfaceContainerHigh.copy(alpha = DockSurfaceAlpha)

    val glassBorderBrush = remember(colors.cardBorder) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.18f),
                colors.cardBorder.copy(alpha = 0.14f)
            )
        )
    }

    val unselectedColor = colorScheme.onSurface.copy(alpha = UnselectedForegroundAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = DockShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(DockShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = dockSurfaceColor,
                        blurRadius = DockBlurRadius
                    )
                )
                .background(dockSurfaceColor)
                .border(
                    width = 0.75.dp,
                    brush = glassBorderBrush,
                    shape = DockShape
                )
                .padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigate(screen) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                                tint = if (isSelected) {
                                    colorScheme.primary
                                } else {
                                    unselectedColor
                                },
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = screen.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    colorScheme.primary
                                } else {
                                    unselectedColor
                                }
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
