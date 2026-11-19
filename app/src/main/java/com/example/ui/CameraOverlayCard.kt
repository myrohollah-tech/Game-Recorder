package com.example.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Displays the simultaneous front-camera overlay at exactly 300x250 px
 * ONLY when Camera is ON and the user is active in stream/capture.
 */
@Composable
fun FrontCameraOverlayBox(
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    // Exact 300x250 px converted to Dp with sensible minimum/maximum bounds across tablet densities
    val widthDp = with(density) { 300.toDp().coerceIn(140.dp, 300.dp) }
    val heightDp = with(density) { 250.toDp().coerceIn(116.dp, 250.dp) }

    var cameraBound by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            val listener = Runnable {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    cameraProvider.unbindAll()
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                    cameraBound = true
                } catch (e: Exception) {
                    Log.w("FrontCameraOverlay", "CameraX bind fallback: ${e.message}")
                    cameraBound = false
                }
            }
            cameraProviderFuture.addListener(listener, executor)

            onDispose {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .size(width = widthDp, height = heightDp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
            .testTag("camera_overlay_300x250")
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!hasCameraPermission || !cameraBound) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0F172A))
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = "Front Camera Active",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Front Camera • 300×250 px",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color(0xCC000000),
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CAM 300×250 px",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}
