package tech.youssefachraf.image_to_pdf.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.PdfBadgeRed
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    var activeDot by remember { mutableIntStateOf(0) }

    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600),
        label = "splash_alpha"
    )

    LaunchedEffect(Unit) {
        appeared = true
        repeat(3) { i ->
            delay(400)
            activeDot = i
        }
        delay(300)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            DocumentIcon()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Image to PDF Converter",
                color = PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Simple PDF converter & editor",
                color = SecondaryText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0..2) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (i == activeDot) AccentBlue else SecondaryText.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun DocumentIcon() {
    Canvas(modifier = Modifier.size(100.dp)) {
        drawDocumentWithFoldedCorner(this)
    }
}

private fun drawDocumentWithFoldedCorner(drawScope: DrawScope) {
    with(drawScope) {
        val w = size.width
        val h = size.height
        val foldSize = w * 0.25f
        val cornerRadius = w * 0.06f

        val bodyPath = Path().apply {
            moveTo(cornerRadius, 0f)
            lineTo(w - foldSize, 0f)
            lineTo(w, foldSize)
            lineTo(w, h - cornerRadius)
            quadraticTo(w, h, w - cornerRadius, h)
            lineTo(cornerRadius, h)
            quadraticTo(0f, h, 0f, h - cornerRadius)
            lineTo(0f, cornerRadius)
            quadraticTo(0f, 0f, cornerRadius, 0f)
            close()
        }
        drawPath(bodyPath, PdfBadgeRed)

        val foldPath = Path().apply {
            moveTo(w - foldSize, 0f)
            lineTo(w - foldSize, foldSize)
            lineTo(w, foldSize)
            close()
        }
        drawPath(foldPath, Color(0xFFC0392B))

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = w * 0.28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            "PDF",
            w / 2f,
            h / 2f + textPaint.textSize / 3f,
            textPaint
        )
    }
}

