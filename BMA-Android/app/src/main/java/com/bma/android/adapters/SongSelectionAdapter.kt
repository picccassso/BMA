package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemSongSelectableBinding
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

/**
 * Adapter for song selection in playlist creation dialog
 */
class SongSelectionAdapter(
    private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<SongSelectionAdapter.SongViewHolder>() {

    private var allSongs = listOf<Song>()
    private var filteredSongs = listOf<Song>()
    private val selectedSongIds = mutableSetOf<String>()

    fun updateSongs(songs: List<Song>) {
        allSongs = songs
        filteredSongs = songs
        notifyDataSetChanged()
    }

    fun filterSongs(query: String) {
        filteredSongs = if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun getSelectedSongIds(): Set<String> = selectedSongIds.toSet()

    fun setInitialSelection(songIds: Set<String>) {
        selectedSongIds.clear()
        selectedSongIds.addAll(songIds)
        notifyDataSetChanged()
        onSelectionChanged(selectedSongIds)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongSelectableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(filteredSongs[position])
    }

    override fun getItemCount() = filteredSongs.size

    inner class SongViewHolder(
        private val binding: ItemSongSelectableBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist.ifEmpty { "Unknown Artist" }
            
            // Set checkbox state
            binding.songCheckbox.isChecked = selectedSongIds.contains(song.id)
            
            // Load song artwork
            loadSongArtwork(song)
            
            // Handle checkbox clicks
            binding.songCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSongIds.add(song.id)
                } else {
                    selectedSongIds.remove(song.id)
                }
                onSelectionChanged(selectedSongIds)
            }
            
            // Handle row clicks (toggle checkbox)
            binding.root.setOnClickListener {
                binding.songCheckbox.isChecked = !binding.songCheckbox.isChecked
            }
        }
        
        private fun loadSongArtwork(song: Song) {
            val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
            val authHeader = ApiClient.getAuthHeader()
            
            if (authHeader != null) {
                val glideUrl = GlideUrl(
                    artworkUrl, 
                    LazyHeaders.Builder()
                        .addHeader("Authorization", authHeader)
                        .build()
                )
                
                Glide.with(binding.root.context)
                    .load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(binding.songArtwork)
            } else {
                binding.songArtwork.setImageResource(R.drawable.ic_music_note)
            }
        }
    }
}