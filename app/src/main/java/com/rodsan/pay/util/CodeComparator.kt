package com.rodsan.pay.util

private val leadingNumber = Regex("^\\d+")

fun compareWorkCodes(a: String, b: String): Int {
    val aStartsNumber = a.firstOrNull()?.isDigit() == true
    val bStartsNumber = b.firstOrNull()?.isDigit() == true
    if (aStartsNumber && bStartsNumber) {
        val an = leadingNumber.find(a)?.value?.toLongOrNull()
        val bn = leadingNumber.find(b)?.value?.toLongOrNull()
        if (an != null && bn != null && an != bn) return an.compareTo(bn)
    }
    return a.compareTo(b, ignoreCase = true)
}
