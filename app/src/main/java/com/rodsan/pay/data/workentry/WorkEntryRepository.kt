package com.rodsan.pay.data.workentry

import com.rodsan.pay.data.AppDatabase
import kotlinx.coroutines.flow.Flow

class WorkEntryRepository(private val database: AppDatabase) {
    private val dao = database.workEntryDao()

    fun observePendingByWorker(workerId: Long): Flow<List<WorkEntryEntity>> = dao.observePendingByWorker(workerId)
    fun observePending(): Flow<List<WorkEntryEntity>> = dao.observePending()
    suspend fun insertAll(entries: List<WorkEntryEntity>) = dao.insertAll(entries)
    suspend fun getAll(): List<WorkEntryEntity> = dao.getAll()
    suspend fun countPending(): Int = dao.countPending()

    suspend fun updatePending(entry: WorkEntryEntity, quantity: Int, effectiveUnitPrice: Long, dateMillis: Long) {
        require(quantity > 0) { "La cantidad debe ser mayor que cero." }
        require(effectiveUnitPrice >= 0) { "El precio no puede ser negativo." }
        val paymentOverride = if (effectiveUnitPrice == entry.unitPrice) null else effectiveUnitPrice
        dao.updatePending(
            entryId = entry.id,
            quantity = quantity,
            subtotal = quantity.toLong() * entry.unitPrice,
            dateMillis = dateMillis,
            paymentOverride = paymentOverride
        )
    }

    suspend fun deletePending(entryId: Long) = dao.deletePending(entryId)
}

