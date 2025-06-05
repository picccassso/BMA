package com.bma.android.setup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bma.android.R
import com.bma.android.setup.fragments.LoadingFragment
import com.bma.android.setup.fragments.QRScannerFragment
import com.bma.android.setup.fragments.TailscaleCheckFragment
import com.bma.android.setup.fragments.WelcomeFragment

class SetupActivity : AppCompatActivity() {
    private lateinit var viewModel: SetupViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        
        viewModel = ViewModelProvider(this)[SetupViewModel::class.java]
        
        // Start with welcome fragment if no saved state
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.setup_container, WelcomeFragment())
                .commit()
        }
    }
    
    fun navigateToNext(currentFragment: Fragment) {
        val nextFragment = when (currentFragment) {
            is WelcomeFragment -> TailscaleCheckFragment()
            is TailscaleCheckFragment -> QRScannerFragment()
            is QRScannerFragment -> LoadingFragment()
            else -> null
        }
        
        nextFragment?.let {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(R.id.setup_container, it)
                .addToBackStack(null)
                .commit()
        }
    }
    
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
} 