package com.rodsan.pay.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rodsan.pay.RodSanApplication
import com.rodsan.pay.data.payment.PaymentEntity
import com.rodsan.pay.data.workentry.WorkEntryEntity
import com.rodsan.pay.util.CurrencyFormatter
import com.rodsan.pay.util.PdfExportUtil
import com.rodsan.pay.util.DateFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as RodSanApplication
    val scope = rememberCoroutineScope()
    val pendingEntries by app.workEntryRepository.observePending().collectAsStateWithLifecycle(initialValue = emptyList())
    val payments by app.paymentRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences = remember { context.getSharedPreferences("rodsanpay_preferences", android.content.Context.MODE_PRIVATE) }
    val companyName = preferences.getString("company_name", "").orEmpty()
    val periodType = preferences.getString("payment_frequency", "WEEKLY").orEmpty()

    var payingWorkerId by remember { mutableStateOf<Long?>(null) }
    var editingPendingEntry by remember { mutableStateOf<WorkEntryEntity?>(null) }
    var deletingPendingEntry by remember { mutableStateOf<WorkEntryEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var isPaying by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pagos") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!showHistory) {
                item { Text("Pendiente por pagar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                val groups = pendingEntries.groupBy { it.workerId to it.workerName }
                    .entries.sortedBy { it.key.second.lowercase(Locale.getDefault()) }
                if (groups.isEmpty()) {
                    item { Text("No hay trabajos pendientes de pago.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(groups, key = { "worker_${it.key.first}" }) { group ->
                        PendingWorkerCard(
                            workerName = group.key.second,
                            entries = group.value,
                            onPay = { payingWorkerId = group.key.first },
                            onEditEntry = { editingPendingEntry = it },
                            onDeleteEntry = { deletingPendingEntry = it }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showHistory = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ver historial de pagos")
                    }
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Historial de pagos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showHistory = false }) { Text("Volver") }
                    }
                }
                if (payments.isEmpty()) {
                    item { Text("Todavía no hay pagos registrados.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(payments, key = { "payment_${it.id}" }) { payment ->
                        PaymentHistoryCard(payment) { selectedPayment = payment }
                    }
                }
            }
        }
    }

    payingWorkerId?.let { workerId ->
        val group = pendingEntries.filter { it.workerId == workerId }
        val workerName = group.firstOrNull()?.workerName.orEmpty()
        AlertDialog(
            onDismissRequest = { payingWorkerId = null },
            title = { Text("Confirmar pago") },
            text = { Text("Pagar ${CurrencyFormatter.format(group.sumOf { it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal })} a $workerName y marcar sus trabajos pendientes como pagados.") },
            confirmButton = {
                Button(onClick = {
                    if (group.isEmpty() || isPaying) { return@Button }
                    isPaying = true
                    error = null
                    scope.launch {
                        runCatching {
                            val now = System.currentTimeMillis()
                            val label = paymentPeriodLabel(now, periodType)
                            val id = app.paymentRepository.payWorker(workerId, workerName, companyName, periodType, label, now)
                            // El pago ya quedó guardado en Room. El PDF se genera desde
                            // el historial para evitar cerrar o cambiar de actividad durante el pago.
                        }.onSuccess { payingWorkerId = null }
                            .onFailure { error = it.message ?: "No se pudo registrar el pago." }
                        isPaying = false
                    }
                }, enabled = !isPaying) { Text(if (isPaying) "Procesando…" else "Pagar") }
            },
            dismissButton = { TextButton(onClick = { payingWorkerId = null }) { Text("Cancelar") } }
        )
    }

    editingPendingEntry?.let { entry ->
        EditPendingEntryDialog(
            entry = entry,
            onDismiss = { editingPendingEntry = null },
            onSave = { quantity, price, dateMillis ->
                scope.launch {
                    runCatching { app.workEntryRepository.updatePending(entry, quantity, price, dateMillis) }
                        .onFailure { error = it.message ?: "No se pudo editar el trabajo." }
                    editingPendingEntry = null
                }
            }
        )
    }

    deletingPendingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingPendingEntry = null },
            title = { Text("Eliminar trabajo") },
            text = { Text("Se eliminará este trabajo pendiente y no aparecerá en el acumulado ni en el próximo pago.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching { app.workEntryRepository.deletePending(entry.id) }
                            .onFailure { error = it.message ?: "No se pudo eliminar el trabajo." }
                        deletingPendingEntry = null
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deletingPendingEntry = null }) { Text("Cancelar") } }
        )
    }

    selectedPayment?.let { PaymentDetailDialog(it, app) { selectedPayment = null } }
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text("No se pudo registrar") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Aceptar") } }) }
}

