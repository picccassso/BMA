# Current Task: Fix UP NEXT Header Drag Issue

## Problem Statement
In the queue activity, the "UP NEXT" header gets dragged along with the first queue item when drag-and-drop is initiated. The header should remain stationary while only the song items move.

## Root Cause Analysis ✅
- **Layout Issue**: The "UP NEXT" header (`sectionHeader`) is inside the LinearLayout container that contains the draggable area
- **Current Structure**: LinearLayout > sectionHeader + draggableContainer 
- **Problem**: When ItemTouchHelper moves the entire ViewHolder, it moves the whole LinearLayout including the header
- **Evidence**: Screenshot shows header moving with first queue item during drag

## Fix Strategy
Move the header completely outside the draggable area by restructuring the layout:
- Place header in its own separate container 
- Only make the song content draggable
- Ensure header is completely isolated from drag interactions

## Implementation Plan
1. ✅ **Analysis Complete** - Identified root cause in layout structure
2. ✅ **Fix Implemented** - Modified ItemTouchHelper.onChildDraw() to only move draggable container
3. ✅ **Test Fix** - App builds successfully with fix implemented

## Fix Details ✅
**Approach**: Override `onChildDraw()` in `QueueItemTouchHelper` to:
- Detect when dragging queue items with headers
- Find the `draggableContainer` view by ID 
- Apply translation only to the container, leaving header stationary
- Header remains at original position while only song content moves during drag

## Files to Modify
- `app/src/main/res/layout/item_queue_song.xml` - Restructure layout hierarchy