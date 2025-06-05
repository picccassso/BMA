package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemAlbumHeaderBinding
import com.bma.android.databinding.ItemSongInAlbumBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class AlbumAdapter(
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ALBUM_HEADER = 0
        private const val VIEW_TYPE_SONG = 1
    }

    private var albums = listOf<Album>()
    private val expandedAlbums = mutableSetOf<String>()
    private val displayItems = mutableListOf<DisplayItem>()

    init {
        updateDisplayItems()
    }

    sealed class DisplayItem {
        data class AlbumHeader(val album: Album, val isExpanded: Boolean) : DisplayItem()
        data class SongItem(val song: Song, val album: Album) : DisplayItem()
    }

    /**
     * Update the albums list and refresh the display
     */
    fun updateAlbums(newAlbums: List<Album>) {
        println("🔍 [AlbumAdapter] updateAlbums called with ${newAlbums.size} albums")
        albums = newAlbums
        updateDisplayItems()
        println("🔍 [AlbumAdapter] Display items updated: ${displayItems.size} items")
        notifyDataSetChanged()
        println("🔍 [AlbumAdapter] notifyDataSetChanged() called")
    }

    private fun updateDisplayItems() {
        displayItems.clear()
        
        albums.forEach { album ->
            val isExpanded = expandedAlbums.contains(album.id)
            displayItems.add(DisplayItem.AlbumHeader(album, isExpanded))
            
            if (isExpanded) {
                album.songs.forEach { song ->
                    displayItems.add(DisplayItem.SongItem(song, album))
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is DisplayItem.AlbumHeader -> VIEW_TYPE_ALBUM_HEADER
            is DisplayItem.SongItem -> VIEW_TYPE_SONG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ALBUM_HEADER -> {
                val binding = ItemAlbumHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AlbumHeaderViewHolder(binding)
            }
            VIEW_TYPE_SONG -> {
                val binding = ItemSongInAlbumBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SongViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        println("🔍 [AlbumAdapter] onBindViewHolder called for position $position")
        when (val item = displayItems[position]) {
            is DisplayItem.AlbumHeader -> {
                println("🔍 [AlbumAdapter] Binding album header: ${item.album.name}")
                (holder as AlbumHeaderViewHolder).bind(item.album, item.isExpanded)
            }
            is DisplayItem.SongItem -> {
                println("🔍 [AlbumAdapter] Binding song: ${item.song.title}")
                (holder as SongViewHolder).bind(item.song)
            }
        }
    }

    override fun getItemCount() = displayItems.size.also { 
        println("🔍 [AlbumAdapter] getItemCount() returning $it")
    }

    inner class AlbumHeaderViewHolder(
        private val binding: ItemAlbumHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album, isExpanded: Boolean) {
            binding.albumNameText.text = album.name
            binding.artistText.text = album.artist ?: "Unknown Artist"
            binding.trackCountText.text = "• ${album.trackCount} tracks"
            
            // Update chevron icon based on expanded state
            val chevronIcon = if (isExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
            binding.expandIcon.setImageResource(chevronIcon)
            
            // Load album artwork from the first song in the album
            loadAlbumArtwork(album)
            
            binding.root.setOnClickListener {
                toggleAlbum(album.id)
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

    inner class SongViewHolder(
        private val binding: ItemSongInAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            // Remove track numbers and clean up title for display
            val cleanTitle = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
            binding.titleText.text = cleanTitle.ifEmpty { song.title }
            
            binding.root.setOnClickListener {
                onSongClick(song)
            }
        }
    }

    private fun toggleAlbum(albumId: String) {
        if (expandedAlbums.contains(albumId)) {
            expandedAlbums.remove(albumId)
        } else {
            expandedAlbums.add(albumId)
        }
        updateDisplayItems()
        notifyDataSetChanged()
    }

    fun expandAll() {
        expandedAlbums.addAll(albums.map { it.id })
        updateDisplayItems()
        notifyDataSetChanged()
    }

    fun collapseAll() {
        expandedAlbums.clear()
        updateDisplayItems()
        notifyDataSetChanged()
    }
} 