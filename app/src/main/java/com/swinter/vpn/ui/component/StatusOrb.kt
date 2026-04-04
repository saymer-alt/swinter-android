package com.swinter.vpn.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.ui.theme.*

@Composable
fun StatusOrb(
    state: VpnState,
    modifier: Modifier = Modifier
) {
    val primaryColor = when (state) {
        VpnState.DISCONNECTED -> Color(0xFF4D596D)
        VpnState.CONNECTING, VpnState.DISCONNECTING -> VpnPurple
        VpnState.CONNECTED -> VpnGreen
    }
    val secondaryColor = when (state) {
        VpnState.DISCONNECTED -> Color(0xFF334066)
        VpnState.CONNECTING, VpnState.DISCONNECTING -> Color(0xFF4D33B3)
        VpnState.CONNECTED -> Color(0xFF008DD9)
    }

    val isActive = state == VpnState.CONNECTING || state == VpnState.CONNECTED

    // Glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.3f else 0.15f,
        targetValue = if (isActive) 0.5f else 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Rotating gradient
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(180.dp)
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // Outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * glowScale
                ),
                radius = radius * glowScale,
                center = center
            )

            // Orb background
            drawCircle(
                color = DarkSurface,
                radius = radius * 0.7f,
                center = center
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.1f),
                radius = radius * 0.7f,
                center = center
            )

            // Rotating gradient ring
            rotate(rotation, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.6f),
                            secondaryColor.copy(alpha = 0.3f),
                            primaryColor.copy(alpha = 0.1f),
                            secondaryColor.copy(alpha = 0.5f),
                            primaryColor.copy(alpha = 0.6f)
                        ),
                        center = center
                    ),
                    radius = radius * 0.65f,
                    center = center
                )
            }

            // Inner dark circle (icon background)
            drawCircle(
                color = DarkSurface.copy(alpha = 0.85f),
                radius = radius * 0.45f,
                center = center
            )

            // Subtle border ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.2f),
                radius = radius * 0.7f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }

        // Center icon
        Icon(
            imageVector = if (state == VpnState.CONNECTED) Icons.Filled.Check else Icons.Filled.Shield,
            contentDescription = null,
            tint = TextPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(44.dp)
        )
    }
}
