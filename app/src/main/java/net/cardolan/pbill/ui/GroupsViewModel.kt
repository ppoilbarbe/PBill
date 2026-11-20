package net.cardolan.pbill.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.cardolan.pbill.data.GroupEntity
import net.cardolan.pbill.data.Repository

class GroupsViewModel(private val repository: Repository) : ViewModel() {

    val groups: StateFlow<List<GroupEntity>?> = repository.groups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createGroup(name: String, currency: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createGroup(name, currency)) }
    }
}
