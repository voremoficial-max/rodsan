package com.rodsan.pay.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rodsan.pay.data.worker.WorkerRepository
import com.rodsan.pay.data.worktype.WorkTypeRepository
import com.rodsan.pay.data.settlement.SettlementRepository

class CalculatorViewModelFactory(
    private val workerRepository: WorkerRepository,
    private val workTypeRepository: WorkTypeRepository,
    private val settlementRepository: SettlementRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalculatorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalculatorViewModel(workerRepository, workTypeRepository, settlementRepository) as T
        }
        throw IllegalArgumentException("Clase de ViewModel desconocida: ${modelClass.name}")
    }
}
