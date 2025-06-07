package com.bma.android.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.AlbumDetailActivity
import com.bma.android.MusicService
import com.bma.android.PlayerActivity
import com.bma.android.R
import com.bma.android.adapters.AlbumAdapter
import com.bma.android.api.ApiClient
import com.bma.android.databinding.FragmentLibraryBinding
import com.bma.android.models.Album
import com.bma.android.models.Song
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(R.layout.fragment_library) {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var albumAdapter: AlbumAdapter
    private var albums: List<Album> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLibraryBinding.bind(view)

        setupRecyclerView()
        loadAlbums()
    }

    private fun setupRecyclerView() {
        albumAdapter = AlbumAdapter(
            onSongClick = { song ->
                val album = albums.find { it.songs.contains(song) }
                if (album != null) {
                    // Start music service
                    val serviceIntent = Intent(requireContext(), MusicService::class.java)
                    requireContext().startService(serviceIntent)
                    
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
            },
            onAlbumClick = { album ->
                val intent = Intent(requireContext(), AlbumDetailActivity::class.java).apply {
                    putExtra("album", album)
                }
                startActivity(intent)
            }
        )

        binding.albumsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = albumAdapter
        }
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            binding.errorText.isVisible = false
            try {
                val authHeader = ApiClient.getAuthHeader()
                if (authHeader == null) {
                    showError("Not authenticated. Please connect in settings.")
                    return@launch
                }

                val songList = ApiClient.api.getSongs(authHeader)
                albums = organizeSongsIntoAlbums(songList)
                albumAdapter.updateAlbums(albums)
                binding.albumsRecyclerView.isVisible = albums.isNotEmpty()

            } catch (e: Exception) {
                showError("Failed to load albums: ${e.message}")
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.isVisible = true
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
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