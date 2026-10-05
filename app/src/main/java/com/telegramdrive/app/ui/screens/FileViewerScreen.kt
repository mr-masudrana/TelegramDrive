package com.telegramdrive.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.telegramdrive.app.data.DriveFileEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    entity: DriveFileEntity,
    file: File,
    onClose: () -> Unit,
    onOpenExternally: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entity.fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenExternally) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val mime = entity.mimeType.orEmpty()
            val ext = entity.fileName.substringAfterLast('.', "").lowercase()
            when {
                mime.startsWith("image/") -> ImageViewer(file)
                mime.startsWith("video/") -> VideoViewer(file)
                mime == "application/pdf" || ext == "pdf" -> PdfViewer(file)
                mime.startsWith("text/") || ext in TEXT_EXTENSIONS -> TextViewer(file)
                else -> UnsupportedPreview(entity, onOpenExternally)
            }
        }
    }
}

private val TEXT_EXTENSIONS = setOf("txt", "md", "json", "xml", "csv", "log", "kt", "java", "py", "js")

@Composable
private fun ImageViewer(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset += pan
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
private fun VideoViewer(file: File) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            VideoView(context).apply {
                setVideoPath(file.absolutePath)
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { start() }
            }
        }
    )
}

@Composable
private fun PdfViewer(file: File) {
    var pageIndex by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(file, pageIndex) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        runCatching {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            pageCount = renderer!!.pageCount
            renderer!!.openPage(pageIndex.coerceIn(0, pageCount - 1)).use { page ->
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap = bmp
            }
        }.onFailure { error = it.message ?: "PDF খোলা যায়নি" }
        onDispose {
            renderer?.close()
            pfd?.close()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text(error!!)
                bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = null)
                else -> CircularProgressIndicator()
            }
        }
        if (pageCount > 1) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
                }
                Text("${pageIndex + 1} / $pageCount")
                IconButton(onClick = { if (pageIndex < pageCount - 1) pageIndex++ }, enabled = pageIndex < pageCount - 1) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
                }
            }
        }
    }
}

@Composable
private fun TextViewer(file: File) {
    var content by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        content = runCatching {
            file.inputStream().bufferedReader().use { it.readText(/* limit not enforced here */) }
                .take(200_000) // খুব বড় ফাইলে UI আটকে যাওয়া ঠেকাতে প্রথম ~200KB দেখানো হয়
        }.getOrElse { "ফাইল পড়া যায়নি: ${it.message}" }
    }
    SelectionContainer {
        Text(
            text = content ?: "লোড হচ্ছে…",
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun UnsupportedPreview(entity: DriveFileEntity, onOpenExternally: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(entity.fileName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("এই ফরম্যাটের জন্য অ্যাপের ভেতরে প্রিভিউ নেই", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenExternally) { Text("অন্য অ্যাপে ওপেন করুন") }
    }
}
