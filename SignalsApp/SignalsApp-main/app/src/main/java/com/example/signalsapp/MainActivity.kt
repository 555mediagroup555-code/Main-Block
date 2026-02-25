package com.example.signalsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SignalsScreen() } }
    }
}

private enum class RecommendedEntrySort(val title: String) {
    DEFAULT("Recommended entry (default)"),
    LONG_SHORT_NO_TRADE("Long → Short → No trade"),
    SHORT_LONG_NO_TRADE("Short → Long → No trade")
}

private enum class CoinSort(val title: String) {
    NAME("Coin name (A-Z)"),
    DAILY_SELL_VOLUME("Daily sell volume (high → low)"),
    MARKET_CAP("Market cap (high → low)")
}

private enum class PageSize(val title: String, val value: Int) {
    TEN("10 per page", 10),
    TWENTY("20 per page", 20),
    FIFTY("50 per page", 50)
}

@Composable
fun SignalsScreen() {
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf("http://192.168.1.112:8010") }
    var signals by remember { mutableStateOf<List<LiteSignal>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var recommendedSort by remember { mutableStateOf(RecommendedEntrySort.DEFAULT) }
    var coinSort by remember { mutableStateOf(CoinSort.NAME) }
    var selectedTimeframe by remember { mutableStateOf("All") }
    var pageSize by remember { mutableStateOf(PageSize.TEN) }
    var currentPage by remember { mutableStateOf(0) }

    fun refresh() {
        loading = true
        status = "Loading..."
        val api = buildApi(baseUrl)

        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    try {
                        api.scanLite()
                    } catch (_: Exception) {
                        api.scan()
                    }
                }
                signals = data
                status = "Loaded: ${data.size}"
            } catch (e: Exception) {
                status = "Error: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    val timeframeOptions = remember(signals) {
        val preferred = listOf("5s", "1m", "5m", "15m", "1h", "4h")
        val normalizedSeen = mutableSetOf<String>()
        val fromApi = signals
            .mapNotNull { it.timeframe }
            .map { normalizeTimeframeLabel(it) }
            .filter { normalizedSeen.add(it.lowercase()) }

        val others = fromApi.filterNot { value ->
            preferred.any { it.equals(value, ignoreCase = true) }
        }.sorted()

        listOf("All") + preferred + others
    }

    val visibleSignals = remember(signals, selectedTimeframe, recommendedSort, coinSort) {
        val timeframeFiltered = if (selectedTimeframe == "All") {
            signals
        } else {
            signals.filter {
                normalizeTimeframeLabel(it.timeframe.orEmpty()).equals(selectedTimeframe, ignoreCase = true)
            }
        }

        timeframeFiltered
            .sortedWith(
                compareBy<LiteSignal> { recommendationRank(it.signal.orEmpty(), recommendedSort) }
                    .thenBy { coinRank(it, coinSort) }
            )
    }

    val totalPages = remember(visibleSignals, pageSize) {
        if (visibleSignals.isEmpty()) 1 else ceil(visibleSignals.size / pageSize.value.toDouble()).toInt()
    }

    LaunchedEffect(visibleSignals, pageSize) {
        currentPage = 0
    }

    val pagedSignals = remember(visibleSignals, currentPage, pageSize) {
        val from = currentPage * pageSize.value
        val to = (from + pageSize.value).coerceAtMost(visibleSignals.size)
        if (from in 0 until to) visibleSignals.subList(from, to) else emptyList()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it.trim() },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { refresh() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Loading..." else "Refresh") }

        Spacer(Modifier.height(8.dp))
        Text(status)

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                modifier = Modifier.weight(1f),
                label = "Entry sort",
                selected = recommendedSort.title,
                options = RecommendedEntrySort.entries.map { it.title },
                onSelect = { selected ->
                    recommendedSort = RecommendedEntrySort.entries.first { it.title == selected }
                }
            )

            DropdownField(
                modifier = Modifier.weight(1f),
                label = "Coin sort",
                selected = coinSort.title,
                options = CoinSort.entries.map { it.title },
                onSelect = { selected ->
                    coinSort = CoinSort.entries.first { it.title == selected }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                label = "Timeframe",
                selected = selectedTimeframe,
                options = timeframeOptions,
                onSelect = { selectedTimeframe = it },
                modifier = Modifier.weight(1f)
            )

            DropdownField(
                label = "Pagination",
                selected = pageSize.title,
                options = PageSize.entries.map { it.title },
                onSelect = { selected ->
                    pageSize = PageSize.entries.first { it.title == selected }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                enabled = currentPage > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Previous") }

            Button(
                onClick = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) },
                enabled = currentPage < totalPages - 1,
                modifier = Modifier.weight(1f)
            ) { Text("Next") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Page ${currentPage + 1} of $totalPages")

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pagedSignals) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(s.symbol.orEmpty(), fontWeight = FontWeight.Bold)
                        Text("${s.signal.orEmpty()} • ${s.score ?: 0} • ${s.timeframe.orEmpty()}")
                        Text("Recommended entry: ${s.recommendedEntryPriceLabel()}")
                        Text("Entry reason: ${s.entryExplanationLabel()}")
                        Text(s.summary.orEmpty())
                    }
                }
            }
        }
    }
}


