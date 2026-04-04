package com.swinter.vpn.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun QRScannerLauncher(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val contents = result.contents
        if (contents != null) {
            onResult(contents)
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Наведите камеру на QR-код сервера")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCameraId(0)
        }
        scanLauncher.launch(options)
    }
}
