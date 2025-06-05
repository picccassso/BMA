package com.bma.android.setup.fragments

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.bma.android.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

class TailscaleCheckFragment : BaseSetupFragment() {
    private lateinit var statusIcon: ImageView
    private lateinit var checkProgress: ProgressBar
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var installButton: MaterialButton

    private val TAG = "TailscaleCheck"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tailscale_check, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        statusIcon = view.findViewById(R.id.status_icon)
        checkProgress = view.findViewById(R.id.check_progress)
        statusTitle = view.findViewById(R.id.status_title)
        statusDescription = view.findViewById(R.id.status_description)
        installButton = view.findViewById(R.id.install_button)
        
        // Set up install button click
        installButton.setOnClickListener {
            openPlayStore()
        }
        
        // Start checking for Tailscale
        checkTailscale()
    }
    
    private fun checkTailscale() {
        lifecycleScope.launch {
            // Show checking state
            showCheckingState()
            
            // Simulate a brief check delay for better UX
            delay(1500)
            
            if (!isTailscaleInstalled()) {
                Log.d(TAG, "Tailscale is not installed")
                showTailscaleNotInstalled()
                return@launch
            }
            
            Log.d(TAG, "Tailscale is installed, checking VPN status")
            
            // Check if Tailscale is running and connected
            when (checkTailscaleStatus()) {
                TailscaleStatus.RUNNING -> {
                    Log.d(TAG, "Tailscale is running and connected")
                    showTailscaleInstalled()
                }
                TailscaleStatus.NOT_CONNECTED -> {
                    Log.d(TAG, "Tailscale is not connected")
                    showTailscaleNotConnected()
                }
                TailscaleStatus.ERROR -> {
                    Log.d(TAG, "Failed to check Tailscale status")
                    showTailscaleError()
                }
            }
        }
    }
    
    private fun showCheckingState() {
        checkProgress.visibility = View.VISIBLE
        statusIcon.visibility = View.GONE
        installButton.visibility = View.GONE
        statusTitle.text = "Checking Tailscale"
        statusDescription.text = "Verifying Tailscale connection..."
    }
    
    private fun isTailscaleInstalled(): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo("com.tailscale.ipn", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private enum class TailscaleStatus {
        RUNNING,
        NOT_CONNECTED,
        ERROR
    }
    
    private fun checkTailscaleStatus(): TailscaleStatus {
        try {
            // Check if VPN is active
            val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            Log.d(TAG, "Active network: $network")
            Log.d(TAG, "Network capabilities: $capabilities")
            
            // Check if VPN is active and if we can resolve Tailscale's service
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                // Try to resolve Tailscale's service to confirm it's their VPN
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.tailscale.ipn",
                        "com.tailscale.ipn.IPNService"
                    )
                }
                
                val resolveInfo = requireContext().packageManager.resolveService(intent, 0)
                Log.d(TAG, "Resolved Tailscale service: $resolveInfo")
                
                return if (resolveInfo != null) {
                    TailscaleStatus.RUNNING
                } else {
                    TailscaleStatus.NOT_CONNECTED
                }
            } else {
                Log.d(TAG, "No VPN transport found")
                return TailscaleStatus.NOT_CONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Tailscale status", e)
            return TailscaleStatus.ERROR
        }
    }
    
    private fun showTailscaleInstalled() {
        checkProgress.visibility = View.GONE
        statusIcon.setImageResource(R.drawable.ic_check_circle)
        statusIcon.visibility = View.VISIBLE
        installButton.visibility = View.GONE
        
        statusTitle.text = "Tailscale is ready"
        statusDescription.text = "Great! Tailscale is connected"
        
        // Auto proceed after a brief delay
        lifecycleScope.launch {
            delay(1500)
            navigateToNext()
        }
    }
    
    private fun showTailscaleNotInstalled() {
        checkProgress.visibility = View.GONE
        statusIcon.setImageResource(R.drawable.ic_warning)
        statusIcon.visibility = View.VISIBLE
        
        statusTitle.text = "Tailscale required"
        statusDescription.text = "Please install Tailscale to continue with the setup"
        
        // Show install button with animation
        installButton.visibility = View.VISIBLE
        installButton.text = "INSTALL TAILSCALE"
        installButton.setOnClickListener {
            openPlayStore()
        }
        installButton.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
        })
    }
    
    private fun showTailscaleNotConnected() {
        checkProgress.visibility = View.GONE
        statusIcon.setImageResource(R.drawable.ic_warning)
        statusIcon.visibility = View.VISIBLE
        
        statusTitle.text = "Tailscale not connected"
        statusDescription.text = "Please connect to your Tailscale network"
        
        // Show open button with animation
        installButton.visibility = View.VISIBLE
        installButton.text = "OPEN TAILSCALE"
        installButton.setOnClickListener {
            openTailscale()
        }
        installButton.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
        })
    }
    
    private fun showTailscaleError() {
        checkProgress.visibility = View.GONE
        statusIcon.setImageResource(R.drawable.ic_warning)
        statusIcon.visibility = View.VISIBLE
        
        statusTitle.text = "Connection check failed"
        statusDescription.text = "Unable to verify Tailscale status. Please ensure it's connected."
        
        // Show retry button with animation
        installButton.visibility = View.VISIBLE
        installButton.text = "RETRY"
        installButton.setOnClickListener {
            checkTailscale()
        }
        installButton.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
        })
    }
    
    private fun openPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.tailscale.ipn")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Fallback to browser if Play Store app is not installed
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")
            })
        }
    }
    
    private fun openTailscale() {
        try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
            if (intent != null) {
                startActivity(intent)
            } else {
                showError("Could not open Tailscale")
            }
        } catch (e: Exception) {
            showError("Could not open Tailscale: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Recheck Tailscale when returning from Play Store or Tailscale app
        if (installButton.visibility == View.VISIBLE) {
            checkTailscale()
        }
    }
} 