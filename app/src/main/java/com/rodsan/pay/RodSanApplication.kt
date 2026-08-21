package com.rodsan.pay

import android.app.Application
import com.rodsan.pay.data.AppDatabase
import com.rodsan.pay.data.worker.WorkerRepository
import com.rodsan.pay.data.worktype.WorkTypeRepository
import com.rodsan.pay.data.settlement.SettlementRepository
import com.rodsan.pay.data.workentry.WorkEntryRepository
import com.rodsan.pay.data.payment.PaymentRepository

/**
 * Clase Application de Muebles RodSan.
 *
 * Actúa como un contenedor manual de dependencias (sin librerías externas
 * como Hilt, para mantener el proyecto simple en estas primeras fases):
 * crea la base de datos y los repositorios una sola vez y los expone para
 * que las pantallas puedan construir sus ViewModels con [ViewModelFactory].
 */
class RodSanApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val workerRepository: WorkerRepository by lazy {
        WorkerRepository(database.workerDao())
    }

    val workTypeRepository: WorkTypeRepository by lazy {
        WorkTypeRepository(database.workTypeDao())
    }

    val settlementRepository: SettlementRepository by lazy {
        SettlementRepository(database)
    }

    val workEntryRepository: WorkEntryRepository by lazy {
        WorkEntryRepository(database)
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(database)
    }
}