@Composable
private fun PendingWorkerCard(
    workerName: String,
    entries: List<WorkEntryEntity>,
    onPay: () -> Unit,
    onEditEntry: (WorkEntryEntity) -> Unit,
    onDeleteEntry: (WorkEntryEntity) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(workerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${entries.sumOf { it.quantity }} unidades · ${entries.size} registros")

            entries.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${DateFormatter.date(entry.dateMillis)} · ${entry.code} ${entry.name}")
                        val effectiveUnit = entry.paymentOverride ?: entry.unitPrice
                        Text(
                            "${entry.quantity} × ${CurrencyFormatter.format(effectiveUnit)} = " +
                                CurrencyFormatter.format(entry.quantity.toLong() * effectiveUnit),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { onEditEntry(entry) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar trabajo")
                    }
                    IconButton(onClick = { onDeleteEntry(entry) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar trabajo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Acumulado", style = MaterialTheme.typography.titleMedium)
                Text(
                    CurrencyFormatter.format(entries.sumOf { it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal }),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Pagar y generar PDF")
            }
        }
    }
}

@Composable
private fun PaymentHistoryCard(payment: PaymentEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Payments, null) },
            headlineContent = { Text(payment.workerName, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("${payment.periodLabel} · ${formatDateTime(payment.paidAtMillis)}") },
            trailingContent = { Text(CurrencyFormatter.format(payment.total), fontWeight = FontWeight.Bold) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDetailDialog(payment: PaymentEntity, app: RodSanApplication, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentPayment by app.paymentRepository.observeById(payment.id).collectAsStateWithLifecycle(initialValue = payment)
    val items by app.paymentRepository.observeItems(payment.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var workerDocumentId by remember(payment.workerId) { mutableStateOf<String?>(null) }

    LaunchedEffect(payment.workerId) {
        workerDocumentId = app.workerRepository.getById(payment.workerId)?.documentId
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pago realizado") },
        text = {
            Column {
                Text(payment.companyName.ifBlank { "Muebles RodSan" }, style = MaterialTheme.typography.labelLarge)
                Text(payment.workerName, style = MaterialTheme.typography.titleMedium)
                Text("${payment.periodLabel} · ${formatDateTime(payment.paidAtMillis)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                if (items.isEmpty()) {
                    Text("Este pago no tiene trabajos asociados.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(items, key = { it.id }) { item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text("${DateFormatter.date(item.dateMillis)} · ${item.code} ${item.name}")
                                val effectiveUnit = item.paymentOverride ?: item.unitPrice
                                Text(
                                    "${item.quantity} × ${CurrencyFormatter.format(effectiveUnit)} = " +
                                        CurrencyFormatter.format(item.quantity.toLong() * effectiveUnit),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Total: ${CurrencyFormatter.format(items.sumOf { it.paymentOverride?.let { value -> it.quantity.toLong() * value } ?: it.subtotal })}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        dismissButton = {
            TextButton(onClick = {
                val uri = PdfExportUtil.exportPayment(context, currentPayment ?: payment, items, workerDocumentId)
                PdfExportUtil.sharePdf(context, uri)
            }) {
                Icon(Icons.Filled.PictureAsPdf, null)
                Spacer(Modifier.width(6.dp))
                Text("PDF")
            }
        }
    )
}

@Composable
private fun EditPendingEntryDialog(
    entry: WorkEntryEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Long, Long) -> Unit
) {
    val context = LocalContext.current
    var quantityText by remember(entry.id) { mutableStateOf(entry.quantity.toString()) }
    var priceText by remember(entry.id) { mutableStateOf((entry.paymentOverride ?: entry.unitPrice).toString()) }
    var dateMillis by remember(entry.id) { mutableStateOf(entry.dateMillis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar trabajo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${entry.code} — ${entry.name}")
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("Precio por unidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val initial = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                dateMillis = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.YEAR, year)
                                    set(java.util.Calendar.MONTH, month)
                                    set(java.util.Calendar.DAY_OF_MONTH, day)
                                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            },
                            initial.get(java.util.Calendar.YEAR),
                            initial.get(java.util.Calendar.MONTH),
                            initial.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Fecha: ${DateFormatter.date(dateMillis)}") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantityText.toIntOrNull()
                    val p = priceText.toLongOrNull()
                    if (q != null && q > 0 && p != null && p >= 0) onSave(q, p, dateMillis)
                },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0 && (priceText.toLongOrNull() ?: -1) >= 0
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatDateTime(millis: Long): String = DateFormatter.dateTime(millis)

private fun paymentPeriodLabel(now: Long, periodType: String): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    return when (periodType) {
        "MONTHLY" -> SimpleDateFormat("MMMM yyyy", Locale("es", "CO")).format(Date(now)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
        "BIWEEKLY" -> {
            val month = SimpleDateFormat("MMMM yyyy", Locale("es", "CO")).format(Date(now)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
            "${if (c.get(java.util.Calendar.DAY_OF_MONTH) <= 15) "1ª quincena" else "2ª quincena"} de $month"
        }
        else -> "Semana ${c.get(java.util.Calendar.WEEK_OF_YEAR)} de ${c.get(java.util.Calendar.YEAR)}"
    }
}
