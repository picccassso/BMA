package com.bma.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.Fragment

class NavigationTransitionAnimator(private val container: ViewGroup) {
    
    private var blackOverlay: View? = null
    private var isAnimating = false
    
    companion object {
        private const val FADE_DURATION = 300L // Slightly faster for navigation
        private val SMOOTH_INTERPOLATOR = AccelerateDecelerateInterpolator()
    }
    
    fun transitionToFragment(
        fragmentContainer: View,
        onTransitionComplete: () -> Unit = {}
    ) {
        if (isAnimating) return
        isAnimating = true

        // Create black overlay if it doesn't exist
        if (blackOverlay == null) {
            blackOverlay = View(container.context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                alpha = 0f
            }
        }
        
        // Add overlay to container
        blackOverlay?.let { overlay ->
            container.addView(overlay)
            
            // Fade fragment container out and fade to black simultaneously
            val contentFadeOut = ObjectAnimator.ofFloat(fragmentContainer, "alpha", 1f, 0f).apply {
                duration = FADE_DURATION
                interpolator = SMOOTH_INTERPOLATOR
            }
            
            val overlayFadeIn = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
                duration = FADE_DURATION
                interpolator = SMOOTH_INTERPOLATOR
            }
            
            contentFadeOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Brief pause at black screen for smooth transition
                    overlay.postDelayed({
                        // Fragment switching happens here
                        onTransitionComplete()
                        
                        // Reset fragment container properties
                        fragmentContainer.visibility = View.VISIBLE
                        fragmentContainer.alpha = 0f
                        
                        // Fade out black overlay and fade in new content simultaneously
                        val overlayFadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
                            duration = FADE_DURATION
                            interpolator = SMOOTH_INTERPOLATOR
                        }
                        
                        val contentFadeIn = ObjectAnimator.ofFloat(fragmentContainer, "alpha", 0f, 1f).apply {
                            duration = FADE_DURATION
                            interpolator = SMOOTH_INTERPOLATOR
                        }
                        
                        overlayFadeOut.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                // Remove the black overlay
                                container.removeView(overlay)
                                isAnimating = false
                            }
                        })
                        
                        // Start both animations together
                        overlayFadeOut.start()
                        contentFadeIn.start()
                    }, 150) // Brief pause at black screen (same as AlbumTransitionAnimator)
                }
            })
            
            // Start both animations together
            contentFadeOut.start()
            overlayFadeIn.start()
        }
    }
    
    fun isCurrentlyAnimating(): Boolean = isAnimating
}