package com.bma.android.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.AlbumDetailActivity
import com.bma.android.MusicService
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentSearchBinding
import com.bma.android.databinding.ItemRecentSearchBinding
import com.bma.android.databinding.ItemSongBinding
import com.bma.android.databinding.ItemAlbumHeaderBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
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
    private lateinit var recentSearchesAdapter: RecentSearchesAdapter
    private var allAlbums = listOf<Album>()
    private var allSongs = listOf<Song>()
    private var recentSearches = mutableListOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)

        setupRecyclerViews()
        setupSearchView()
        loadAllMusicForSearching()
        loadRecentSearches()
    }

    private fun setupRecyclerViews() {
        // For search results with mixed content (songs and albums)
        searchResultsAdapter = SearchResultsAdapter(
            onSongClick = { song, album ->
                // Start music service and play the song
                val serviceIntent = Intent(requireContext(), MusicService::class.java)
                requireContext().startService(serviceIntent)
                
                // Open PlayerActivity
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("song_id", song.id)
                    putExtra("song_title", song.title)
                    putExtra("song_artist", song.artist)
                    putExtra("album_name", album.name)
                    // For individual song search results, create a single-song playlist
                    putExtra("playlist_song_ids", arrayOf(song.id))
                    putExtra("playlist_song_titles", arrayOf(song.title))
                    putExtra("playlist_song_artists", arrayOf(song.artist))
                    putExtra("current_position", 0)
                }
                startActivity(intent)
            },
            onAlbumClick = { album ->
                val intent = Intent(requireContext(), AlbumDetailActivity::class.java).apply {
                    putExtra("album", album)
                }
                startActivity(intent)
            }
        )
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultsAdapter
        }

        // For recent searches
        recentSearchesAdapter = RecentSearchesAdapter(
            onItemClick = { query ->
                binding.searchView.setQuery(query, true)
            },
            onRemoveClick = { query ->
                removeRecentSearch(query)
            }
        )
        binding.recentSearchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentSearchesAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    performSearch(query)
                    addRecentSearch(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    showRecentSearches()
                }
                return true
            }
        })
    }
    
    private fun showRecentSearches() {
        binding.searchResultsRecyclerView.isVisible = false
        binding.recentSearchesRecyclerView.isVisible = true
        binding.recentSearchesTitle.isVisible = true
    }

    private fun showSearchResults() {
        binding.searchResultsRecyclerView.isVisible = true
        binding.recentSearchesRecyclerView.isVisible = false
        binding.recentSearchesTitle.isVisible = false
    }

    private fun loadAllMusicForSearching() {
        lifecycleScope.launch {
            try {
                val authHeader = ApiClient.getAuthHeader() ?: return@launch
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
        val searchResults = mutableListOf<SearchResult>()

        // Find individual songs that match the query
        allSongs.forEach { song ->
            if (song.title.lowercase().contains(lowerCaseQuery) || 
                song.artist.lowercase().contains(lowerCaseQuery)) {
                val album = allAlbums.find { it.songs.contains(song) }
                if (album != null) {
                    searchResults.add(SearchResult.SongResult(song, album))
                }
            }
        }

        // Find albums that match the query by name
        allAlbums.forEach { album ->
            if (album.name.lowercase().contains(lowerCaseQuery)) {
                searchResults.add(SearchResult.AlbumResult(album))
            }
        }

        searchResultsAdapter.updateResults(searchResults)
    }
    
    // --- Recent Searches Logic ---

    private fun getPrefs() = requireActivity().getSharedPreferences("BMA_Search", Context.MODE_PRIVATE)

    private fun loadRecentSearches() {
        val savedSearches = getPrefs().getStringSet("recent_searches", emptySet()) ?: emptySet()
        recentSearches.clear()
        recentSearches.addAll(savedSearches.sorted())
        recentSearchesAdapter.updateSearches(recentSearches)
        showRecentSearches()
    }

    private fun addRecentSearch(query: String) {
        if (!recentSearches.contains(query)) {
            recentSearches.add(0, query)
        }
        // Keep the list a reasonable size
        if (recentSearches.size > 10) {
            recentSearches.removeAt(recentSearches.size - 1)
        }
        saveRecentSearches()
        recentSearchesAdapter.updateSearches(recentSearches)
    }

    private fun removeRecentSearch(query: String) {
        recentSearches.remove(query)
        saveRecentSearches()
        recentSearchesAdapter.updateSearches(recentSearches)
    }

    private fun saveRecentSearches() {
        getPrefs().edit().putStringSet("recent_searches", recentSearches.toSet()).apply()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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


// A simple adapter for the recent searches list
class RecentSearchesAdapter(
    private val onItemClick: (String) -> Unit,
    private val onRemoveClick: (String) -> Unit
) : RecyclerView.Adapter<RecentSearchesAdapter.ViewHolder>() {

    private var searches = listOf<String>()

    fun updateSearches(newSearches: List<String>) {
        this.searches = newSearches
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentSearchBinding.inflate(parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(searches[position])
    }

    override fun getItemCount() = searches.size

    inner class ViewHolder(private val binding: ItemRecentSearchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(query: String) {
            binding.recentSearchText.text = query
            binding.root.setOnClickListener { onItemClick(query) }
            binding.removeRecentSearch.setOnClickListener { onRemoveClick(query) }
        }
    }
}