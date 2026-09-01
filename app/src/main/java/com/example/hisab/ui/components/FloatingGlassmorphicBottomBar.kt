package com.example.hisab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
private val PillWidth = 68.dp
private val PillHeight = 48.dp

@Composable
fun FloatingGlassmorphicBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    selectedRoute: String? = null
) {
    val colors = HisabTheme.colors
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

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

    val selectedIndex = remember(selectedRoute) {
        Screen.bottomNavItems.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    }

    // Track each item's position and size relative to the Row container
    var itemData by remember { mutableStateOf(Array(Screen.bottomNavItems.size) { IntSize.Zero to 0f }) }

    // Animated pill offset using Dp for smooth spring animation
    val pillOffsetXDp by animateDpAsState(
        targetValue = with(density) {
            val (size, posX) = itemData[selectedIndex]
            if (size.width > 0) {
                val centerX = posX + size.width / 2f
                val pillHalf = PillWidth.toPx() / 2f
                (centerX - pillHalf).toDp()
            } else 0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pillOffsetX"
    )

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
            // Sliding pill indicator — positioned relative to this Row's coordinate space
            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffsetXDp.roundToPx(), 0) }
                    .width(PillWidth)
                    .height(PillHeight)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        clip = false,
                        ambientColor = colorScheme.primary.copy(alpha = 0.30f),
                        spotColor = colorScheme.primary.copy(alpha = 0.40f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        colorScheme.primary.copy(alpha = 0.60f)
                    )

            )

            // Nav items row — positions are tracked relative to this Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { /* Row itself is the reference frame */ },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Screen.bottomNavItems.forEachIndexed { index, screen ->
                    val isSelected = when {
                        selectedRoute != null -> screen.route == selectedRoute
                        else -> currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    }

                    val animatedTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else unselectedColor,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "navTint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate(screen) }
                            )
                            .onGloballyPositioned { coords ->
                                // positionInParent gives us the x offset within the Row
                                val pos = coords.positionInParent()
                                val size = coords.size
                                itemData = itemData.copyOf().also {
                                    it[index] = IntSize(size.width, size.height) to pos.x
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
                            modifier = Modifier.padding(vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                                tint = animatedTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = screen.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = animatedTint
                            )
                        }
                    }
                }
            }
        }
    }
}
