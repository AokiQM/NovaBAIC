package com.verlintas.baic2.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.RunRepository
import com.verlintas.baic2.core.model.RunSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TasksViewModel @Inject constructor(
    runRepository: RunRepository,
) : ViewModel() {

    val runs: StateFlow<List<RunSummary>> = runRepository.observeSummaries().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
