package com.scrctl.client.ui.file

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            selectedStorage = StorageTab.Remote,
            breadcrumb = "Root / Internal Storage / Downloads",
            searchVisible = false,
            searchQuery = "",
            entries = emptyList(),
            visibleEntries = emptyList(),
            selectedCount = 0,
        )
    )
    
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()
    
    val entries = mutableStateListOf(
        FileEntry(
            id = "folder_system",
            kind = EntryKind.Folder,
            name = "System_Config",
            meta = "May 12, 2024 • 8 items",
        ),
        FileEntry(
            id = "folder_dcim",
            kind = EntryKind.ImageFolder,
            name = "DCIM_Backup",
            meta = "Today, 10:45 AM • 142 items",
            checked = true,
        ),
        FileEntry(
            id = "file_img",
            kind = EntryKind.Image,
            name = "vacation_photo_01.jpg",
            meta = "2.4 MB • JPG",
            checked = true,
        ),
        FileEntry(
            id = "file_video",
            kind = EntryKind.Video,
            name = "production_demo.mp4",
            meta = "45.8 MB • MP4",
        ),
        FileEntry(
            id = "file_doc",
            kind = EntryKind.Doc,
            name = "security_log_v2.txt",
            meta = "14 KB • TEXT",
            checked = true,
        ),
        FileEntry(
            id = "folder_shared",
            kind = EntryKind.SharedFolder,
            name = "Shared_Project",
            meta = "Yesterday • 12 files",
        ),
    )
    
    init {
        updateUiState()
    }
    
    fun selectStorage(storage: StorageTab) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedStorage = storage)
            updateUiState()
        }
    }
    
    fun toggleSearchVisible() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val newSearchVisible = !currentState.searchVisible
            _uiState.value = currentState.copy(
                searchVisible = newSearchVisible,
                searchQuery = if (!newSearchVisible) "" else currentState.searchQuery
            )
            updateUiState()
        }
    }
    
    fun updateSearchQuery(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query)
            updateUiState()
        }
    }
    
    fun toggleEntrySelection(entryId: String) {
        val index = entries.indexOfFirst { it.id == entryId }
        if (index >= 0) {
            entries[index] = entries[index].copy(checked = !entries[index].checked)
            updateUiState()
        }
    }
    
    fun openEntry(entry: FileEntry) {
        if (entry.kind.isFolder) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    breadcrumb = "Root / Internal Storage / ${entry.name}"
                )
                updateUiState()
            }
        }
    }
    
    fun uploadFile() {
        val newName = "uploaded_${System.currentTimeMillis()}.txt"
        entries.add(
            0,
            FileEntry(
                id = "file_$newName",
                kind = EntryKind.Doc,
                name = newName,
                meta = "1 KB • TEXT",
                checked = false,
            )
        )
        updateUiState()
    }
    
    fun downloadFiles() {
        entries.replaceAll { if (it.checked) it.copy(checked = false) else it }
        updateUiState()
    }
    
    fun moveFiles() {
        entries.replaceAll {
            if (it.checked) {
                it.copy(name = "moved_${it.name}", checked = false)
            } else {
                it
            }
        }
        updateUiState()
    }
    
    fun deleteFiles() {
        entries.removeAll { it.checked }
        updateUiState()
    }
    
    private fun updateUiState() {
        val currentState = _uiState.value
        val entriesList = entries.toList()
        
        val visibleEntries = if (currentState.searchQuery.trim().isEmpty()) {
            entriesList
        } else {
            entriesList.filter { entry ->
                entry.name.contains(currentState.searchQuery, ignoreCase = true) ||
                entry.meta.contains(currentState.searchQuery, ignoreCase = true)
            }
        }
        
        val selectedCount = entriesList.count { it.checked }
        
        _uiState.value = currentState.copy(
            entries = entriesList,
            visibleEntries = visibleEntries,
            selectedCount = selectedCount
        )
    }
}