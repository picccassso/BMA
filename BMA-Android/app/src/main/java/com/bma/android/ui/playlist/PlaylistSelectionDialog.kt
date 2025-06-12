package com.bma.android.ui.playlist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.adapters.PlaylistSelectionAdapter
import com.bma.android.databinding.DialogPlaylistSelectionBinding
import com.bma.android.models.Playlist
import com.bma.android.models.Song
import com.bma.android.storage.PlaylistManager
import kotlinx.coroutines.launch

/**
 * Dialog for selecting playlists to add a song to
 */
class PlaylistSelectionDialog : DialogFragment() {

    private var _binding: DialogPlaylistSelectionBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var playlistSelectionAdapter: PlaylistSelectionAdapter
    private var song: Song? = null
    private var allSongs: List<Song> = emptyList()
    private var onPlaylistsUpdated: (() -> Unit)? = null
    
    companion object {
        private const val ARG_SONG = "song"
        private const val ARG_ALL_SONGS = "all_songs"
        
        fun newInstance(song: Song, allSongs: List<Song>): PlaylistSelectionDialog {
            val dialog = PlaylistSelectionDialog()
            val args = Bundle()
            args.putSerializable(ARG_SONG, song)
            args.putSerializable(ARG_ALL_SONGS, ArrayList(allSongs))
            dialog.arguments = args
            return dialog
        }
    }
    
    fun setOnPlaylistsUpdatedListener(listener: () -> Unit) {
        onPlaylistsUpdated = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPlaylistSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get song and all songs from arguments
        song = arguments?.getSerializable(ARG_SONG) as? Song
        @Suppress("UNCHECKED_CAST")
        allSongs = arguments?.getSerializable(ARG_ALL_SONGS) as? List<Song> ?: emptyList()
        
        setupViews()
        setupRecyclerView()
        loadPlaylists()
    }

    private fun setupViews() {
        // Update title to include song name
        binding.dialogTitle.text = "Add \"${song?.title}\" to playlist"
        
        // Close button
        binding.closeButton.setOnClickListener {
            dismiss()
        }
        
        // Create new playlist option
        binding.createNewPlaylistOption.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun setupRecyclerView() {
        playlistSelectionAdapter = PlaylistSelectionAdapter { playlist, isSelected ->
            handlePlaylistToggled(playlist, isSelected)
        }
        
        binding.playlistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playlistSelectionAdapter
        }
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                val playlists = playlistManager.loadPlaylists()
                
                if (playlists.isEmpty()) {
                    // Show empty state
                    binding.playlistsRecyclerView.isVisible = false
                    // Could add an empty state view here
                } else {
                    binding.playlistsRecyclerView.isVisible = true
                    song?.let { songToAdd ->
                        playlistSelectionAdapter.updatePlaylists(playlists, songToAdd.id)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PlaylistSelectionDialog", "Error loading playlists", e)
                Toast.makeText(requireContext(), "Failed to load playlists", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePlaylistToggled(playlist: Playlist, isSelected: Boolean) {
        val currentSong = song ?: return
        
        lifecycleScope.launch {
            try {
                val playlistManager = PlaylistManager.getInstance(requireContext())
                
                if (isSelected) {
                    // Add song to playlist
                    playlistManager.addSongToPlaylist(playlist.id, currentSong.id)
                    Toast.makeText(
                        requireContext(),
                        "Added to '${playlist.name}'",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Remove song from playlist
                    playlistManager.removeSongFromPlaylist(playlist.id, currentSong.id)
                    Toast.makeText(
                        requireContext(),
                        "Removed from '${playlist.name}'",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Notify parent that playlists were updated
                onPlaylistsUpdated?.invoke()
                
            } catch (e: Exception) {
                android.util.Log.e("PlaylistSelectionDialog", "Error updating playlist", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to update playlist: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        if (allSongs.isEmpty()) {
            Toast.makeText(requireContext(), "No songs available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentSong = song ?: return
        
        val createDialog = CreatePlaylistDialog.newInstance(allSongs)
        createDialog.setOnPlaylistCreatedListener { playlistId ->
            // The CreatePlaylistDialog will create the playlist with selected songs
            // We need to add our current song to it if it wasn't already selected
            lifecycleScope.launch {
                try {
                    val playlistManager = PlaylistManager.getInstance(requireContext())
                    val createdPlaylist = playlistManager.getPlaylist(playlistId)
                    
                    if (createdPlaylist != null && !createdPlaylist.containsSong(currentSong.id)) {
                        playlistManager.addSongToPlaylist(playlistId, currentSong.id)
                    }
                    
                    Toast.makeText(
                        requireContext(),
                        "Added to new playlist '${createdPlaylist?.name}'",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Reload playlists to show the new one
                    loadPlaylists()
                    onPlaylistsUpdated?.invoke()
                    
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistSelectionDialog", "Error adding to new playlist", e)
                }
            }
        }
        
        createDialog.show(parentFragmentManager, "CreatePlaylistFromSelection")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}