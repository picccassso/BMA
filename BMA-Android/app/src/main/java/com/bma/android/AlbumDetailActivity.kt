package com.bma.android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemSongInAlbumBinding
import com.bma.android.databinding.ActivityAlbumDetailBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class AlbumDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumDetailBinding
    private lateinit var album: Album
    private lateinit var songAdapter: AlbumSongAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get album data from intent
        album = intent.getSerializableExtra("album") as? Album
            ?: run {
                finish()
                return
            }

        setupUI()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupUI() {
        // Set album information
        binding.albumTitle.text = album.name
        binding.artistName.text = album.artist ?: "Unknown Artist"
        binding.trackCount.text = "• ${album.trackCount} tracks"

        // Load album artwork
        loadAlbumArtwork()
    }

    private fun loadAlbumArtwork() {
        if (album.songs.isNotEmpty()) {
            val firstSong = album.songs.first()
            val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${firstSong.id}"
            
            val authHeader = ApiClient.getAuthHeader()
            
            if (authHeader != null) {
                val glideUrl = GlideUrl(
                    artworkUrl, 
                    LazyHeaders.Builder()
                        .addHeader("Authorization", authHeader)
                        .build()
                )
                
                Glide.with(this)
                    .load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_folder)
                    .error(R.drawable.ic_folder)
                    .into(binding.albumArtwork)
            } else {
                binding.albumArtwork.setImageResource(R.drawable.ic_folder)
            }
        } else {
            binding.albumArtwork.setImageResource(R.drawable.ic_folder)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = AlbumSongAdapter { song ->
            playSong(song)
        }
        
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AlbumDetailActivity)
            adapter = songAdapter
        }
        
        songAdapter.updateSongs(album.songs)
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.playButton.setOnClickListener {
            if (album.songs.isNotEmpty()) {
                playSong(album.songs.first(), startFromBeginning = true)
            }
        }

        binding.shuffleButton.setOnClickListener {
            if (album.songs.isNotEmpty()) {
                val shuffledSongs = album.songs.shuffled()
                playSong(shuffledSongs.first(), startFromBeginning = true, shuffled = true)
            }
        }
    }

        private fun playSong(song: Song, startFromBeginning: Boolean = false, shuffled: Boolean = false) {
        val songs = if (shuffled) album.songs.shuffled() else album.songs
        val currentPosition = songs.indexOf(song)
        
        // Start music service
        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("song_id", song.id)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("album_name", album.name)
            
            val songIds = songs.map { it.id }.toTypedArray()
            val songTitles = songs.map { it.title }.toTypedArray()
            val songArtists = songs.map { it.artist }.toTypedArray()
            
            putExtra("playlist_song_ids", songIds)
            putExtra("playlist_song_titles", songTitles)
            putExtra("playlist_song_artists", songArtists)
            putExtra("current_position", currentPosition)
            
            if (shuffled) {
                putExtra("shuffle_enabled", true)
            }
        }
        startActivity(intent)
    }

    // Simple adapter for songs in album detail
    private class AlbumSongAdapter(
        private val onSongClick: (Song) -> Unit
    ) : RecyclerView.Adapter<AlbumSongAdapter.SongViewHolder>() {

        private var songs = listOf<Song>()

        fun updateSongs(newSongs: List<Song>) {
            songs = newSongs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val binding = ItemSongInAlbumBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SongViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            holder.bind(songs[position])
        }

        override fun getItemCount() = songs.size

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
    }
} 