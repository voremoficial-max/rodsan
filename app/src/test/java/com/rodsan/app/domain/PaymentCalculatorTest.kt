package com.rodsan.pay.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentCalculatorTest {

    @Test
    fun subtotal_multiplicaCantidadPorPrecio() {
        val subtotal = PaymentCalculator.subtotal(quantity = 35, unitPrice = 4_000L)
        assertEquals(140_000L, subtotal)
    }

    @Test
    fun subtotal_conCantidadCero_esCero() {
        val subtotal = PaymentCalculator.subtotal(quantity = 0, unitPrice = 4_000L)
        assertEquals(0L, subtotal)
    }

    @Test
    fun total_sumaTodosLosSubtotales() {
        // Ejemplo del enunciado del proyecto:
        // C01: 35 x 4.000 = 140.000
        // C02: 20 x 2.500 = 50.000
        // Total: 190.000
        val items = listOf(
            CalculationItem(workTypeId = 1L, code = "C01", name = "Cocido de telas", unitPrice = 4_000L, quantity = 35),
            CalculationItem(workTypeId = 2L, code = "C02", name = "Dobladillo", unitPrice = 2_500L, quantity = 20)
        )
        assertEquals(190_000L, PaymentCalculator.total(items))
    }

    @Test
    fun pagoOpcional_seMultiplicaPorLaCantidad() {
        val item = CalculationItem(
            workTypeId = 1L, code = "C01", name = "Cama",
            unitPrice = 10_000L, quantity = 10, paymentOverride = 9_000L
        )
        assertEquals(90_000L, item.effectivePayment)
        assertEquals(90_000L, PaymentCalculator.total(listOf(item)))
    }

    @Test
    fun total_conListaVacia_esCero() {
        assertEquals(0L, PaymentCalculator.total(emptyList()))
    }

    @Test
    fun calculationItem_calculaSuPropioSubtotal() {
        val item = CalculationItem(workTypeId = 1L, code = "C03", name = "Corte", unitPrice = 3_000L, quantity = 10)
        assertEquals(30_000L, item.subtotal)
    }
}
