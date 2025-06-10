package com.bma.android.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ItemQueueCurrentSongBinding
import com.bma.android.databinding.ItemQueueSongBinding
import com.bma.android.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class QueueAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onRemoveClick: (Song, Int) -> Unit,
    private val onPlayPauseClick: () -> Unit,
    val onReorder: (Int, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CURRENT_SONG = 0
        private const val TYPE_QUEUE_ITEM = 1
    }
    
    private var currentSong: Song? = null
    private var queueItems: MutableList<Song> = mutableListOf()
    private var originalQueueItems: List<Song> = emptyList() // Backup for drag operations
    private var isPlaying: Boolean = false
    private var currentProgress: Int = 0
    private var currentDuration: Int = 0
    private var itemTouchHelper: ItemTouchHelper? = null
    private var isDragging: Boolean = false
    
    fun updateQueue(currentSong: Song?, queueItems: List<Song>, isPlaying: Boolean) {
        this.currentSong = currentSong
        this.isPlaying = isPlaying
        
        // Only block updates during active dragging
        if (!isDragging) {
            android.util.Log.d("QueueDrag", "✅ Updating queue normally: ${queueItems.size} items")
            this.queueItems.clear()
            this.queueItems.addAll(queueItems)
            this.originalQueueItems = queueItems.toList()
            notifyDataSetChanged()
        } else {
            android.util.Log.d("QueueDrag", "🚫 Ignoring queue update during drag")
        }
    }
    
    fun updateProgress(progress: Int, duration: Int) {
        // Only update if values actually changed
        if (currentSong != null && (progress != this.currentProgress || duration != this.currentDuration)) {
            this.currentProgress = progress
            this.currentDuration = duration
            // Use payload to indicate this is just a progress update, not full refresh
            notifyItemChanged(0, "progress_update")
        }
    }
    
    fun hasCurrentSong(): Boolean = currentSong != null
    
    fun setDragState(dragging: Boolean) {
        isDragging = dragging
    }
    
    /**
     * Start drag operation - backup original queue state
     */
    fun startDrag(startPosition: Int) {
        isDragging = true
        originalQueueItems = queueItems.toList()
        android.util.Log.d("QueueDrag", "Started drag from position $startPosition, queue size: ${queueItems.size}")
    }
    
    /**
     * Move item visually during drag for smooth real-time feedback
     */
    fun moveItemVisually(fromPosition: Int, toPosition: Int) {
        if (!isDragging) return
        
        // Only move queue items, not the current song (position 0)
        val hasCurrentSong = hasCurrentSong()
        val fromQueueIndex = if (hasCurrentSong) fromPosition - 1 else fromPosition
        val toQueueIndex = if (hasCurrentSong) toPosition - 1 else toPosition
        
        if (fromQueueIndex >= 0 && fromQueueIndex < queueItems.size &&
            toQueueIndex >= 0 && toQueueIndex < queueItems.size) {
            
            // Move item in the list
            val item = queueItems.removeAt(fromQueueIndex)
            queueItems.add(toQueueIndex, item)
            
            // Notify adapter for smooth animation
            notifyItemMoved(fromPosition, toPosition)
            
            android.util.Log.d("QueueDrag", "Moved visually: $fromPosition -> $toPosition (queue: $fromQueueIndex -> $toQueueIndex)")
        }
    }
    
    /**
     * End drag operation without relying on final position - figure it out from queue state
     */
    fun endDragWithoutFinalPosition(startPosition: Int) {
        android.util.Log.d("QueueDrag", "🎯 === ENDING DRAG OPERATION ===")
        android.util.Log.d("QueueDrag", "startPosition=$startPosition")
        
        isDragging = false
        
        if (startPosition != -1) {
            // Calculate the original queue position
            val hasCurrentSong = hasCurrentSong()
            val originalFromQueuePosition = if (hasCurrentSong) startPosition - 1 else startPosition
            
            android.util.Log.d("QueueDrag", "hasCurrentSong=$hasCurrentSong")
            android.util.Log.d("QueueDrag", "originalFromQueuePosition=$originalFromQueuePosition")
            android.util.Log.d("QueueDrag", "originalQueue size=${originalQueueItems.size}")
            android.util.Log.d("QueueDrag", "currentQueue size=${queueItems.size}")
            
            // Log the original queue
            android.util.Log.d("QueueDrag", "📝 Original queue:")
            originalQueueItems.forEachIndexed { index, song ->
                android.util.Log.d("QueueDrag", "  [$index] ${song.title}")
            }
            
            // Log the current queue
            android.util.Log.d("QueueDrag", "📝 Current queue:")
            queueItems.forEachIndexed { index, song ->
                android.util.Log.d("QueueDrag", "  [$index] ${song.title}")
            }
            
            // Find what song was moved by looking at the original queue
            if (originalFromQueuePosition >= 0 && originalFromQueuePosition < originalQueueItems.size) {
                val movedSong = originalQueueItems[originalFromQueuePosition]
                
                // Find where this song ended up in the current (visually updated) queue
                val currentToIndex = queueItems.indexOf(movedSong)
                
                android.util.Log.d("QueueDrag", "🎵 Moved song: '${movedSong.title}'")
                android.util.Log.d("QueueDrag", "📍 Original position: $originalFromQueuePosition")
                android.util.Log.d("QueueDrag", "📍 Current position: $currentToIndex")
                
                if (currentToIndex >= 0 && originalFromQueuePosition != currentToIndex) {
                    android.util.Log.d("QueueDrag", "🚀 COMMITTING reorder: $originalFromQueuePosition -> $currentToIndex")
                    
                    // Commit to MusicService using original positions
                    onReorder(originalFromQueuePosition, currentToIndex)
                    
                    android.util.Log.d("QueueDrag", "✅ Sent reorder to service")
                    
                    // Don't ignore next update - let the service send back the authoritative queue
                    // The visual state is already correct, service update will confirm it
                } else if (currentToIndex < 0) {
                    android.util.Log.e("QueueDrag", "❌ Could not find moved song in current queue!")
                    // Restore original state
                    queueItems.clear()
                    queueItems.addAll(originalQueueItems)
                    notifyDataSetChanged()
                } else {
                    android.util.Log.d("QueueDrag", "ℹ️ No position change detected: $originalFromQueuePosition == $currentToIndex")
                }
            } else {
                android.util.Log.w("QueueDrag", "❌ Invalid start position: $originalFromQueuePosition (queue size: ${originalQueueItems.size})")
                // Restore original state
                queueItems.clear()
                queueItems.addAll(originalQueueItems)
                notifyDataSetChanged()
            }
        } else {
            android.util.Log.d("QueueDrag", "ℹ️ No valid start position, restored original queue")
            // No actual move happened, restore original state
            queueItems.clear()
            queueItems.addAll(originalQueueItems)
            notifyDataSetChanged()
        }
        
        android.util.Log.d("QueueDrag", "🏁 === DRAG OPERATION COMPLETE ===")
    }
    
    /**
     * End drag operation - commit changes to MusicService (legacy method)
     */
    fun endDrag(startPosition: Int, finalPosition: Int) {
        // Delegate to the new method that doesn't rely on finalPosition
        endDragWithoutFinalPosition(startPosition)
    }
    
    fun setupDragAndDrop(recyclerView: RecyclerView) {
        itemTouchHelper = ItemTouchHelper(QueueItemTouchHelper(this))
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && currentSong != null) {
            TYPE_CURRENT_SONG
        } else {
            TYPE_QUEUE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CURRENT_SONG -> {
                val binding = ItemQueueCurrentSongBinding.inflate(inflater, parent, false)
                CurrentSongViewHolder(binding)
            }
            TYPE_QUEUE_ITEM -> {
                val binding = ItemQueueSongBinding.inflate(inflater, parent, false)
                val viewHolder = QueueItemViewHolder(binding)
                // Set the ItemTouchHelper reference if available
                itemTouchHelper?.let { helper ->
                    viewHolder.setItemTouchHelper(helper)
                }
                viewHolder
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CurrentSongViewHolder -> {
                currentSong?.let { song ->
                    holder.bind(song, isPlaying, currentProgress, currentDuration)
                }
            }
            is QueueItemViewHolder -> {
                val queuePosition = if (currentSong != null) position - 1 else position
                if (queuePosition >= 0 && queuePosition < queueItems.size) {
                    val isFirstQueueItem = queuePosition == 0 && currentSong != null
                    holder.bind(queueItems[queuePosition], queuePosition + 1, isFirstQueueItem)
                }
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains("progress_update")) {
            // Handle partial update for progress only
            if (holder is CurrentSongViewHolder) {
                holder.updateProgressOnly(currentProgress, currentDuration)
            }
        } else {
            // Full update
            onBindViewHolder(holder, position)
        }
    }

    override fun getItemCount(): Int {
        val currentSongCount = if (currentSong != null) 1 else 0
        return currentSongCount + queueItems.size
    }

    inner class CurrentSongViewHolder(
        private val binding: ItemQueueCurrentSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, isPlaying: Boolean, progress: Int, duration: Int) {
            // Update song info
            binding.songTitle.text = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
            binding.songArtist.text = song.artist.ifEmpty { "Unknown Artist" }
            
            // Update play/pause button
            binding.playPauseButton.setImageResource(
                if (isPlaying) com.bma.android.R.drawable.ic_pause else com.bma.android.R.drawable.ic_play_arrow
            )
            
            // Update progress
            if (duration > 0) {
                val progressPercent = (progress * 100) / duration
                binding.progressBar.progress = progressPercent
            } else {
                binding.progressBar.progress = 0
            }
            
            // Load album artwork
            loadArtwork(song, binding.albumArtwork)
            
            // Set click listeners
            binding.playPauseButton.setOnClickListener {
                onPlayPauseClick()
            }
            
            binding.root.setOnClickListener {
                // Optional: Could navigate to PlayerActivity
            }
        }
        
        fun updateProgressOnly(progress: Int, duration: Int) {
            // Only update progress bar, don't touch other UI elements
            if (duration > 0) {
                val progressPercent = (progress * 100) / duration
                binding.progressBar.progress = progressPercent
            } else {
                binding.progressBar.progress = 0
            }
        }
    }

    inner class QueueItemViewHolder(
        private val binding: ItemQueueSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var itemTouchHelper: ItemTouchHelper? = null
        
        fun setItemTouchHelper(helper: ItemTouchHelper) {
            itemTouchHelper = helper
        }

        fun bind(song: Song, position: Int, isFirstItem: Boolean) {
            // Show "UP NEXT" header for first queue item, but make it non-draggable
            binding.sectionHeader.isVisible = isFirstItem
            
            // Update song info
            binding.songTitle.text = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
            binding.songArtist.text = song.artist.ifEmpty { "Unknown Artist" }
            binding.queuePosition.text = position.toString()
            
            // Load album artwork
            loadArtwork(song, binding.albumArtwork)
            
            // Set click listeners - only on the draggable container, not the header
            binding.draggableContainer.setOnClickListener {
                val queuePosition = if (currentSong != null) adapterPosition - 1 else adapterPosition
                onSongClick(song, queuePosition)
            }
            
            binding.removeButton.setOnClickListener {
                val queuePosition = if (currentSong != null) adapterPosition - 1 else adapterPosition
                onRemoveClick(song, queuePosition)
            }
            
            // Drag handle functionality - ONLY from the drag handle
            binding.dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        android.util.Log.d("QueueDrag", "🤏 Drag handle touched on position $adapterPosition")
                        itemTouchHelper?.startDrag(this)
                        true
                    }
                    else -> false
                }
            }
            
            // Header is now in FrameLayout structure - completely isolated from drag operations
            // No additional isolation needed since header is outside draggable container
        }
    }
    
    private fun loadArtwork(song: Song, imageView: android.widget.ImageView) {
        val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        if (authHeader != null) {
            val glideUrl = GlideUrl(
                artworkUrl, 
                LazyHeaders.Builder()
                    .addHeader("Authorization", authHeader)
                    .build()
            )
            
            Glide.with(imageView.context)
                .load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(com.bma.android.R.drawable.ic_music_note)
                .error(com.bma.android.R.drawable.ic_music_note)
                .into(imageView)
        } else {
            imageView.setImageResource(com.bma.android.R.drawable.ic_music_note)
        }
    }
}

