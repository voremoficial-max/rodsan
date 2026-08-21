package com.rodsan.pay.ui.worker

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.rodsan.pay.ui.settlement.SettlementHistoryScreen

@Composable
fun WorkerHistoryScreen(navController: NavHostController, workerId: Long) {
    SettlementHistoryScreen(navController = navController, initialWorkerId = workerId)
}
