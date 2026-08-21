package com.rodsan.pay.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rodsan.pay.data.worker.WorkerDao
import com.rodsan.pay.data.worker.WorkerEntity
import com.rodsan.pay.data.worktype.WorkTypeDao
import com.rodsan.pay.data.worktype.WorkTypeEntity
import com.rodsan.pay.data.settlement.SettlementDao
import com.rodsan.pay.data.settlement.SettlementEntity
import com.rodsan.pay.data.settlement.SettlementItemEntity
import com.rodsan.pay.data.workentry.WorkEntryDao
import com.rodsan.pay.data.workentry.WorkEntryEntity
import com.rodsan.pay.data.payment.PaymentDao
import com.rodsan.pay.data.payment.PaymentEntity
import com.rodsan.pay.data.payment.PaymentItemEntity

/**
 * Base de datos local de Muebles RodSan.
 *
 * En fases posteriores se agregarán aquí las entidades Settlement y
 * SettlementItem (Fases 4 y 5), junto con sus DAOs y las migraciones
 * correspondientes usando Room.Migration para no perder datos existentes.
 */
@Database(
    entities = [WorkerEntity::class, WorkTypeEntity::class, SettlementEntity::class, SettlementItemEntity::class, WorkEntryEntity::class, PaymentEntity::class, PaymentItemEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workerDao(): WorkerDao

    abstract fun workTypeDao(): WorkTypeDao

    abstract fun settlementDao(): SettlementDao

    abstract fun workEntryDao(): WorkEntryDao

    abstract fun paymentDao(): PaymentDao

    companion object {
        private const val DATABASE_NAME = "rodsanpay_database"

        /**
         * FASE 3: agrega la tabla `work_types` con su índice único por código.
         * No modifica ni borra la tabla `workers` existente, así que los
         * trabajadores creados en la Fase 2 se conservan intactos.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_types` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unitPrice` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_types_code` ON `work_types` (`code`)"
                )
            }
        }


        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `settlements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workerId` INTEGER NOT NULL,
                        `workerName` TEXT NOT NULL,
                        `dateMillis` INTEGER NOT NULL,
                        `periodLabel` TEXT NOT NULL,
                        `total` INTEGER NOT NULL,
                        FOREIGN KEY(`workerId`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_workerId` ON `settlements` (`workerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_dateMillis` ON `settlements` (`dateMillis`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `settlement_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `settlementId` INTEGER NOT NULL,
                        `workTypeId` INTEGER NOT NULL,
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPrice` INTEGER NOT NULL,
                        `subtotal` INTEGER NOT NULL,
                        FOREIGN KEY(`settlementId`) REFERENCES `settlements`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_items_settlementId` ON `settlement_items` (`settlementId`)")
            }
        }



        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settlements ADD COLUMN companyName TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workerId` INTEGER NOT NULL,
                        `workerName` TEXT NOT NULL,
                        `companyName` TEXT NOT NULL,
                        `paidAtMillis` INTEGER NOT NULL,
                        `periodType` TEXT NOT NULL,
                        `periodLabel` TEXT NOT NULL,
                        `total` INTEGER NOT NULL,
                        FOREIGN KEY(`workerId`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_workerId` ON `payments` (`workerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_paidAtMillis` ON `payments` (`paidAtMillis`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `work_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workerId` INTEGER NOT NULL,
                        `workerName` TEXT NOT NULL,
                        `dateMillis` INTEGER NOT NULL,
                        `workTypeId` INTEGER NOT NULL,
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPrice` INTEGER NOT NULL,
                        `subtotal` INTEGER NOT NULL,
                        `paymentId` INTEGER,
                        FOREIGN KEY(`workerId`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`paymentId`) REFERENCES `payments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_workerId` ON `work_entries` (`workerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_dateMillis` ON `work_entries` (`dateMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_paymentId` ON `work_entries` (`paymentId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payment_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `paymentId` INTEGER NOT NULL,
                        `workEntryId` INTEGER NOT NULL,
                        `dateMillis` INTEGER NOT NULL,
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPrice` INTEGER NOT NULL,
                        `subtotal` INTEGER NOT NULL,
                        FOREIGN KEY(`paymentId`) REFERENCES `payments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_items_paymentId` ON `payment_items` (`paymentId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_entries ADD COLUMN paymentOverride INTEGER")
                db.execSQL("ALTER TABLE payment_items ADD COLUMN paymentOverride INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settlement_items ADD COLUMN paymentOverride INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settlement_items ADD COLUMN dateMillis INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE payments
                    SET total = (
                        SELECT COALESCE(SUM(
                            CASE
                                WHEN paymentOverride IS NOT NULL THEN quantity * paymentOverride
                                ELSE subtotal
                            END
                        ), 0)
                        FROM payment_items
                        WHERE paymentId = payments.id
                    )
                """.trimIndent())
                db.execSQL("""
                    UPDATE settlements
                    SET total = (
                        SELECT COALESCE(SUM(
                            CASE
                                WHEN paymentOverride IS NOT NULL THEN quantity * paymentOverride
                                ELSE subtotal
                            END
                        ), 0)
                        FROM settlement_items
                        WHERE settlementId = settlements.id
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build().also { instance = it }
            }
        }
    }
}
