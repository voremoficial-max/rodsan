package com.rodsan.pay.ui.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rodsan.pay.RodSanApplication
import com.rodsan.pay.data.worker.WorkerEntity
import com.rodsan.pay.data.worktype.WorkTypeEntity
import com.rodsan.pay.domain.CalculationItem
import com.rodsan.pay.domain.CalculatorValidator
import com.rodsan.pay.util.CurrencyFormatter
import com.rodsan.pay.ui.settlement.SaveSettlementResult

/**
 * Paso 2 del flujo de Calculadora: añadir trabajos con su cantidad, ver
 * subtotales y el total, y confirmar la liquidación.
 *
 * FASE 5: "Confirmar liquidación" guarda una copia inmutable de los precios,
 * cantidades y subtotales en Room para el historial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorDetailScreen(navController: NavHostController, workerId: Long) {
    val context = LocalContext.current
    val app = context.applicationContext as RodSanApplication
    val viewModel: CalculatorViewModel = viewModel(
        factory = CalculatorViewModelFactory(app.workerRepository, app.workTypeRepository, app.settlementRepository)
    )

    var worker by remember { mutableStateOf<WorkerEntity?>(null) }
    var isLoadingWorker by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val items by viewModel.items.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(workerId) {
        worker = viewModel.getWorker(workerId)
        isLoadingWorker = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(worker?.name ?: "Calculadora") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoadingWorker) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Trabajos realizados",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Añadir trabajo")
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no has añadido trabajos.\nToca \"Añadir trabajo\" para comenzar.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        CalculationItemCard(
                            item = item,
                            onDelete = { viewModel.removeItem(index) }
                        )
                    }
                }
            }

            Divider()

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = CurrencyFormatter.format(total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    enabled = items.isNotEmpty(),
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Text(" Confirmar liquidación")
                }
            }
        }
    }

    if (showAddDialog) {
        AddWorkItemDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }

    if (saveError != null) {
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("No se pudo guardar") },
            text = { Text(saveError ?: "") },
            confirmButton = { TextButton(onClick = { saveError = null }) { Text("Aceptar") } }
        )
    }

    if (showConfirmDialog) {
        ConfirmSettlementDialog(
            workerName = worker?.name ?: "",
            items = items,
            total = total,
            onDismiss = { showConfirmDialog = false },
            onAccept = {
                if (!isSaving) {
                    isSaving = true
                    saveError = null
                    scope.launch {
                        when (val result = viewModel.saveSettlement(
                            workerId,
                            worker?.name ?: "",
                            context.getSharedPreferences("rodsanpay_preferences", android.content.Context.MODE_PRIVATE).getString("company_name", "").orEmpty(),
                            context.getSharedPreferences("rodsanpay_preferences", android.content.Context.MODE_PRIVATE).getString("payment_frequency", "WEEKLY").orEmpty()
                        )) {
                            is SaveSettlementResult.Success -> {
                                isSaving = false
                                showConfirmDialog = false
                                viewModel.clear()
                                navController.popBackStack()
                            }
                            is SaveSettlementResult.Error -> {
                                isSaving = false
                                saveError = result.message
                            }
                        }
                    }
                }
            }
        )
    }
}

/** Wrapper simple para poder usar itemsIndexed sin importar el paquete completo cada vez. */
@Composable
private fun CalculationItemCard(item: CalculationItem, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${item.code} — ${item.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${item.quantity} × ${CurrencyFormatter.format(item.unitPrice)} = " +
                        CurrencyFormatter.format(item.subtotal) +
                        (item.paymentOverride?.let { " · A pagar: ${CurrencyFormatter.format(item.quantity.toLong() * it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorkItemDialog(
    viewModel: CalculatorViewModel,
    onDismiss: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    var selectedWorkType by remember { mutableStateOf<WorkTypeEntity?>(null) }
    var quantity by remember { mutableStateOf("") }
    var paymentText by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val dateContext = LocalContext.current

    fun dateLabel(millis: Long?): String {
        if (millis == null) return "Fecha del trabajo: hoy"
        return java.text.SimpleDateFormat("EEEE dd/MM/yyyy", java.util.Locale("es", "CO"))
            .format(java.util.Date(millis)).replaceFirstChar { it.titlecase(java.util.Locale("es", "CO")) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedWorkType == null) "Buscar trabajo" else "Añadir trabajo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedWorkType == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Código o nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (searchResults.isEmpty()) {
                        Text(
                            "Sin resultados. Verifica el código/nombre o crea el trabajo en Trabajos.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(searchResults, key = { it.id }) { workType ->
                                ListItem(
                                    headlineContent = { Text("${workType.code} — ${workType.name}") },
                                    supportingContent = { Text(CurrencyFormatter.format(workType.unitPrice)) },
                                    modifier = Modifier.clickable {
                                        selectedWorkType = workType
                                        errorMessage = null
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val workType = selectedWorkType!!
                    Text("${workType.code} — ${workType.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Precio establecido: ${CurrencyFormatter.format(workType.unitPrice)} por unidad",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit) },
                        label = { Text("Cantidad") },
                        singleLine = true,
                        isError = errorMessage != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paymentText,
                        onValueChange = { paymentText = it.filter(Char::isDigit) },
                        label = { Text("Pago opcional (reemplaza solo el valor a pagar)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "El pago opcional no cambia el precio establecido del trabajo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val now = java.util.Calendar.getInstance()
                            val initial = selectedDateMillis?.let {
                                java.util.Calendar.getInstance().apply { timeInMillis = it }
                            } ?: now
                            android.app.DatePickerDialog(
                                dateContext,
                                { _, year, month, day ->
                                    selectedDateMillis = java.util.Calendar.getInstance().apply {
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
                    ) { Text(dateLabel(selectedDateMillis)) }
                    if (quantity.isNotBlank()) {
                        val q = quantity.toIntOrNull() ?: 0
                        val base = workType.unitPrice * q
                        val override = paymentText.toLongOrNull()
                        Text(
                            "Valor establecido: ${CurrencyFormatter.format(base)}" +
                                if (override != null) " · A pagar: ${CurrencyFormatter.format(q.toLong() * override)}" else "",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (selectedWorkType != null) {
                TextButton(onClick = {
                    val error = CalculatorValidator.validateQuantity(quantity)
                    val payment = paymentText.toLongOrNull()
                    if (error != null) {
                        errorMessage = error
                    } else if (paymentText.isNotBlank() && payment == null) {
                        errorMessage = "El pago opcional no es válido."
                    } else {
                        viewModel.addItem(
                            selectedWorkType!!,
                            quantity.trim().toInt(),
                            paymentOverride = payment,
                            dateMillis = selectedDateMillis
                        )
                        viewModel.onSearchQueryChange("")
                        onDismiss()
                    }
                }) { Text("Agregar") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedWorkType != null) {
                    selectedWorkType = null
                    quantity = ""
                    paymentText = ""
                    selectedDateMillis = null
                    errorMessage = null
                } else {
                    viewModel.onSearchQueryChange("")
                    onDismiss()
                }
            }) { Text(if (selectedWorkType != null) "Atrás" else "Cancelar") }
        }
    )
}

@Composable
private fun ConfirmSettlementDialog(
    workerName: String,
    items: List<CalculationItem>,
    total: Long,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liquidación calculada") },
        text = {
            Column {
                Text(text = workerName, style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                    items.forEach { item ->
                        Text(
                            text = "${item.code} · ${item.quantity} × ${CurrencyFormatter.format(item.unitPrice)} = " +
                                CurrencyFormatter.format(item.subtotal) +
                                (item.paymentOverride?.let { " · A pagar: ${CurrencyFormatter.format(item.quantity.toLong() * it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    text = "Total: ${CurrencyFormatter.format(total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Al confirmar, esta liquidación se guardará en el historial con los precios usados hoy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Aceptar") }
        }
    )
}
