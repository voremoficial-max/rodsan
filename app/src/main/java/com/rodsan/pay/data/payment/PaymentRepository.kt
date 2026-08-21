package com.rodsan.pay.data.payment

import androidx.room.withTransaction
import com.rodsan.pay.data.AppDatabase
import com.rodsan.pay.data.workentry.WorkEntryDao
import kotlinx.coroutines.flow.Flow

class PaymentRepository(private val database: AppDatabase) {
    private val paymentDao = database.paymentDao()
    private val workEntryDao = database.workEntryDao()

    fun observeAll(): Flow<List<PaymentEntity>> = paymentDao.observeAll()
    fun observeByWorker(workerId: Long): Flow<List<PaymentEntity>> = paymentDao.observeByWorker(workerId)
    fun observeItems(paymentId: Long): Flow<List<PaymentItemEntity>> = paymentDao.observeItems(paymentId)
    fun observeById(paymentId: Long): Flow<PaymentEntity?> = paymentDao.observeById(paymentId)
    suspend fun getItems(paymentId: Long): List<PaymentItemEntity> = paymentDao.getItems(paymentId)
    suspend fun getAll(): List<PaymentEntity> = paymentDao.getAll()
    suspend fun getAllItems(): List<PaymentItemEntity> = paymentDao.getAllItems()

    suspend fun deletePayment(paymentId: Long) = database.withTransaction {
        val items = paymentDao.getItems(paymentId)
        if (items.isNotEmpty()) workEntryDao.unmarkPaid(items.map { it.workEntryId })
        paymentDao.deleteItemsByPayment(paymentId)
        paymentDao.deleteById(paymentId)
    }

    suspend fun removeItemFromPayment(paymentId: Long, item: PaymentItemEntity) = database.withTransaction {
        workEntryDao.unmarkPaid(listOf(item.workEntryId))
        paymentDao.deleteItem(item.id)
        val newTotal = paymentDao.getItems(paymentId).sumOf { it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal }
        paymentDao.updateTotal(paymentId, newTotal)
    }

    suspend fun updatePaymentItem(
        paymentId: Long,
        itemId: Long,
        quantity: Int,
        effectiveUnitPrice: Long,
        dateMillis: Long
    ) = database.withTransaction {
        require(quantity > 0) { "La cantidad debe ser mayor que cero." }
        require(effectiveUnitPrice >= 0) { "El precio no puede ser negativo." }
        val item = paymentDao.getItems(paymentId).firstOrNull { it.id == itemId }
            ?: error("El trabajo del pago ya no existe.")
        val subtotal = quantity.toLong() * item.unitPrice
        val paymentOverride = if (effectiveUnitPrice == item.unitPrice) null else effectiveUnitPrice
        paymentDao.updateItem(
            itemId = itemId,
            quantity = quantity,
            unitPrice = item.unitPrice,
            subtotal = subtotal,
            dateMillis = dateMillis,
            paymentOverride = paymentOverride
        )
        val newTotal = paymentDao.getItems(paymentId).sumOf {
            it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal
        }
        paymentDao.updateTotal(paymentId, newTotal)
    }

    suspend fun addWorkToPayment(paymentId: Long, entryId: Long) = database.withTransaction {
        val entry = workEntryDao.getById(entryId)
            ?: error("El trabajo seleccionado ya no existe.")
        require(entry.paymentId == null) { "El trabajo ya está asociado a un pago." }
        val payment = paymentDao.getById(paymentId)
            ?: error("El pago ya no existe.")
        require(entry.workerId == payment.workerId) { "El trabajo pertenece a otro trabajador." }
        paymentDao.insertItems(listOf(
            PaymentItemEntity(
                paymentId = paymentId,
                workEntryId = entry.id,
                dateMillis = entry.dateMillis,
                code = entry.code,
                name = entry.name,
                quantity = entry.quantity,
                unitPrice = entry.unitPrice,
                subtotal = entry.subtotal,
                paymentOverride = entry.paymentOverride
            )
        ))
        workEntryDao.markPaid(listOf(entry.id), paymentId)
        val newTotal = paymentDao.getItems(paymentId).sumOf { it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal }
        paymentDao.updateTotal(paymentId, newTotal)
    }

    suspend fun payWorker(
        workerId: Long,
        workerName: String,
        companyName: String,
        periodType: String,
        periodLabel: String,
        paidAtMillis: Long
    ): Long = database.withTransaction {
        val entries = workEntryDao.getPendingByWorker(workerId)
        require(entries.isNotEmpty()) { "Este trabajador no tiene trabajos pendientes." }
        val total = entries.sumOf { it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal }
        val paymentId = paymentDao.insert(
            PaymentEntity(
                workerId = workerId,
                workerName = workerName,
                companyName = companyName,
                paidAtMillis = paidAtMillis,
                periodType = periodType,
                periodLabel = periodLabel,
                total = total
            )
        )
        paymentDao.insertItems(entries.map {
            PaymentItemEntity(
                paymentId = paymentId,
                workEntryId = it.id,
                dateMillis = it.dateMillis,
                code = it.code,
                name = it.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                subtotal = it.subtotal,
                paymentOverride = it.paymentOverride
            )
        })
        workEntryDao.markPaid(entries.map { it.id }, paymentId)
        paymentId
    }
}
