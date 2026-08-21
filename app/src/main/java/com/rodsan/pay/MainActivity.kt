package com.rodsan.pay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rodsan.pay.navigation.RodSanDestinations
import com.rodsan.pay.reminder.NotificationHelper
import com.rodsan.pay.reminder.ReminderScheduler
import com.rodsan.pay.ui.calculator.CalculatorDetailScreen
import com.rodsan.pay.ui.calculator.CalculatorWorkerSelectScreen
import com.rodsan.pay.ui.settlement.SettlementHistoryScreen
import com.rodsan.pay.ui.payment.PaymentsScreen
import com.rodsan.pay.ui.theme.RodSanTheme
import com.rodsan.pay.ui.worker.WorkerFormScreen
import com.rodsan.pay.ui.worker.WorkerHistoryScreen
import com.rodsan.pay.ui.worker.WorkerListScreen
import com.rodsan.pay.ui.worktype.WorkTypeFormScreen
import com.rodsan.pay.ui.worktype.WorkTypeListScreen
import com.rodsan.pay.util.DataBackupUtil
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ReminderScheduler.scheduleNext(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createChannel(this)
        setupMonthEndReminder()
        setContent { RodSanTheme { RodSanApp() } }
    }

    private fun setupMonthEndReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) ReminderScheduler.scheduleNext(this)
            else requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else ReminderScheduler.scheduleNext(this)
    }
}

private data class HomeMenuItem(val label: String, val icon: ImageVector, val destination: String)

private val homeMenuItems = listOf(
    HomeMenuItem("Calculadora", Icons.Filled.Calculate, RodSanDestinations.CALCULATOR_WORKER_SELECT),
    HomeMenuItem("Personal", Icons.Filled.Groups, RodSanDestinations.WORKER_LIST),
    HomeMenuItem("Trabajos", Icons.Filled.Work, RodSanDestinations.WORK_TYPE_LIST),
    HomeMenuItem("Pagos", Icons.Filled.Payments, RodSanDestinations.PAYMENTS),
    HomeMenuItem("Liquidaciones", Icons.Filled.ReceiptLong, RodSanDestinations.SETTLEMENT_HISTORY),
    HomeMenuItem("Ajustes", Icons.Filled.Settings, RodSanDestinations.SETTINGS)
)

