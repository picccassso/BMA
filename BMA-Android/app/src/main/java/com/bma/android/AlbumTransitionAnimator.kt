package com.bma.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.isVisible

class AlbumTransitionAnimator(private val container: ViewGroup) {
    
    private var blackOverlay: View? = null
    private var isAnimating = false
    
    companion object {
        private const val FADE_DURATION = 375L // 0.75 seconds total timing
        private val SMOOTH_INTERPOLATOR = AccelerateDecelerateInterpolator()
    }
    
    fun fadeToBlackAndShowContent(
        contentView: View,
        onAnimationComplete: () -> Unit = {}
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
            
            // Fade in to black
            ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
                duration = FADE_DURATION
                interpolator = SMOOTH_INTERPOLATOR
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Brief pause at black screen for smooth transition
                        overlay.postDelayed({
                            // Show content first (invisible)
                            contentView.visibility = View.VISIBLE
                            contentView.alpha = 0f
                            
                            // Fade out black overlay and fade in content simultaneously
                            val overlayFadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
                                duration = FADE_DURATION
                                interpolator = SMOOTH_INTERPOLATOR
                            }
                            
                            val contentFadeIn = ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f).apply {
                                duration = FADE_DURATION
                                interpolator = SMOOTH_INTERPOLATOR
                            }
                            
                            overlayFadeOut.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    // Remove the black overlay
                                    container.removeView(overlay)
                                    isAnimating = false
                                    onAnimationComplete()
                                }
                            })
                            
                            // Start both animations together
                            overlayFadeOut.start()
                            contentFadeIn.start()
                        }, 150) // Brief pause at black screen
                    }
                })
                start()
            }
        }
    }
    
    fun fadeToBlackAndHideContent(
        contentView: View,
        onAnimationComplete: () -> Unit = {}
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
        
        blackOverlay?.let { overlay ->
            container.addView(overlay)
            
            // Fade content out and fade to black simultaneously
            val contentFadeOut = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f).apply {
                duration = FADE_DURATION
                interpolator = SMOOTH_INTERPOLATOR
            }
            
            val overlayFadeIn = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
                duration = FADE_DURATION
                interpolator = SMOOTH_INTERPOLATOR
            }
            
            contentFadeOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Hide content view
                    contentView.visibility = View.GONE
                    
                    // Brief pause at black, then fade out
                    overlay.postDelayed({
                        ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
                            duration = FADE_DURATION
                            interpolator = SMOOTH_INTERPOLATOR
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    // Remove overlay and call completion
                                    container.removeView(overlay)
                                    isAnimating = false
                                    onAnimationComplete()
                                }
                            })
                            start()
                        }
                    }, 150) // Brief pause at black screen
                }
            })
            
            // Start both animations together
            contentFadeOut.start()
            overlayFadeIn.start()
        }
    }
    
    fun isCurrentlyAnimating(): Boolean = isAnimating
}