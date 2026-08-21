package com.rodsan.pay.data.payment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Insert
    suspend fun insertItems(items: List<PaymentItemEntity>)

    @Insert
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Query("SELECT * FROM payments ORDER BY paidAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE workerId = :workerId ORDER BY paidAtMillis DESC, id DESC")
    fun observeByWorker(workerId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY id ASC")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payment_items ORDER BY id ASC")
    suspend fun getAllItems(): List<PaymentItemEntity>

    @Query("SELECT * FROM payment_items WHERE paymentId = :paymentId ORDER BY dateMillis ASC, id ASC")
    fun observeItems(paymentId: Long): Flow<List<PaymentItemEntity>>

    @Query("SELECT * FROM payment_items WHERE paymentId = :paymentId ORDER BY dateMillis ASC, id ASC")
    suspend fun getItems(paymentId: Long): List<PaymentItemEntity>

    @Query("SELECT * FROM payments WHERE id = :paymentId LIMIT 1")
    suspend fun getById(paymentId: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE id = :paymentId LIMIT 1")
    fun observeById(paymentId: Long): Flow<PaymentEntity?>

    @Query("DELETE FROM payment_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("DELETE FROM payment_items WHERE paymentId = :paymentId")
    suspend fun deleteItemsByPayment(paymentId: Long)

    @Query("UPDATE payments SET total = :total WHERE id = :paymentId")
    suspend fun updateTotal(paymentId: Long, total: Long)

    @Query("UPDATE payment_items SET quantity = :quantity, unitPrice = :unitPrice, subtotal = :subtotal, dateMillis = :dateMillis, paymentOverride = :paymentOverride WHERE id = :itemId")
    suspend fun updateItem(itemId: Long, quantity: Int, unitPrice: Long, subtotal: Long, dateMillis: Long, paymentOverride: Long?)

    @Query("DELETE FROM payment_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM payments WHERE id = :paymentId")
    suspend fun deleteById(paymentId: Long)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
