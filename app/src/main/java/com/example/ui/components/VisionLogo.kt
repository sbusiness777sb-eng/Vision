package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VisionColors

/**
 * Preserves the original VISION brand mark:
 * - Teal V-shaped symbol
 * - Orange upward arrow rising cleanly from the center of the V
 */
@Composable
fun VisionLogo(
    modifier: Modifier = Modifier.size(34.dp)
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "VISION Logo"
        }
    ) {
        val w = size.width
        val h = size.height

        // Teal V-shaped chevron symbol
        val vPath = Path().apply {
            moveTo(w * 0.12f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.86f)
            lineTo(w * 0.88f, h * 0.30f)
        }
        drawPath(
            path = vPath,
            color = VisionColors.LogoTeal,
            style = Stroke(
                width = w * 0.145f,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Miter
            )
        )

        // Orange upward arrow centered inside the V
        val shaftPath = Path().apply {
            moveTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.50f, h * 0.20f)
        }
        drawPath(
            path = shaftPath,
            color = VisionColors.LogoOrange,
            style = Stroke(
                width = w * 0.135f,
                cap = StrokeCap.Butt
            )
        )

        val arrowHead = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.70f, h * 0.34f)
            lineTo(w * 0.30f, h * 0.34f)
            close()
        }
        drawPath(
            path = arrowHead,
            color = VisionColors.LogoOrange
        )
    }
}

@Composable
fun VisionBrandHeader(
    titleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VisionLogo(modifier = Modifier.size(34.dp))
        androidx.compose.foundation.layout.Column {
            Text(
                text = "VISION",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = 1.6.sp,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = subtitleColor
            )
        }
    }
}
