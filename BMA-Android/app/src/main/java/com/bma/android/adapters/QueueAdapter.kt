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
    private var isPlaying: Boolean = false
    private var currentProgress: Int = 0
    private var currentDuration: Int = 0
    
    fun updateQueue(currentSong: Song?, queueItems: List<Song>, isPlaying: Boolean) {
        this.currentSong = currentSong
        this.queueItems.clear()
        this.queueItems.addAll(queueItems)
        this.isPlaying = isPlaying
        notifyDataSetChanged()
    }
    
    fun updateProgress(progress: Int, duration: Int) {
        if (currentSong != null && (progress != this.currentProgress || duration != this.currentDuration)) {
            this.currentProgress = progress
            this.currentDuration = duration
            notifyItemChanged(0, "progress_update")
        }
    }
    
    fun setupDragAndDrop(recyclerView: RecyclerView) {
        val itemTouchHelper = ItemTouchHelper(QueueItemTouchHelper(this))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        this.itemTouchHelper = itemTouchHelper
    }
    
    private var itemTouchHelper: ItemTouchHelper? = null
    
    /**
     * Move item visually during drag without committing to service
     */
    fun moveItemVisually(fromPosition: Int, toPosition: Int) {
        val hasCurrentSong = currentSong != null
        val fromQueueIndex = if (hasCurrentSong) fromPosition - 1 else fromPosition
        val toQueueIndex = if (hasCurrentSong) toPosition - 1 else toPosition
        
        if (fromQueueIndex >= 0 && fromQueueIndex < queueItems.size &&
            toQueueIndex >= 0 && toQueueIndex < queueItems.size) {
            
            val item = queueItems.removeAt(fromQueueIndex)
            queueItems.add(toQueueIndex, item)
            notifyItemMoved(fromPosition, toPosition)
        }
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
                viewHolder.setItemTouchHelper(itemTouchHelper)
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
                    holder.bind(queueItems[queuePosition], queuePosition + 1)
                }
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains("progress_update")) {
            if (holder is CurrentSongViewHolder) {
                holder.updateProgressOnly(currentProgress, currentDuration)
            }
        } else {
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
            binding.songTitle.text = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
            binding.songArtist.text = song.artist.ifEmpty { "Unknown Artist" }
            
            binding.playPauseButton.setImageResource(
                if (isPlaying) com.bma.android.R.drawable.ic_pause else com.bma.android.R.drawable.ic_play_arrow
            )
            
            if (duration > 0) {
                val progressPercent = (progress * 100) / duration
                binding.progressBar.progress = progressPercent
            } else {
                binding.progressBar.progress = 0
            }
            
            loadArtwork(song, binding.albumArtwork)
            
            binding.playPauseButton.setOnClickListener {
                onPlayPauseClick()
            }
        }
        
        fun updateProgressOnly(progress: Int, duration: Int) {
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
        
        fun setItemTouchHelper(helper: ItemTouchHelper?) {
            itemTouchHelper = helper
        }

        fun bind(song: Song, position: Int) {
            binding.songTitle.text = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
            binding.songArtist.text = song.artist.ifEmpty { "Unknown Artist" }
            binding.queuePosition.text = position.toString()
            
            loadArtwork(song, binding.albumArtwork)
            
            binding.draggableContainer.setOnClickListener {
                val queuePosition = if (currentSong != null) adapterPosition - 1 else adapterPosition
                onSongClick(song, queuePosition)
            }
            
            binding.removeButton.setOnClickListener {
                // Pass the upcoming queue position - QueueActivity will convert to full queue position
                val upcomingQueuePosition = if (currentSong != null) adapterPosition - 1 else adapterPosition
                onRemoveClick(song, upcomingQueuePosition)
            }
            
            // Drag handle functionality
            binding.dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        itemTouchHelper?.startDrag(this)
                        true
                    }
                    else -> false
                }
            }
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
 * Simple, clean ItemTouchHelper for drag-and-drop
 */
class QueueItemTouchHelper(
    private val adapter: QueueAdapter
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0
) {
    
    private var dragStartPosition = -1
    private var dragEndPosition = -1
    
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Don't allow dragging the current song (position 0)
        if (viewHolder.adapterPosition == 0 && viewHolder is QueueAdapter.CurrentSongViewHolder) {
            return 0
        }
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        
        // Don't allow moving to/from current song position
        if (fromPosition == 0 || toPosition == 0) return false
        
        // Perform visual move only
        adapter.moveItemVisually(fromPosition, toPosition)
        
        // Track the end position for when drag completes
        dragEndPosition = toPosition
        
        return true
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                // Remember start position
                dragStartPosition = viewHolder?.adapterPosition ?: -1
                dragEndPosition = dragStartPosition
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Drag ended - commit to service
                if (dragStartPosition != -1 && dragEndPosition != -1 && dragStartPosition != dragEndPosition) {
                    // Convert to queue positions
                    val hasCurrentSong = dragStartPosition > 0
                    val fromQueuePos = if (hasCurrentSong) dragStartPosition - 1 else dragStartPosition
                    val toQueuePos = if (hasCurrentSong) dragEndPosition - 1 else dragEndPosition
                    
                    android.util.Log.d("QueueDrag", "Committing drag: $dragStartPosition -> $dragEndPosition (queue: $fromQueuePos -> $toQueuePos)")
                    
                    // Commit to service
                    adapter.onReorder(fromQueuePos, toQueuePos)
                }
                dragStartPosition = -1
                dragEndPosition = -1
            }
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe functionality
    }
    
    override fun isLongPressDragEnabled(): Boolean = false
    
    override fun isItemViewSwipeEnabled(): Boolean = false
}