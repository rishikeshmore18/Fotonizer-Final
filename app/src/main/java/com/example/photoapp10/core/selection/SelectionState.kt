package com.example.photoapp10.core.selection

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Manages selection state for items with support for multiple selection modes
 * Uses stable IDs as keys to prevent issues when item properties change
 */
class SelectionState<T> {
    private val _selectedItems = mutableStateOf<SnapshotStateMap<Long, Int>>(SnapshotStateMap())
    val selectedItems: State<SnapshotStateMap<Long, Int>> = _selectedItems
    
    private val _isSelectionMode = mutableStateOf(false)
    val isSelectionMode: State<Boolean> = _isSelectionMode
    
    // Store the actual items for reference
    private val _itemMap = mutableStateOf<SnapshotStateMap<Long, T>>(SnapshotStateMap())
    val itemMap: State<SnapshotStateMap<Long, T>> = _itemMap
    
    fun toggleSelection(item: T) {
        try {
            val itemId = getItemId(item)
            if (itemId == null) {
                android.util.Log.e("SelectionState", "Cannot get ID for item: $item")
                return
            }
            
            val current = _selectedItems.value
            android.util.Log.d("SelectionState", "toggleSelection called for item ID: $itemId")
            android.util.Log.d("SelectionState", "Current selection state: $current")
            
            val newMap = SnapshotStateMap<Long, Int>()
            val newItemMap = SnapshotStateMap<Long, T>()
            
            if (current.containsKey(itemId)) {
                android.util.Log.d("SelectionState", "Removing item ID: $itemId")
                // Remove item and renumber remaining items
                val remainingItems = current.keys.filter { it != itemId }.toList().sortedBy { current[it] }
                android.util.Log.d("SelectionState", "Remaining items after removal: $remainingItems")
                remainingItems.forEachIndexed { index, remainingItemId ->
                    newMap[remainingItemId] = index + 1
                    _itemMap.value[remainingItemId]?.let { item ->
                        newItemMap[remainingItemId] = item
                    }
                }
                
                // Exit selection mode if no items selected
                if (newMap.isEmpty()) {
                    _isSelectionMode.value = false
                }
            } else {
                android.util.Log.d("SelectionState", "Adding item ID: $itemId")
                // Add item with next number - use current map size + 1
                val nextNumber = current.size + 1
                android.util.Log.d("SelectionState", "Next number for item ID: $nextNumber")
                newMap.putAll(current)
                newMap[itemId] = nextNumber
                newItemMap.putAll(_itemMap.value)
                newItemMap[itemId] = item
                _isSelectionMode.value = true
            }
            
            android.util.Log.d("SelectionState", "New selection state: $newMap")
            _selectedItems.value = newMap
            _itemMap.value = newItemMap
            android.util.Log.d("SelectionState", "Selection state updated successfully")
        } catch (e: Exception) {
            // Handle any errors gracefully
            android.util.Log.e("SelectionState", "Error in toggleSelection", e)
        }
    }
    
    fun clearSelection() {
        _selectedItems.value = SnapshotStateMap()
        _itemMap.value = SnapshotStateMap()
        _isSelectionMode.value = false
    }
    
    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }
    
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedItems.value = SnapshotStateMap()
        _itemMap.value = SnapshotStateMap()
    }
    
    fun isSelected(item: T): Boolean {
        val itemId = getItemId(item)
        return itemId != null && _selectedItems.value.containsKey(itemId)
    }
    
    fun getSelectionNumber(item: T): Int? {
        val itemId = getItemId(item)
        if (itemId == null) return null
        
        val number = _selectedItems.value[itemId]
        android.util.Log.d("SelectionState", "getSelectionNumber for item ID: $itemId, result: $number")
        android.util.Log.d("SelectionState", "Current selection map: ${_selectedItems.value}")
        
        // If the number is null or invalid, try to recalculate
        if (number == null || number <= 0) {
            android.util.Log.w("SelectionState", "Invalid selection number for item ID: $itemId, attempting to fix")
            val currentMap = _selectedItems.value
            if (currentMap.containsKey(itemId)) {
                // Find the correct number by sorting items by their current numbers
                val sortedItems = currentMap.entries.sortedBy { it.value }
                val itemIndex = sortedItems.indexOfFirst { it.key == itemId }
                if (itemIndex >= 0) {
                    val correctNumber = itemIndex + 1
                    android.util.Log.d("SelectionState", "Recalculated number for item ID: $itemId = $correctNumber")
                    return correctNumber
                }
            }
        }
        
        return number
    }
    
    fun getSelectedItems(): List<T> {
        return _itemMap.value.values.toList()
    }
    
    fun getSelectedCount(): Int {
        return _selectedItems.value.size
    }
    
    fun validateSelectionState(): Boolean {
        val items = _selectedItems.value
        val numbers = items.values.toSet()
        val expectedNumbers = (1..items.size).toSet()
        
        // Check if all numbers are unique and sequential
        val isValid = numbers == expectedNumbers
        
        if (!isValid) {
            android.util.Log.w("SelectionState", "Invalid selection state: items=$items, numbers=$numbers, expected=$expectedNumbers")
            // Auto-fix the selection state
            fixSelectionNumbering()
        }
        
        return isValid
    }
    
    private fun fixSelectionNumbering() {
        try {
            android.util.Log.d("SelectionState", "Fixing selection numbering...")
            val current = _selectedItems.value
            val newMap = SnapshotStateMap<Long, Int>()
            
            // Sort items by their current numbers and renumber sequentially
            val sortedItems = current.entries.sortedBy { it.value }
            sortedItems.forEachIndexed { index, entry ->
                newMap[entry.key] = index + 1
            }
            
            android.util.Log.d("SelectionState", "Fixed selection state: $newMap")
            _selectedItems.value = newMap
        } catch (e: Exception) {
            android.util.Log.e("SelectionState", "Error fixing selection numbering", e)
        }
    }
    
    private fun getItemId(item: T): Long? {
        return when (item) {
            is com.example.photoapp10.feature.photo.data.PhotoEntity -> item.id
            is com.example.photoapp10.feature.album.data.AlbumEntity -> item.id
            else -> {
                android.util.Log.e("SelectionState", "Unknown item type")
                null
            }
        }
    }
}

/**
 * Creates a SelectionState that will be remembered across recompositions
 */
@Composable
fun <T> rememberSelectionState(): SelectionState<T> {
    return remember { SelectionState() }
}