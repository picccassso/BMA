package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemAlbumHeaderBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class AlbumAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {



    private var albums = listOf<Album>()

    /**
     * Update the albums list and refresh the display
     */
    fun updateAlbums(newAlbums: List<Album>) {
        println("🔍 [AlbumAdapter] updateAlbums called with ${newAlbums.size} albums")
        albums = newAlbums
        println("🔍 [AlbumAdapter] Albums updated: ${albums.size} items")
        notifyDataSetChanged()
        println("🔍 [AlbumAdapter] notifyDataSetChanged() called")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemAlbumHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlbumHeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        println("🔍 [AlbumAdapter] onBindViewHolder called for position $position")
        val album = albums[position]
        println("🔍 [AlbumAdapter] Binding album: ${album.name}")
        (holder as AlbumHeaderViewHolder).bind(album)
    }

    override fun getItemCount() = albums.size.also { 
        println("🔍 [AlbumAdapter] getItemCount() returning $it")
    }

    inner class AlbumHeaderViewHolder(
        private val binding: ItemAlbumHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.albumNameText.text = album.name
            binding.artistText.text = album.artist ?: "Unknown Artist"
            binding.trackCountText.text = "• ${album.trackCount} tracks"
            
            // Show right chevron to indicate navigation
            binding.expandIcon.setImageResource(R.drawable.ic_chevron_right)
            
            // Load album artwork from the first song in the album
            loadAlbumArtwork(album)
            
            binding.root.setOnClickListener {
                onAlbumClick(album)
            }
        }
        
        private fun loadAlbumArtwork(album: Album) {
            if (album.songs.isNotEmpty()) {
                val firstSong = album.songs.first()
                val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${firstSong.id}"
                
                println("🎨 [AlbumAdapter] Loading artwork for album '${album.name}' from: $artworkUrl")
                
                // Get the auth header for the request
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
                        .placeholder(R.drawable.ic_folder)
                        .error(R.drawable.ic_folder)
                        .into(binding.albumArtwork)
                } else {
                    println("❌ [AlbumAdapter] No auth header available for artwork request")
                    // No auth, show default folder icon
                    binding.albumArtwork.setImageResource(R.drawable.ic_folder)
                }
            } else {
                // No songs in album, show default folder icon
                binding.albumArtwork.setImageResource(R.drawable.ic_folder)
            }
        }
    }
} 