package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BarChart(
    data: Map<String, Int>,
    modifier: Modifier = Modifier,
    barColorStart: Color = MaterialTheme.colorScheme.primary,
    barColorEnd: Color = MaterialTheme.colorScheme.secondary
) {
    val classes = listOf("Nursury", "Prep", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5")
    val values = classes.map { data[it] ?: 0 }
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    // Animated progression scale
    val animationScale = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    val density = LocalDensity.current
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = with(density) { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    val countPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = with(density) { 11.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalBars = classes.size
            val paddingRatio = 0.4f // 40% spacing empty
            val barSpacing = width / totalBars
            val barWidth = barSpacing * (1f - paddingRatio)
            
            // Bottom baseline offset
            val labelAreaHeight = 45f
            val topBufferHeight = 45f
            val chartDisplayHeight = height - labelAreaHeight - topBufferHeight

            values.forEachIndexed { i, count ->
                val barProgressHeight = (count.toFloat() / maxValue) * chartDisplayHeight * animationScale.value
                val leftX = (i * barSpacing) + (barSpacing * paddingRatio / 2f)
                val topY = height - labelAreaHeight - barProgressHeight
                val rightX = leftX + barWidth
                val bottomY = height - labelAreaHeight

                // Draw solid background grid line (subtle)
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    start = Offset(leftX, height - labelAreaHeight),
                    end = Offset(leftX, topBufferHeight),
                    strokeWidth = 1f
                )

                // Draw bar using rounded rect with gradient
                if (count > 0) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(barColorStart, barColorEnd)
                        ),
                        topLeft = Offset(leftX, topY),
                        size = Size(barWidth, barProgressHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }

                // Draw counts value text dynamically above bars
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        count.toString(),
                        leftX + (barWidth / 2f),
                        (topY - 10f).coerceAtLeast(topBufferHeight - 10f),
                        countPaint
                    )

                    // Draw class labels below
                    val splitLabel = when (val label = classes[i]) {
                        "Nursury" -> "Nursery"
                        "Prep" -> "Prep"
                        else -> label
                    }
                    canvas.nativeCanvas.drawText(
                        splitLabel,
                        leftX + (barWidth / 2f),
                        height - 12f,
                        textPaint
                    )
                }
            }
        }
    }
}
