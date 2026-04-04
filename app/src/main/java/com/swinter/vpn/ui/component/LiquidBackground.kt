package com.swinter.vpn.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.swinter.vpn.data.VpnState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun LiquidBackground(
    state: VpnState,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (state) {
        VpnState.DISCONNECTED -> Color(0x26_4D66CC)       // blue, 15% alpha
        VpnState.CONNECTING, VpnState.DISCONNECTING -> Color(0x26_8040E6) // purple
        VpnState.CONNECTED -> Color(0x26_00C099)           // green
    }
    val secondaryColor = when (state) {
        VpnState.DISCONNECTED -> Color(0x1F_263380)
        VpnState.CONNECTING, VpnState.DISCONNECTING -> Color(0x1F_9933B3)
        VpnState.CONNECTED -> Color(0x1F_0080D9)
    }
    val deepBlue = Color(0x33_1A3366)

    val infiniteTransition = rememberInfiniteTransition(label = "liquid")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100_000, easing = LinearEasing),
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minDim = min(w, h)

        // Base background
        drawRect(Color(0xFF_080A14))

        // Blob definitions: cx%, cy%, radius%, speed, color
        data class Blob(val cx: Float, val cy: Float, val radius: Float, val speed: Float, val color: Color)

        val blobs = listOf(
            Blob(0.3f, 0.2f, 0.5f, 0.15f, accentColor),
            Blob(0.7f, 0.4f, 0.45f, 0.12f, secondaryColor),
            Blob(0.5f, 0.7f, 0.55f, 0.10f, deepBlue),
            Blob(0.2f, 0.6f, 0.35f, 0.18f, accentColor.copy(alpha = accentColor.alpha * 0.5f)),
            Blob(0.8f, 0.15f, 0.3f, 0.14f, secondaryColor.copy(alpha = secondaryColor.alpha * 0.7f)),
        )

        for (blob in blobs) {
            val t = phase * blob.speed
            val x = (blob.cx + sin(t * 1.3f) * 0.15f) * w
            val y = (blob.cy + cos(t * 0.9f) * 0.12f) * h
            val r = blob.radius * minDim

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob.color, Color.Transparent),
                    center = Offset(x, y),
                    radius = r,
                ),
                radius = r,
                center = Offset(x, y),
            )
        }
    }
}
