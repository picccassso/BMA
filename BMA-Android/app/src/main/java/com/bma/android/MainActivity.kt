package com.bma.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bma.android.databinding.ActivityMainBinding
import com.bma.android.ui.library.LibraryFragment
import com.bma.android.ui.search.SearchFragment
import com.bma.android.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val libraryFragment = LibraryFragment()
    private val searchFragment = SearchFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_library -> {
                    loadFragment(libraryFragment)
                    true
                }
                R.id.navigation_search -> {
                    loadFragment(searchFragment)
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }

        // Load the initial fragment
        if (savedInstanceState == null) {
            binding.bottomNavView.selectedItemId = R.id.navigation_library
            loadFragment(libraryFragment)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}