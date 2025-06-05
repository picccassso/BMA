package com.bma.android.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.adapters.AlbumAdapter
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentSearchBinding
import com.bma.android.databinding.ItemRecentSearchBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import kotlinx.coroutines.launch

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchResultsAdapter: AlbumAdapter
    private lateinit var recentSearchesAdapter: RecentSearchesAdapter
    private var allAlbums = listOf<Album>()
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
        // For search results, we can reuse the AlbumAdapter
        searchResultsAdapter = AlbumAdapter { song ->
            val album = allAlbums.find { it.songs.contains(song) }
            if (album != null) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("song_id", song.id)
                    putExtra("song_title", song.title)
                    putExtra("song_artist", song.artist)
                    putExtra("album_name", album.name)
                    val songIds = album.songs.map { it.id }.toTypedArray()
                    val songTitles = album.songs.map { it.title }.toTypedArray()
                    val songArtists = album.songs.map { it.artist }.toTypedArray()
                    putExtra("playlist_song_ids", songIds)
                    putExtra("playlist_song_titles", songTitles)
                    putExtra("playlist_song_artists", songArtists)
                    putExtra("current_position", album.songs.indexOf(song))
                }
                startActivity(intent)
            }
        }
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
                allAlbums = organizeSongsIntoAlbums(songList)
            } catch (e: Exception) {
                // Errors will be handled on the Library/Settings screen, silently fail here
            }
        }
    }

    private fun performSearch(query: String) {
        showSearchResults()
        val lowerCaseQuery = query.lowercase()

        val filteredAlbums = allAlbums.mapNotNull { album ->
            val matchingSongs = album.songs.filter { song ->
                song.title.lowercase().contains(lowerCaseQuery) ||
                song.artist.lowercase().contains(lowerCaseQuery)
            }
            if (matchingSongs.isNotEmpty() || album.name.lowercase().contains(lowerCaseQuery)) {
                // Return a new album instance with only matching songs, or all songs if album name matches
                 if (album.name.lowercase().contains(lowerCaseQuery)) {
                    album
                } else {
                    album.copy(songs = matchingSongs)
                }
            } else {
                null
            }
        }
        searchResultsAdapter.updateAlbums(filteredAlbums)
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