@Composable
fun RodSanApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("rodsanpay_preferences", android.content.Context.MODE_PRIVATE)
    }
    var userName by remember { mutableStateOf(preferences.getString("user_name", null).orEmpty()) }
    var showNameDialog by remember { mutableStateOf(userName.isBlank()) }

    if (showNameDialog) {
        NameDialog { name ->
            val cleanName = name.trim()
            preferences.edit().putString("user_name", cleanName).apply()
            userName = cleanName
            showNameDialog = false
        }
    }

    NavHost(navController = navController, startDestination = RodSanDestinations.PANEL, modifier = Modifier.fillMaxSize()) {
        composable(RodSanDestinations.PANEL) { RodSanHome(navController, userName) }
        composable(RodSanDestinations.WORKER_LIST) { WorkerListScreen(navController) }
        composable(RodSanDestinations.WORKER_FORM) { WorkerFormScreen(navController, null) }
        composable(RodSanDestinations.WORKER_FORM_WITH_ID) { entry ->
            WorkerFormScreen(navController, entry.arguments?.getString("workerId")?.toLongOrNull())
        }
        composable(RodSanDestinations.WORKER_HISTORY) { entry ->
            WorkerHistoryScreen(navController, entry.arguments?.getString("workerId")?.toLongOrNull() ?: 0L)
        }
        composable(RodSanDestinations.WORK_TYPE_LIST) { WorkTypeListScreen(navController) }
        composable(RodSanDestinations.WORK_TYPE_FORM) { WorkTypeFormScreen(navController, null) }
        composable(RodSanDestinations.WORK_TYPE_FORM_WITH_ID) { entry ->
            WorkTypeFormScreen(navController, entry.arguments?.getString("workTypeId")?.toLongOrNull())
        }
        composable(RodSanDestinations.CALCULATOR_WORKER_SELECT) { CalculatorWorkerSelectScreen(navController) }
        composable(RodSanDestinations.CALCULATOR_DETAIL) { entry ->
            CalculatorDetailScreen(navController, entry.arguments?.getString("workerId")?.toLongOrNull() ?: 0L)
        }
        composable(RodSanDestinations.PAYMENTS) { PaymentsScreen(navController) }
        composable(RodSanDestinations.SETTLEMENT_HISTORY) { SettlementHistoryScreen(navController) }
        composable(RodSanDestinations.SETTINGS) {
            SettingsScreen(navController, userName) { newName ->
                preferences.edit().putString("user_name", newName.trim()).apply()
                userName = newName.trim()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RodSanHome(navController: NavHostController, userName: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Muebles RodSan", fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (userName.isBlank()) "Panel principal" else "Bienvenido, $userName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "¿Qué deseas hacer?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(homeMenuItems, key = { it.destination }) { item ->
                Card(
                    onClick = {
                        navController.navigate(item.destination) { launchSingleTop = true }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                when (item.label) {
                                    "Calculadora" -> "Registrar trabajos y calcular pagos"
                                    "Personal" -> "Administrar trabajadores"
                                    "Trabajos" -> "Crear y ordenar trabajos por código"
                                    "Pagos" -> "Registrar y consultar pagos"
                                    "Liquidaciones" -> "Consultar liquidaciones guardadas"
                                    else -> "Configurar la aplicación"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    navController: NavHostController,
    userName: String,
    onUserNameChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("rodsanpay_preferences", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var editedName by remember(userName) { mutableStateOf(userName) }
    var editedCompany by remember { mutableStateOf(preferences.getString("company_name", "").orEmpty()) }
    var paymentFrequency by remember { mutableStateOf(preferences.getString("payment_frequency", "WEEKLY").orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = DataBackupUtil.exportJson(context, userName)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.writer(Charsets.UTF_8).use { it.write(json) }
                    } ?: error("No se pudo abrir el archivo de destino.")
                }.onSuccess {
                    status = "Datos exportados correctamente."
                }.onFailure {
                    status = "No se pudo exportar: ${it.message}"
                }
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.reader(Charsets.UTF_8).readText()
                    } ?: error("No se pudo leer el archivo seleccionado.")
                }.onSuccess { pendingImport = it }
                    .onFailure { status = "No se pudo leer el respaldo: ${it.message}" }
            }
        }
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importar datos") },
            text = {
                Text("La importación reemplazará los datos actuales de Muebles RodSan por los del respaldo. ¿Quieres continuar?")
            },
            confirmButton = {
                Button(onClick = {
                    val json = pendingImport ?: return@Button
                    pendingImport = null
                    scope.launch {
                        runCatching { DataBackupUtil.importJson(context, json) }
                            .onSuccess { importedName ->
                                if (importedName.isNotBlank()) {
                                    editedName = importedName
                                    onUserNameChanged(importedName)
                                }
                                status = "Datos importados correctamente."
                            }
                            .onFailure { status = "No se pudo importar: ${it.message}" }
                    }
                }) { Text("Importar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancelar") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Personaliza Muebles RodSan y protege tus datos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Usuario administrador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
                )
                Button(
                    onClick = {
                        val clean = editedName.trim()
                        if (clean.isNotEmpty()) {
                            onUserNameChanged(clean)
                            editedName = clean
                            status = "Nombre actualizado."
                        }
                    },
                    enabled = editedName.trim().isNotEmpty()
                ) { Text("Guardar nombre") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Empresa y pagos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = editedCompany,
                    onValueChange = { editedCompany = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre de la empresa") },
                    leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null) }
                )
                Text("Frecuencia de pago", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { paymentFrequency = "WEEKLY" }) { Text("Semanal") }
                    OutlinedButton(onClick = { paymentFrequency = "BIWEEKLY" }) { Text("Quincenal") }
                    OutlinedButton(onClick = { paymentFrequency = "MONTHLY" }) { Text("Mensual") }
                }
                Text(
                    when (paymentFrequency) {
                        "BIWEEKLY" -> "Seleccionado: quincenal"
                        "MONTHLY" -> "Seleccionado: mensual"
                        else -> "Seleccionado: semanal"
                    },
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = {
                    preferences.edit()
                        .putString("company_name", editedCompany.trim())
                        .putString("payment_frequency", paymentFrequency)
                        .apply()
                    status = "Configuración guardada correctamente."
                }) { Text("Guardar configuración") }
                status?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Copia de seguridad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Exporta trabajadores, trabajos, liquidaciones y el nombre de usuario para llevarlos a otro celular.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        val safeName = userName.ifBlank { "rodsanpay" }.replace(" ", "_")
                        exportLauncher.launch("rodsanpay_$safeName.json")
                    }) { Text("Exportar datos") }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }) { Text("Importar datos") }
                }
            }
        }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Creado by Vorem",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Versión 1.1 • Todos los derechos reservados © 2026 Vorem",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "M",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun NameDialog(onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Bienvenido a Muebles RodSan", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Para personalizar el inicio, dime cómo quieres que te llamemos.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Tu nombre") },
                    leadingIcon = {
                        Icon(Icons.Filled.Person, contentDescription = null)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Continuar")
            }
        }
    )
}

private fun formatMoney(value: Long): String =
    "$" + "%,d".format(java.util.Locale("es", "CO"), value)

@Preview(showBackground = true)
@Composable
fun PanelPrincipalPreview() {
    RodSanTheme {
        RodSanHome(rememberNavController(), "Carlos")
    }
}
