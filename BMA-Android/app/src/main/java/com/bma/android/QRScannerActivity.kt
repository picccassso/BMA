package com.bma.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityQrScannerBinding
import com.bma.android.models.PairingData
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showError("Camera permission is required to scan QR codes")
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkCameraPermission()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    }
    
    private fun setupUI() {
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                    processQRCode(qrCode)
                })
            }
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            showError("Failed to start camera: ${exc.message}")
        }
    }
    
    private fun processQRCode(qrCodeData: String) {
        runOnUiThread {
            try {
                val gson = Gson()
                val pairingData = gson.fromJson(qrCodeData, PairingData::class.java)
                
                showStatus("QR Code detected! Connecting...")
                connectWithPairingData(pairingData)
                
            } catch (e: Exception) {
                showError("Invalid QR code format")
            }
        }
    }
    
    private fun connectWithPairingData(pairingData: PairingData) {
        // Stop camera scanning
        imageAnalyzer?.clearAnalyzer()
        
        // Update API client with new server URL and token
        ApiClient.setServerUrl(pairingData.serverUrl)
        ApiClient.setAuthToken(pairingData.token)
        
        // Save to preferences
        getSharedPreferences("BMA", MODE_PRIVATE).edit()
            .putString("server_url", pairingData.serverUrl)
            .putString("auth_token", pairingData.token)
            .putString("token_expires_at", pairingData.expiresAt)
            .apply()
        
        showStatus("Connected successfully!")
        
        Toast.makeText(this, "Connected to BMA server!", Toast.LENGTH_LONG).show()
        
        // Return to main activity
        setResult(RESULT_OK)
        finish()
    }
    
    private fun showStatus(message: String) {
        binding.statusText.text = message
        binding.statusText.visibility = android.view.View.VISIBLE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    private inner class QRCodeAnalyzer(
        private val onQRCodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()
        
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_TEXT -> {
                                    barcode.displayValue?.let { qrData ->
                                        onQRCodeDetected(qrData)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        // Handle failure silently for now
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
} 