package com.bma.android.setup.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bma.android.R
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerFragment : BaseSetupFragment() {
    private lateinit var previewView: PreviewView
    private lateinit var loadingCard: MaterialCardView
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isProcessingQR = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showError("Camera permission is required to scan QR codes")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_qr_scanner, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        previewView = view.findViewById(R.id.preview_view)
        loadingCard = view.findViewById(R.id.loading_card)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        checkCameraPermission()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showError("Camera permission is required to scan QR codes")
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                showError("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessingQR) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { qrContent ->
                        if (!isProcessingQR) {
                            handleScannedQR(qrContent)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    private fun handleScannedQR(content: String) {
        if (isProcessingQR) return
        isProcessingQR = true
        
        // Stop scanning
        cameraProvider?.unbindAll()
        
        // Show loading state
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                loadingCard.visibility = View.VISIBLE
                loadingCard.startAnimation(AlphaAnimation(0f, 1f).apply {
                    duration = 300
                })
            }
            
            try {
                // Process QR code using ViewModel
                val success = viewModel.processQRCode(content)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        navigateToNext()
                    } else {
                        // Error already shown by ViewModel
                        loadingCard.visibility = View.GONE
                        isProcessingQR = false
                        startCamera()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingCard.visibility = View.GONE
                    showError("Failed to connect: ${e.message}")
                    isProcessingQR = false
                    startCamera()
                }
            }
        }
    }
} 