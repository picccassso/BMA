package com.bma.android.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.QRScannerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from QR scanner, update the status
        updateConnectionStatus()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Update status every time the fragment is shown
        updateConnectionStatus()
    }

    private fun setupClickListeners() {
        binding.disconnectButton.setOnClickListener {
            disconnectFromServer()
        }
        binding.reconnectButton.setOnClickListener {
            qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
        }
    }

    private fun updateConnectionStatus() {
        val isAuthenticated = ApiClient.isAuthenticated()
        val serverUrl = ApiClient.getServerUrl()

        if (isAuthenticated && serverUrl.isNotEmpty()) {
            val protocol = when {
                serverUrl.contains(".ts.net") -> "Tailscale"
                serverUrl.startsWith("https://") -> "HTTPS"
                else -> "HTTP"
            }
            binding.connectionStatusText.text = "✅ Connected via $protocol to:\n$serverUrl"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            binding.disconnectButton.isVisible = true
            binding.reconnectButton.isVisible = false
        } else {
            binding.connectionStatusText.text = "❌ Not connected"
            binding.connectionStatusText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            binding.disconnectButton.isVisible = false
            binding.reconnectButton.isVisible = true
        }
    }

    private fun disconnectFromServer() {
        lifecycleScope.launch {
            var serverNotified = false
            if (ApiClient.isAuthenticated()) {
                try {
                    ApiClient.getAuthHeader()?.let {
                        ApiClient.api.disconnect(it)
                        serverNotified = true
                    }
                } catch (e: Exception) {
                    // Ignore, we are disconnecting anyway
                }
            }

            // Clear local credentials
            ApiClient.setAuthToken(null)
            requireActivity().getSharedPreferences("BMA", Context.MODE_PRIVATE).edit()
                .remove("auth_token")
                .apply()
            
            val message = if (serverNotified) "Disconnected from server" else "Disconnected locally"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            updateConnectionStatus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}