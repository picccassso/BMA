package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.databinding.ItemPlaylistSelectableBinding
import com.bma.android.models.Playlist

/**
 * Adapter for playlist selection dialog
 */
class PlaylistSelectionAdapter(
    private val onPlaylistToggled: (Playlist, Boolean) -> Unit
) : RecyclerView.Adapter<PlaylistSelectionAdapter.PlaylistViewHolder>() {

    private var playlists = listOf<Playlist>()
    private val selectedPlaylistIds = mutableSetOf<String>()
    private var songId: String? = null

    fun updatePlaylists(playlists: List<Playlist>, songId: String) {
        this.playlists = playlists
        this.songId = songId
        
        // Pre-select playlists that already contain this song
        selectedPlaylistIds.clear()
        selectedPlaylistIds.addAll(
            playlists.filter { it.containsSong(songId) }.map { it.id }
        )
        
        notifyDataSetChanged()
    }

    fun getSelectedPlaylistIds(): Set<String> = selectedPlaylistIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistSelectableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    override fun getItemCount() = playlists.size

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistSelectableBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.playlistName.text = playlist.name
            binding.songCount.text = "${playlist.songCount} songs"
            
            // Show checkmark if playlist is selected
            val isSelected = selectedPlaylistIds.contains(playlist.id)
            binding.checkmark.isVisible = isSelected
            
            // Handle clicks
            binding.root.setOnClickListener {
                val wasSelected = selectedPlaylistIds.contains(playlist.id)
                
                if (wasSelected) {
                    selectedPlaylistIds.remove(playlist.id)
                    binding.checkmark.isVisible = false
                    onPlaylistToggled(playlist, false)
                } else {
                    selectedPlaylistIds.add(playlist.id)
                    binding.checkmark.isVisible = true
                    onPlaylistToggled(playlist, true)
                }
            }
        }
    }
}