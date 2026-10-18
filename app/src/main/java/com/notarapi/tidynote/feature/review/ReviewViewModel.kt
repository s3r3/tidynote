package com.notarapi.tidynote.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notarapi.tidynote.data.receipt.ReceiptRepository
import com.notarapi.tidynote.core.model.ReceiptDraft
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

class ReviewViewModel(
    private val repository: ReceiptRepository
) : ViewModel() {

    fun saveDraft(draft: ReceiptDraft) {
        viewModelScope.launch {
            repository.saveDraft(draft)
        }
    }
}