package com.bma.android.setup.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import com.bma.android.R
import com.google.android.material.button.MaterialButton

class WelcomeFragment : BaseSetupFragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Find views
        val logo = view.findViewById<ImageView>(R.id.logo)
        val title = view.findViewById<TextView>(R.id.welcome_title)
        val subtitle = view.findViewById<TextView>(R.id.welcome_subtitle)
        val getStartedButton = view.findViewById<MaterialButton>(R.id.get_started_button)
        
        // Set up fade-in animations
        setupFadeInAnimation(logo, 0)
        setupFadeInAnimation(title, 300)
        setupFadeInAnimation(subtitle, 600)
        setupFadeInAnimation(getStartedButton, 900)
        
        // Set up button click
        getStartedButton.setOnClickListener {
            navigateToNext()
        }
    }
    
    private fun setupFadeInAnimation(view: View, startOffset: Long) {
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 500
            this.startOffset = startOffset
            fillAfter = true
        }
        
        fadeIn.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {
                view.visibility = View.VISIBLE
            }
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        
        view.visibility = View.INVISIBLE
        view.startAnimation(fadeIn)
    }
} 