package com.rodsan.pay.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodsan.pay.data.worker.WorkerEntity
import com.rodsan.pay.data.worker.WorkerRepository
import com.rodsan.pay.data.worktype.WorkTypeEntity
import com.rodsan.pay.data.worktype.WorkTypeRepository
import com.rodsan.pay.data.workentry.WorkEntryEntity
import com.rodsan.pay.domain.CalculationItem
import com.rodsan.pay.domain.PaymentCalculator
import com.rodsan.pay.util.compareWorkCodes
import com.rodsan.pay.ui.settlement.SaveSettlementResult
import com.rodsan.pay.data.settlement.SettlementRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Estado y lógica de la pantalla de Calculadora (Fase 4).
 *
 * Los trabajos que el usuario va añadiendo se guardan en memoria como
 * [CalculationItem]. Todavía NO se persisten en la base de datos: la
 * persistencia como liquidación (Settlement) llega en la Fase 5. Al
 * "Confirmar liquidación" en esta fase solo se muestra el resumen final
 * calculado, sin guardarlo.
 */
class CalculatorViewModel(
    private val workerRepository: WorkerRepository,
    private val workTypeRepository: WorkTypeRepository,
    private val settlementRepository: SettlementRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<CalculationItem>>(emptyList())
    val items: StateFlow<List<CalculationItem>> = _items.asStateFlow()

    val total: StateFlow<Long> = items
        .map { PaymentCalculator.total(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<WorkTypeEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) workTypeRepository.observeActive() else workTypeRepository.search(query)
        }
        .map { list -> list.sortedWith { a, b -> compareWorkCodes(a.code, b.code) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    suspend fun getWorker(workerId: Long): WorkerEntity? = workerRepository.getById(workerId)

    /** Añade un trabajo con su cantidad, guardando el precio unitario vigente. */
    fun addItem(workType: WorkTypeEntity, quantity: Int, paymentOverride: Long? = null, dateMillis: Long? = null) {
        val newItem = CalculationItem(
            workTypeId = workType.id,
            code = workType.code,
            name = workType.name,
            unitPrice = workType.unitPrice,
            quantity = quantity,
            paymentOverride = paymentOverride,
            dateMillis = dateMillis
        )
        _items.value = (_items.value + newItem).sortedWith { a, b -> compareWorkCodes(a.code, b.code) }
    }

    fun removeItem(index: Int) {
        _items.value = _items.value.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
    }

    suspend fun saveSettlement(workerId: Long, workerName: String, companyName: String, periodType: String): SaveSettlementResult {
        if (_items.value.isEmpty()) return SaveSettlementResult.Error("Añade al menos un trabajo.")
        val now = System.currentTimeMillis()
        val settlement = com.rodsan.pay.data.settlement.SettlementEntity(
            workerId = workerId, workerName = workerName, companyName = companyName, dateMillis = now,
            periodLabel = periodLabel(now, periodType),
            total = _items.value.sumOf { it.effectivePayment }
        )
        val details = _items.value.map {
            com.rodsan.pay.data.settlement.SettlementItemEntity(
                settlementId = 0, workTypeId = it.workTypeId, code = it.code, name = it.name,
                quantity = it.quantity, unitPrice = it.unitPrice, subtotal = it.subtotal, paymentOverride = it.paymentOverride,
                dateMillis = it.dateMillis ?: now
            )
        }
        val entries = _items.value.map {
            WorkEntryEntity(
                workerId = workerId, workerName = workerName, dateMillis = it.dateMillis ?: now,
                workTypeId = it.workTypeId, code = it.code, name = it.name,
                quantity = it.quantity, unitPrice = it.unitPrice, subtotal = it.subtotal,
                paymentOverride = it.paymentOverride
            )
        }
        return try {
            val id = settlementRepository.createWithWorkEntries(settlement, details, entries)
            SaveSettlementResult.Success(id)
        } catch (e: Exception) {
            SaveSettlementResult.Error("No se pudo guardar la liquidación: ${e.message ?: "error desconocido"}.")
        }
    }

    private fun periodLabel(now: Long, periodType: String): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return when (periodType) {
            "MONTHLY" -> java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("es", "CO")).format(java.util.Date(now))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("es", "CO")) else it.toString() }
            "BIWEEKLY" -> {
                val month = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("es", "CO")).format(java.util.Date(now))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("es", "CO")) else it.toString() }
                val half = if (calendar.get(java.util.Calendar.DAY_OF_MONTH) <= 15) "1ª quincena" else "2ª quincena"
                "$half de $month"
            }
            else -> {
                val week = calendar.get(java.util.Calendar.WEEK_OF_YEAR)
                val year = calendar.get(java.util.Calendar.YEAR)
                "Semana $week de $year"
            }
        }
    }

    /** Limpia la calculadora, por ejemplo tras confirmar la liquidación. */
    fun clear() {
        _items.value = emptyList()
        _searchQuery.value = ""
    }
}
