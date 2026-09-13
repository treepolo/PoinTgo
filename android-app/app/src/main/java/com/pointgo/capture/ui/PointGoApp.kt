package com.pointgo.capture.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pointgo.capture.ui.screens.HistoryScreen
import com.pointgo.capture.ui.screens.LiveScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointGoApp(viewModel: PointGoViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Live", "Replay")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PointGo Analyzer · ${titles[selectedTab]}") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("●") },
                    label = { Text("Live") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("↺") },
                    label = { Text("Replay") },
                )
            }
        },
    ) { paddingValues: PaddingValues ->
        when (selectedTab) {
            0 -> LiveScreen(
                state = state,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onToggleRecording = viewModel::toggleRecording,
                onMetricSelected = viewModel::setSelectedMetric,
                onToggleAdvanced = {
                    viewModel.setAdvancedPanelExpanded(!state.advancedPanelExpanded)
                },
                onDeviceAddressChanged = viewModel::setDeviceAddress,
                onSmoothingChanged = viewModel::setSmoothingWindow,
                onThresholdChanged = viewModel::setWarningThreshold,
                modifier = Modifier.padding(paddingValues),
            )

            else -> HistoryScreen(
                state = state,
                onSelectSession = viewModel::selectSession,
                onAddAnnotation = viewModel::addAnnotation,
                onExport = viewModel::exportSelected,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
