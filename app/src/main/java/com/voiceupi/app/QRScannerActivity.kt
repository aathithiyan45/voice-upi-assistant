package com.voiceupi.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class QRScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tts: TextToSpeech
    private var isScanned = false  // prevent multiple scans

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        setContentView(previewView)

        // 🔊 Initialize Text-to-Speech
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale("en", "IN")
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) startCamera()
            else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val scanner = BarcodeScanning.getClient()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                if (isScanned) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val mediaImage = imageProxy.image

                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val rawValue = barcode.rawValue

                                rawValue?.let {
                                    isScanned = true

                                    if (it.startsWith("upi://")) {
                                        val (name, amount) = parseUPI(it)
                                        val displayName = name ?: "merchant"

                                        if (amount != null) {
                                            val message = "Pay $amount rupees to $displayName"
                                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                            speak(message)
                                        } else {
                                            val message = "Enter amount to $displayName"
                                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                            speak(message)
                                        }

                                    } else {
                                        val message = "QR detected"
                                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                        speak(message)
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    // 🔥 UPI Parser
    private fun parseUPI(data: String): Pair<String?, String?> {
        val uri = Uri.parse(data)
        val name = uri.getQueryParameter("pn")
        val amount = uri.getQueryParameter("am")
        return Pair(name, amount)
    }

    // 🔊 Speak function
    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // 🧹 Release TTS
    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}