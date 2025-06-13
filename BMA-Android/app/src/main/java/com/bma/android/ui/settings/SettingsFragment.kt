package com.bma.android.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.QRScannerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentSettingsBinding
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var qrScannerLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var createBackupFileLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var selectRestoreFileLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register ActivityResultLaunchers in onCreate to ensure they're available
        qrScannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // After returning from QR scanner, update the status
            updateConnectionStatus()
        }

        createBackupFileLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { 
                performBackup(it)
            }
        }

        selectRestoreFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { 
                performRestore(it)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        setupClickListeners()
        setupComingSoonFeatures()
    }

    override fun onResume() {
        super.onResume()
        // Update status every time the fragment is shown
        updateConnectionStatus()
    }

    private fun setupClickListeners() {
        // Connection Settings
        binding.disconnectButton.setOnClickListener {
            disconnectFromServer()
        }
        binding.reconnectButton.setOnClickListener {
            qrScannerLauncher.launch(Intent(requireContext(), QRScannerActivity::class.java))
        }
    }

    private fun setupComingSoonFeatures() {
        // Backup & Restore features
        binding.backupButton.setOnClickListener {
            createBackup()
        }
        binding.restoreButton.setOnClickListener {
            restoreBackup()
        }

        // Download Settings features - currently disabled
        binding.downloadQualityButton.setOnClickListener {
            Toast.makeText(requireContext(), "Download quality settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        binding.storageLocationButton.setOnClickListener {
            Toast.makeText(requireContext(), "Storage location settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        binding.clearCacheButton.setOnClickListener {
            Toast.makeText(requireContext(), "Clear cache feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateConnectionStatus() {
        // Check if binding is available before updating UI
        if (_binding == null) return
        
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

    private fun createBackup() {
        lifecycleScope.launch {
            val playlistManager = PlaylistManager.getInstance(requireContext())
            val playlists = playlistManager.loadPlaylists()
            
            if (playlists.isEmpty()) {
                Toast.makeText(requireContext(), "No playlists to backup", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val filename = playlistManager.generateBackupFilename()
            createBackupFileLauncher.launch(filename)
        }
    }

    private fun restoreBackup() {
        selectRestoreFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun performBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val success = playlistManager.exportBackup(uri)
                
                if (success) {
                    val playlists = playlistManager.loadPlaylists()
                    Toast.makeText(
                        requireContext(), 
                        "Successfully backed up ${playlists.size} playlists", 
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(), 
                        "Failed to create backup", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(), 
                    "Error creating backup: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performRestore(uri: android.net.Uri) {
        // Show dialog to ask if user wants to merge or replace
        AlertDialog.Builder(requireContext())
            .setTitle("Restore Playlists")
            .setMessage("How would you like to restore your playlists?")
            .setPositiveButton("Merge with existing") { _, _ ->
                executeRestore(uri, mergeWithExisting = true)
            }
            .setNegativeButton("Replace all") { _, _ ->
                showReplaceConfirmation(uri)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showReplaceConfirmation(uri: android.net.Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle("Replace All Playlists")
            .setMessage("This will delete all your current playlists and replace them with the backup. This action cannot be undone.\n\nAre you sure you want to continue?")
            .setPositiveButton("Yes, Replace All") { _, _ ->
                executeRestore(uri, mergeWithExisting = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeRestore(uri: android.net.Uri, mergeWithExisting: Boolean) {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val result = playlistManager.importBackup(uri, mergeWithExisting)
                
                when (result) {
                    is PlaylistManager.ImportResult.Success -> {
                        Toast.makeText(
                            requireContext(), 
                            result.message, 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is PlaylistManager.ImportResult.Error -> {
                        Toast.makeText(
                            requireContext(), 
                            "Restore failed: ${result.message}", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(), 
                    "Error restoring backup: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}