private fun normalizeTimeframeLabel(raw: String): String {
    val normalized = raw.trim().lowercase()
    return when (normalized) {
        "5s", "5sec", "5secs", "5second", "5seconds" -> "5s"
        "1m", "1min", "1mins", "1minute", "1minutes" -> "1m"
        "5m", "5min", "5mins", "5minute", "5minutes" -> "5m"
        "15m", "15min", "15mins", "15minute", "15minutes" -> "15m"
        "1h", "1hr", "1hour", "60m", "60min" -> "1h"
        "4h", "4hr", "4hour", "240m", "240min" -> "4h"
        else -> raw.trim()
    }
}

private fun recommendationRank(signal: String, sort: RecommendedEntrySort): Int {
    val normalized = signal.uppercase()
    return when (sort) {
        RecommendedEntrySort.DEFAULT -> 0
        RecommendedEntrySort.LONG_SHORT_NO_TRADE -> when {
            normalized.contains("LONG") -> 0
            normalized.contains("SHORT") -> 1
            else -> 2
        }

        RecommendedEntrySort.SHORT_LONG_NO_TRADE -> when {
            normalized.contains("SHORT") -> 0
            normalized.contains("LONG") -> 1
            else -> 2
        }
    }
}

private fun coinRank(signal: LiteSignal, sort: CoinSort): Comparable<*> = when (sort) {
    CoinSort.NAME -> signal.symbol.orEmpty()
    CoinSort.DAILY_SELL_VOLUME -> -(signal.dailySellVolume ?: Double.NEGATIVE_INFINITY)
    CoinSort.MARKET_CAP -> -(signal.marketCap ?: Double.NEGATIVE_INFINITY)
}

private fun LiteSignal.recommendedEntryPriceLabel(): String {
    recommendedEntryPrice?.let { return "$${"%.4f".format(it)}" }

    val zone = entry_zone
    if (zone.isNullOrEmpty()) return "N/A"

    val recommended = when {
        signal.orEmpty().contains("LONG", ignoreCase = true) -> zone.minOrNull() ?: (price ?: 0.0)
        signal.orEmpty().contains("SHORT", ignoreCase = true) -> zone.maxOrNull() ?: (price ?: 0.0)
        else -> zone.average()
    }

    return "$${"%.4f".format(recommended)}"
}

private fun LiteSignal.entryExplanationLabel(): String {
    return entryExplanation.orEmpty().ifBlank {
        when {
            recommendedEntryPrice != null -> "Recommended entry provided by server"
            !entry_zone.isNullOrEmpty() -> "Entry computed from entry zone"
            else -> "N/A"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
