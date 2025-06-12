package com.bma.android.ui.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.view.inputmethod.InputMethodManager
import android.os.IBinder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.MainActivity
import com.bma.android.MusicService
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentSearchBinding
import com.bma.android.databinding.ItemSongBinding
import com.bma.android.databinding.ItemAlbumHeaderBinding
import com.bma.android.databinding.ItemRecentlyPlayedSongBinding
import com.bma.android.models.Album
import com.bma.android.models.SearchPlayHistory
import com.bma.android.models.Song
import com.bma.android.storage.SearchPlayHistoryManager
import com.bma.android.ui.playlist.PlaylistSelectionDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.launch

// Sealed class for search result types
sealed class SearchResult {
    data class SongResult(val song: Song, val album: Album) : SearchResult()
    data class AlbumResult(val album: Album) : SearchResult()
}

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private lateinit var recentlyPlayedAdapter: RecentlyPlayedAdapter
    private var allAlbums = listOf<Album>()
    private var allSongs = listOf<Song>()
    
    // Search play history manager
    private lateinit var searchPlayHistoryManager: SearchPlayHistoryManager
    
    // Music service connection
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Pending playback request for when service connects
    private var pendingPlayback: PlaybackRequest? = null
    
    private data class PlaybackRequest(
        val song: Song,
        val album: Album
    )
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            
            // Handle pending playback request
            pendingPlayback?.let { request ->
                // Add to search play history for pending requests too
                searchPlayHistoryManager.addToHistory(request.song, request.album.name)
                refreshRecentlyPlayed()
                
                // For individual song search results, create a single-song playlist
                musicService!!.loadAndPlay(request.song, listOf(request.song), 0)
                pendingPlayback = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)
        
        // Initialize search play history manager
        searchPlayHistoryManager = SearchPlayHistoryManager.getInstance(requireContext())

        setupRecyclerViews()
        setupSearchView()
        loadAllMusicForSearching()
        loadRecentlyPlayed()
        bindMusicService()
    }
    
    override fun onPause() {
        super.onPause()
        // Hide keyboard and clear search when navigating away from search
        hideKeyboard()
        clearSearch()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        _binding = null
    }
    
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchView.windowToken, 0)
    }
    
    private fun clearSearch() {
        binding.searchView.setQuery("", false)
        showRecentlyPlayed()
    }
    
    private fun bindMusicService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupRecyclerViews() {
        // For search results with mixed content (songs and albums)
        searchResultsAdapter = SearchResultsAdapter(
            onSongClick = { song, album ->
                // Add to search play history
                searchPlayHistoryManager.addToHistory(song, album.name)
                refreshRecentlyPlayed()
                
                // Start music service
                val serviceIntent = Intent(requireContext(), MusicService::class.java)
                requireContext().startService(serviceIntent)
                
                // If service is bound, directly start playback without opening PlayerActivity
                if (serviceBound && musicService != null) {
                    // For individual song search results, create a single-song playlist
                    musicService!!.loadAndPlay(song, listOf(song), 0)
                } else {
                    // Fallback: bind service and play when connected
                    bindMusicService()
                    // Store playback request for when service connects
                    pendingPlayback = PlaybackRequest(song, album)
                }
            },
            onAlbumClick = { album ->
                // Use new animation system instead of AlbumDetailActivity
                (requireActivity() as? MainActivity)?.showAlbumDetail(album)
            }
        )
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultsAdapter
        }

        // For recently played songs from search
        recentlyPlayedAdapter = RecentlyPlayedAdapter(
            onItemClick = { historyItem ->
                // Find the song and play it
                val song = allSongs.find { it.id == historyItem.songId }
                val album = allAlbums.find { it.name == historyItem.albumName }
                
                if (song != null && album != null) {
                    // Don't add to history when playing from recently played list
                    // Start music service
                    val serviceIntent = Intent(requireContext(), MusicService::class.java)
                    requireContext().startService(serviceIntent)
                    
                    // Play the song
                    if (serviceBound && musicService != null) {
                        musicService!!.loadAndPlay(song, listOf(song), 0)
                    } else {
                        bindMusicService()
                        pendingPlayback = PlaybackRequest(song, album)
                    }
                }
            },
            onItemLongClick = { historyItem ->
                showRecentlyPlayedOptions(historyItem)
            },
            onRemoveClick = { historyItem ->
                removeFromSearchHistory(historyItem)
            }
        )
        binding.recentlyPlayedRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentlyPlayedAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    val trimmedQuery = query.trim()
                    if (trimmedQuery.isNotEmpty()) {
                        performSearch(trimmedQuery)
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    showRecentlyPlayed()
                } else {
                    val trimmedText = newText.trim()
                    if (trimmedText.isNotEmpty()) {
                        performSearch(trimmedText)
                    } else {
                        showRecentlyPlayed()
                    }
                }
                return true
            }
        })
    }
    
    private fun showRecentlyPlayed() {
        binding.searchResultsRecyclerView.isVisible = false
        binding.recentlyPlayedRecyclerView.isVisible = true
        binding.recentlyPlayedTitle.isVisible = true
    }

    private fun showSearchResults() {
        binding.searchResultsRecyclerView.isVisible = true
        binding.recentlyPlayedRecyclerView.isVisible = false
        binding.recentlyPlayedTitle.isVisible = false
    }

    private fun loadAllMusicForSearching() {
        lifecycleScope.launch {
            try {
                val authHeader = ApiClient.getAuthHeader()
                if (authHeader == null || ApiClient.isTokenExpired(requireContext())) {
                    return@launch
                }
                val songList = ApiClient.api.getSongs(authHeader)
                allSongs = songList
                allAlbums = organizeSongsIntoAlbums(songList)
            } catch (e: Exception) {
                // Errors will be handled on the Library/Settings screen, silently fail here
            }
        }
    }

    private fun performSearch(query: String) {
        showSearchResults()
        val lowerCaseQuery = query.lowercase()
        val songResults = mutableListOf<SearchResult.SongResult>()
        val albumResults = mutableListOf<SearchResult.AlbumResult>()

        // Find individual songs that match the query (with fuzzy matching)
        allSongs.forEach { song ->
            val songScore = calculateMatchScore(lowerCaseQuery, song.title.lowercase()) +
                           calculateMatchScore(lowerCaseQuery, song.artist.lowercase())
            
            if (songScore > 0) {
                val album = allAlbums.find { it.songs.contains(song) }
                if (album != null) {
                    songResults.add(SearchResult.SongResult(song, album))
                }
            }
        }

        // Find albums that match the query by name (with fuzzy matching)
        allAlbums.forEach { album ->
            val albumScore = calculateMatchScore(lowerCaseQuery, album.name.lowercase()) +
                            (album.artist?.let { calculateMatchScore(lowerCaseQuery, it.lowercase()) } ?: 0)
            
            if (albumScore > 0) {
                albumResults.add(SearchResult.AlbumResult(album))
            }
        }

        // Combine results with songs first, then albums
        val searchResults = mutableListOf<SearchResult>()
        searchResults.addAll(songResults)
        searchResults.addAll(albumResults)

        searchResultsAdapter.updateResults(searchResults)
    }
    
    private fun calculateMatchScore(query: String, target: String): Int {
        // Exact substring match gets highest score
        if (target.contains(query)) {
            return if (target == query) 100 else 80
        }
        
        // Smart fuzzy matching for typos - only if query is 4+ characters
        if (query.length >= 4) {
            // Check if most characters match, allowing for 1-2 typos
            val maxTypos = when {
                query.length <= 5 -> 1  // Allow 1 typo for short words
                else -> 2              // Allow 2 typos for longer words
            }
            
            // Try different combinations with allowed typos
            for (typos in 1..maxTypos) {
                if (hasPartialMatch(query, target, typos)) {
                    return 50 - (typos * 10) // Lower score for more typos
                }
            }
        }
        
        return 0
    }
    
    private fun hasPartialMatch(query: String, target: String, allowedTypos: Int): Boolean {
        // Check if we can find query in target with allowed number of character differences
        val words = target.split(" ")
        
        for (word in words) {
            if (word.length >= query.length - allowedTypos && 
                word.length <= query.length + allowedTypos) {
                
                var differences = 0
                val minLength = minOf(query.length, word.length)
                
                // Count character differences
                for (i in 0 until minLength) {
                    if (i < query.length && i < word.length && 
                        query[i] != word[i]) {
                        differences++
                    }
                }
                
                // Add length difference to differences count
                differences += kotlin.math.abs(query.length - word.length)
                
                if (differences <= allowedTypos) {
                    return true
                }
            }
        }
        
        return false
    }
    
    
    private fun loadRecentlyPlayed() {
        val recentlyPlayed = searchPlayHistoryManager.getHistory()
        recentlyPlayedAdapter.updateHistory(recentlyPlayed)
        showRecentlyPlayed()
    }
    
    private fun refreshRecentlyPlayed() {
        loadRecentlyPlayed()
    }
    
    private fun showRecentlyPlayedOptions(historyItem: SearchPlayHistory) {
        // Find the actual song object
        val song = allSongs.find { it.id == historyItem.songId }
        if (song != null) {
            showQueueOptionsDialog(song)
        } else {
            Toast.makeText(requireContext(), "Song not found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showQueueOptionsDialog(song: Song) {
        val options = arrayOf("Add to Queue", "Add Next", "Add to playlist")
        
        AlertDialog.Builder(requireContext())
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> addSongToQueue(song)
                    1 -> addSongNext(song)
                    2 -> showPlaylistSelectionDialog(song)
                }
            }
            .show()
    }
    
    private fun addSongToQueue(song: Song) {
        if (serviceBound && musicService != null) {
            musicService!!.addToQueue(song)
            Toast.makeText(requireContext(), "Added '${song.title}' to queue", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Music service not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addSongNext(song: Song) {
        if (serviceBound && musicService != null) {
            musicService!!.addNext(song)
            Toast.makeText(requireContext(), "Added '${song.title}' to play next", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Music service not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showPlaylistSelectionDialog(song: Song) {
        if (allSongs.isEmpty()) {
            Toast.makeText(requireContext(), "No songs available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = PlaylistSelectionDialog.newInstance(song, allSongs)
        dialog.show(parentFragmentManager, "PlaylistSelectionDialog")
    }
    
    private fun removeFromSearchHistory(historyItem: SearchPlayHistory) {
        searchPlayHistoryManager.removeFromHistory(historyItem.songId)
        refreshRecentlyPlayed()
    }
    
    private fun organizeSongsIntoAlbums(songList: List<Song>): List<Album> {
        val sortedSongs = songList.sortedBy { it.sortOrder }
        val albumGroups = sortedSongs.groupBy { song ->
            song.album.ifEmpty { "Unknown Album" }
        }
        return albumGroups.map { (albumName, albumSongs) ->
            Album(
                name = albumName,
                artist = albumSongs.firstOrNull()?.artist?.takeIf { it.isNotEmpty() },
                songs = albumSongs
            )
        }.sortedBy { it.name }
    }

}


// Adapter for mixed search results (songs and albums)
class SearchResultsAdapter(
    private val onSongClick: (Song, Album) -> Unit,
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SONG = 0
        private const val VIEW_TYPE_ALBUM = 1
    }

    private var searchResults = listOf<SearchResult>()

    fun updateResults(newResults: List<SearchResult>) {
        searchResults = newResults
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (searchResults[position]) {
            is SearchResult.SongResult -> VIEW_TYPE_SONG
            is SearchResult.AlbumResult -> VIEW_TYPE_ALBUM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SONG -> {
                val binding = ItemSongBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SongViewHolder(binding)
            }
            VIEW_TYPE_ALBUM -> {
                val binding = ItemAlbumHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AlbumViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val result = searchResults[position]) {
            is SearchResult.SongResult -> {
                (holder as SongViewHolder).bind(result.song, result.album)
            }
            is SearchResult.AlbumResult -> {
                (holder as AlbumViewHolder).bind(result.album)
            }
        }
    }

    override fun getItemCount() = searchResults.size

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, album: Album) {
            binding.titleText.text = song.title
            binding.artistText.text = song.artist.ifEmpty { "Unknown Artist" }
            
            // Load album artwork for the song
            loadAlbumArtwork(song)
            
            binding.root.setOnClickListener {
                onSongClick(song, album)
            }
        }
        
        private fun loadAlbumArtwork(song: Song) {
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
                    .into(binding.albumArtwork)
            } else {
                binding.albumArtwork.setImageResource(R.drawable.ic_music_note)
            }
        }
    }

    inner class AlbumViewHolder(
        private val binding: ItemAlbumHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.albumNameText.text = album.name
            binding.artistText.text = album.artist ?: "Unknown Artist"
            binding.trackCountText.text = "• ${album.trackCount} tracks"
            
            // Show right chevron to indicate navigation
            binding.expandIcon.setImageResource(R.drawable.ic_chevron_right)
            
            // Load album artwork
            loadAlbumArtwork(album)
            
            binding.root.setOnClickListener {
                onAlbumClick(album)
            }
        }
        
        private fun loadAlbumArtwork(album: Album) {
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
                    
                    Glide.with(binding.root.context)
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
    }
}



// Adapter for recently played songs from search
class RecentlyPlayedAdapter(
    private val onItemClick: (SearchPlayHistory) -> Unit,
    private val onItemLongClick: (SearchPlayHistory) -> Unit,
    private val onRemoveClick: (SearchPlayHistory) -> Unit
) : RecyclerView.Adapter<RecentlyPlayedAdapter.ViewHolder>() {

    private var playHistory = listOf<SearchPlayHistory>()

    fun updateHistory(newHistory: List<SearchPlayHistory>) {
        this.playHistory = newHistory
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentlyPlayedSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(playHistory[position])
    }

    override fun getItemCount() = playHistory.size

    inner class ViewHolder(private val binding: ItemRecentlyPlayedSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(historyItem: SearchPlayHistory) {
            binding.titleText.text = historyItem.songTitle
            binding.artistText.text = historyItem.artist
            
            // Try to load artwork, fall back to default icon
            val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${historyItem.songId}"
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
                    .into(binding.albumArtwork)
            } else {
                binding.albumArtwork.setImageResource(R.drawable.ic_music_note)
            }
            
            binding.root.setOnClickListener {
                onItemClick(historyItem)
            }
            
            binding.root.setOnLongClickListener {
                onItemLongClick(historyItem)
                true
            }
            
            binding.removeButton.setOnClickListener {
                onRemoveClick(historyItem)
            }
        }
    }
}