/**
 * ItemTouchHelper callback for drag-and-drop reordering in queue
 * Modified to only drag the draggable container, not the header
 */
class QueueItemTouchHelper(
    private val adapter: QueueAdapter
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN, // Drag directions
    0 // No swipe (we handle remove with button)
) {
    
    private var dragStartPosition = -1
    
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Don't allow dragging the "Now Playing" item (position 0)
        if (viewHolder.adapterPosition == 0 && adapter.hasCurrentSong()) {
            return 0 // No movement for current song
        }
        
        // Only allow queue items to be dragged, not current song
        if (viewHolder !is QueueAdapter.QueueItemViewHolder) {
            return 0 // No movement for non-queue items
        }
        
        // Only allow drag from drag handle, not from anywhere else
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        
        // Don't allow moving to/from position 0 (current song)
        if (fromPosition == 0 || toPosition == 0) return false
        
        // Perform real-time visual update for smooth dragging
        adapter.moveItemVisually(fromPosition, toPosition)
        
        return true
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                // Drag started - make sure it's a valid queue item
                if (viewHolder is QueueAdapter.QueueItemViewHolder) {
                    dragStartPosition = viewHolder.adapterPosition
                    adapter.startDrag(dragStartPosition)
                    android.util.Log.d("QueueDrag", "ItemTouchHelper: Drag started at position: $dragStartPosition")
                } else {
                    android.util.Log.w("QueueDrag", "ItemTouchHelper: Invalid drag attempt on non-queue item")
                    dragStartPosition = -1
                }
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Drag ended - but viewHolder.adapterPosition is unreliable here
                // Let the adapter figure out the final position based on current queue state
                if (dragStartPosition != -1) {
                    adapter.endDragWithoutFinalPosition(dragStartPosition)
                    android.util.Log.d("QueueDrag", "ItemTouchHelper: Drag ended from start position: $dragStartPosition")
                }
                
                // Reset
                dragStartPosition = -1
            }
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // We don't use swipe for removal, only button clicks
    }
    
    override fun isLongPressDragEnabled(): Boolean = false // We'll use drag handle
    
    override fun isItemViewSwipeEnabled(): Boolean = false // We use remove button instead
    
    override fun onChildDraw(
        c: android.graphics.Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        // For queue items, only move the draggable container, not the header
        if (viewHolder is QueueAdapter.QueueItemViewHolder && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Find the draggable container and only translate that
            val draggableContainer = viewHolder.itemView.findViewById<android.view.View>(com.bma.android.R.id.draggableContainer)
            if (draggableContainer != null) {
                // Only move the draggable container, header stays in place
                draggableContainer.translationY = dY
                android.util.Log.d("QueueDrag", "🎯 Moving only draggable container: dY=$dY")
            } else {
                // Fallback to default behavior
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        } else {
            // Default behavior for other view types
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}