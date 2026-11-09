package com.fluxie.browser.ui.downloads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluxie.browser.data.DownloadItem
import com.fluxie.browser.data.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(private val repository: DownloadRepository) : ViewModel() {

    val downloads: StateFlow<List<DownloadItem>> = repository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun contentUri(item: DownloadItem): Uri? = repository.contentUri(item)

    /** Cancels the download if it is still running and deletes the file. */
    fun delete(item: DownloadItem) {
        viewModelScope.launch { repository.delete(item) }
    }
}
