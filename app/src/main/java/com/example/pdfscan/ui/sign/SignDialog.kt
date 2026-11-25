package com.example.pdfscan.ui.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.pdfscan.data.SavedSignature
import com.example.pdfscan.domain.SignaturePlacement
import com.example.pdfscan.ui.theme.ReviewCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Ink = Color(0xFF111B4A)

/**
 * Full-screen flow: draw a signature (first time or "Redraw") -> drag/resize it on the page -> Apply.
 * The signature is stored on the device only and burned into the page image when applied.
 */
@Composable
fun SignDialog(
    pageUri: Uri,
    signature: SavedSignature?,
    onSaveSignature: (Bitmap) -> Unit,
    onApply: (SignaturePlacement) -> Unit,
    onDismiss: () -> Unit,
) {
    var drawing by remember { mutableStateOf(signature == null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                drawing -> SignaturePad(
                    onCancel = { if (signature != null) drawing = false else onDismiss() },
                    onDone = { bmp -> onSaveSignature(bmp); drawing = false },
                )
                signature == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> PlaceSignature(
                    pageUri = pageUri,
                    signature = signature,
                    onRedraw = { drawing = true },
                    onCancel = onDismiss,
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun SignaturePad(onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Draw your signature", style = MaterialTheme.typography.titleLarge)
        Text(
            "Use your finger or a stylus. It is stored on this device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        ) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ -> change.consume(); current = current + change.position },
                        onDragEnd = { if (current.isNotEmpty()) strokes.add(current); current = emptyList() },
                        onDragCancel = { if (current.isNotEmpty()) strokes.add(current); current = emptyList() },
                    )
                }
            ) {
                (strokes + listOf(current)).forEach { pts -> if (pts.isNotEmpty()) drawPoints(pts, strokePx) }
            }
            if (strokes.isEmpty() && current.isEmpty()) {
                Text("Sign here", Modifier.align(Alignment.Center), color = Color(0xFF94A3B8))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Clear") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = { renderSignature(strokes.toList(), strokePx)?.let(onDone) },
            ) { Text("Save") }
        }
    }
}

private fun DrawScope.drawPoints(pts: List<Offset>, width: Float) {
    if (pts.size == 1) {
        drawCircle(Ink, width / 2f, pts[0])
        return
    }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    drawPath(path, Ink, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Renders strokes to a tightly-cropped transparent bitmap (2x for crisp output). */
private fun renderSignature(strokes: List<List<Offset>>, strokePx: Float): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val scale = 2f
    val margin = strokePx * 2f
    val minX = all.minOf { it.x }
    val minY = all.minOf { it.y }
    val maxX = all.maxOf { it.x }
    val maxY = all.maxOf { it.y }
    val w = ((maxX - minX + margin * 2f) * scale).toInt().coerceAtLeast(2)
    val h = ((maxY - minY + margin * 2f) * scale).toInt().coerceAtLeast(2)

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111B4A.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokePx * scale
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val dot = android.graphics.Paint(stroke).apply { style = android.graphics.Paint.Style.FILL }
    fun tx(x: Float) = (x - minX + margin) * scale
    fun ty(y: Float) = (y - minY + margin) * scale

    for (pts in strokes) {
        if (pts.size == 1) {
            canvas.drawCircle(tx(pts[0].x), ty(pts[0].y), strokePx * scale / 2f, dot)
        } else {
            val path = android.graphics.Path()
            path.moveTo(tx(pts[0].x), ty(pts[0].y))
            for (i in 1 until pts.size) path.lineTo(tx(pts[i].x), ty(pts[i].y))
            canvas.drawPath(path, stroke)
        }
    }
    return bmp
}

@Composable
private fun PlaceSignature(
    pageUri: Uri,
    signature: SavedSignature,
    onRedraw: () -> Unit,
    onCancel: () -> Unit,
    onApply: (SignaturePlacement) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val sigBitmap by produceState<ImageBitmap?>(null, signature.version) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(signature.file.path)?.asImageBitmap() }
    }
    val pageAspect by produceState(0.707f, pageUri) {
        value = withContext(Dispatchers.IO) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(pageUri)?.use { BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 0.707f
        }
    }

    var x by rememberSaveable { mutableFloatStateOf(0.4f) }
    var y by rememberSaveable { mutableFloatStateOf(0.78f) }
    var width by rememberSaveable { mutableFloatStateOf(0.35f) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Place signature", style = MaterialTheme.typography.titleLarge)
            Text(
                "Drag the signature to move it. Use the slider to resize.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().background(ReviewCanvas).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val pageW = minOf(maxWidth, maxHeight * pageAspect)
            val pageH = pageW / pageAspect
            val pageWPx = with(density) { pageW.toPx() }
            val pageHPx = with(density) { pageH.toPx() }

            Box(Modifier.size(pageW, pageH)) {
                AsyncImage(
                    model = pageUri,
                    contentDescription = "Page",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                sigBitmap?.let { bmp ->
                    val sigW = pageW * width
                    val sigH = sigW * bmp.height / bmp.width
                    // signature height as a fraction of page height
                    val sigHFrac = width * bmp.height / bmp.width * pageAspect
                    Image(
                        bitmap = bmp,
                        contentDescription = "Signature",
                        modifier = Modifier
                            .offset { IntOffset((x * pageWPx).roundToInt(), (y * pageHPx).roundToInt()) }
                            .size(sigW, sigH)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                            .pointerInput(pageWPx, pageHPx, width, sigHFrac) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    x = (x + drag.x / pageWPx).coerceIn(0f, (1f - width).coerceAtLeast(0f))
                                    y = (y + drag.y / pageHPx).coerceIn(0f, (1f - sigHFrac).coerceAtLeast(0f))
                                }
                            },
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Size", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = width,
                onValueChange = { w ->
                    width = w
                    val bmp = sigBitmap
                    val hFrac = if (bmp != null) w * bmp.height / bmp.width * pageAspect else 0f
                    x = x.coerceAtMost((1f - w).coerceAtLeast(0f))
                    y = y.coerceAtMost((1f - hFrac).coerceAtLeast(0f))
                },
                valueRange = 0.1f..0.7f,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRedraw) { Text("Redraw") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onApply(SignaturePlacement(x, y, width)) }) { Text("Apply") }
            }
        }
    }
}
