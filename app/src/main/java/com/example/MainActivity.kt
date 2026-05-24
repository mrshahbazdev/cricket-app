package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.CricketDatabase
import com.example.data.CricketRepository
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val db by lazy { Room.databaseBuilder(applicationContext, CricketDatabase::class.java, "cricket_db").fallbackToDestructiveMigration(dropAllTables = true).build() }
    private val repository by lazy { CricketRepository(db.cricketDao()) }

    private val viewModel: CricketViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CricketViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CricketApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CricketApp(viewModel: CricketViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Scoring" to Icons.Filled.PlayArrow, "Matches" to Icons.Filled.DateRange, "Leaderboards" to Icons.Filled.Star)
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("ProScore Cricket", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (selectedTab == 0 && (viewModel.phase == MatchPhase.INNINGS_1 || viewModel.phase == MatchPhase.INNINGS_2)) {
                        IconButton(onClick = {
                            val oversScore = "${viewModel.balls / 6}.${viewModel.balls % 6}"
                            val msg = "Live Score: ${viewModel.battingTeam} ${viewModel.runs}/${viewModel.wickets} in $oversScore overs."
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, msg)
                                type = "text/plain"
                            }
                            try {
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, pair ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(pair.second, contentDescription = pair.first) },
                        label = { Text(pair.first) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    when (viewModel.phase) {
                        MatchPhase.SETUP -> MatchSetupScreen(viewModel)
                        MatchPhase.INNINGS_1, MatchPhase.INNINGS_2 -> ScoringScreen(viewModel)
                        MatchPhase.FINISHED -> MatchFinishedScreen(viewModel)
                    }
                }
                1 -> MatchHistoryScreen(viewModel)
                2 -> LeaderboardsScreen(viewModel)
            }
        }
    }
}

@Composable
fun MatchSetupScreen(viewModel: CricketViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Match Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = viewModel.teamA, onValueChange = { viewModel.teamA = it }, label = { Text("Team A") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = viewModel.teamB, onValueChange = { viewModel.teamB = it }, label = { Text("Team B") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Toss Won By:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = viewModel.tossWinner == viewModel.teamA, onClick = { viewModel.tossWinner = viewModel.teamA }, label = { Text(viewModel.teamA) }, modifier = Modifier.weight(1f))
            FilterChip(selected = viewModel.tossWinner == viewModel.teamB, onClick = { viewModel.tossWinner = viewModel.teamB }, label = { Text(viewModel.teamB) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Elected To:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = viewModel.electedTo == "Bat", onClick = { viewModel.electedTo = "Bat" }, label = { Text("Bat") }, modifier = Modifier.weight(1f))
            FilterChip(selected = viewModel.electedTo == "Bowl", onClick = { viewModel.electedTo = "Bowl" }, label = { Text("Bowl") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    var oversStr by remember { mutableStateOf(viewModel.totalOversLimit.toString()) }
    OutlinedTextField(
        value = oversStr, 
        onValueChange = { 
            oversStr = it
            viewModel.totalOversLimit = it.toIntOrNull() ?: 5 
        }, 
        label = { Text("Total Overs") },
        modifier = Modifier.fillMaxWidth()
    )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { viewModel.startMatch() }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Start Match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScoringScreen(viewModel: CricketViewModel) {
    if (viewModel.needsNewBatter) {
        val existingBatters = viewModel.sessionBatters.keys.filter { it != viewModel.striker.name && it != viewModel.nonStriker.name }
        PlayerSelectionDialog("Striker Name", existingBatters) { viewModel.setNewBatter(it) }
    } else if (viewModel.needsNewNonStriker) {
        val existingBatters = viewModel.sessionBatters.keys.filter { it != viewModel.striker.name && it != viewModel.nonStriker.name }
        PlayerSelectionDialog("Non-Striker Name", existingBatters) { viewModel.setNewNonStriker(it) }
    } else if (viewModel.needsNewBowler) {
        val existingBowlers = viewModel.sessionBowlers.keys.filter { it != viewModel.currentBowler.name }
        PlayerSelectionDialog("Bowler Name", existingBowlers) { viewModel.setNewBowler(it) }
    }

    val crr = if (viewModel.balls > 0) String.format(java.util.Locale.US, "%.2f", viewModel.runs / (viewModel.balls / 6.0)) else "0.00"

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Sticky Top Section
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(viewModel.battingTeam, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(" VS ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Text(viewModel.bowlingTeam, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Text("${viewModel.runs}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(" / ${viewModel.wickets}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("OVERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text("${viewModel.balls / 6}.${viewModel.balls % 6} / ${viewModel.totalOversLimit}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CRR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text(crr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (viewModel.phase == MatchPhase.INNINGS_2) {
                    val reqBalls = viewModel.totalOversLimit * 6 - viewModel.balls
                    val reqRuns = (viewModel.target - viewModel.runs).coerceAtLeast(0)
                    val rrr = if (reqBalls > 0) String.format(java.util.Locale.US, "%.2f", (reqRuns / (reqBalls / 6.0))) else "0.00"
                    Spacer(Modifier.height(16.dp))
                    Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Target: ${viewModel.target}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                                Text("RRR: $rrr", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Need $reqRuns runs from $reqBalls balls", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            // Batters Table
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Batsman", Modifier.weight(2.5f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("R", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("B", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4s", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("6s", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("SR", Modifier.weight(1.2f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PlayerRow(viewModel.striker, true)
                    PlayerRow(viewModel.nonStriker, false)
                    Spacer(Modifier.height(8.dp))
                    Text("Extras: ${viewModel.extras}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            // Bowler Table
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
                 Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                         Text("Bowler", Modifier.weight(2.5f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         Text("O", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         Text("M", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         Text("R", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         Text("W", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         Text("ER", Modifier.weight(1.2f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                     }
                     HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                     val b = viewModel.currentBowler
                     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                         Text(b.name, Modifier.weight(2.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                         Text(b.overs, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                         Text("${b.maidens}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                         Text("${b.runs}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                         Text("${b.wickets}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                         Text(b.econ, Modifier.weight(1.2f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                     }
                 }
            }
            Spacer(Modifier.height(16.dp))
            // This Over
            Text("This Over", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            val events = viewModel.currentOverHistory.toList()
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events) { event ->
                    val isWicket = event.startsWith("W")
                    val isBoundary = event == "4" || event == "6"
                    val isExtra = event.contains("WD") || event.contains("NB") || event.contains("B")
                    val bgColor = when {
                        isWicket -> MaterialTheme.colorScheme.error
                        isBoundary -> MaterialTheme.colorScheme.tertiary
                        isExtra -> MaterialTheme.colorScheme.secondary
                        event == "0" -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val textColor = if (event == "0") MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(bgColor).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(event, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Control Panel anchored at bottom
        Surface(
            shadowElevation = 24.dp, 
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            var activeExtrasDialog by remember { mutableStateOf<ExtraType?>(null) }
            
            if (activeExtrasDialog != null) {
                ExtraSelectionDialog(
                    type = activeExtrasDialog!!,
                    onDismiss = { activeExtrasDialog = null },
                    onConfirm = { batRuns, extRuns, isWicket ->
                        viewModel.recordExplicitAction(batRuns, extRuns, isWicket, activeExtrasDialog!!)
                        activeExtrasDialog = null
                    }
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                // Secondary Controls
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalButton(onClick = { viewModel.swapStrike() }, shape = RoundedCornerShape(12.dp)) {
                        Text("Swap Batsman")
                    }
                    FilledTonalButton(onClick = { viewModel.needsNewBowler = true }, shape = RoundedCornerShape(12.dp)) {
                        Text("Swap Bowler")
                    }
                    FilledIconButton(onClick = { viewModel.undo() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Refresh, contentDescription = "Undo")
                    }
                }
                Spacer(Modifier.height(16.dp))
                
                // Keypad
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreBtn("0", Modifier.weight(1f)) { viewModel.recordExplicitAction(0, 0, false, ExtraType.NONE) }
                    ScoreBtn("1", Modifier.weight(1f)) { viewModel.recordExplicitAction(1, 0, false, ExtraType.NONE) }
                    ScoreBtn("2", Modifier.weight(1f)) { viewModel.recordExplicitAction(2, 0, false, ExtraType.NONE) }
                    ScoreBtn("3", Modifier.weight(1f)) { viewModel.recordExplicitAction(3, 0, false, ExtraType.NONE) }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreBtn("4", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { viewModel.recordExplicitAction(4, 0, false, ExtraType.NONE) }
                    ScoreBtn("6", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { viewModel.recordExplicitAction(6, 0, false, ExtraType.NONE) }
                    ScoreBtn("WICKET", Modifier.weight(2f), containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) { viewModel.recordExplicitAction(0, 0, true, ExtraType.NONE) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreBtn("WD", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { activeExtrasDialog = ExtraType.WIDE }
                    ScoreBtn("NB", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { activeExtrasDialog = ExtraType.NO_BALL }
                    ScoreBtn("BYE", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) { activeExtrasDialog = ExtraType.BYE }
                    ScoreBtn("LB", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) { activeExtrasDialog = ExtraType.LEG_BYE }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraSelectionDialog(
    type: ExtraType,
    onDismiss: () -> Unit,
    onConfirm: (runsScoredOffBat: Int, extraRuns: Int, isWicket: Boolean) -> Unit
) {
    var selectedRuns by remember { mutableStateOf(0) }
    var isWicket by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(type.name.replace("_", " ")) },
        text = {
            Column {
                Text(if (type == ExtraType.NO_BALL) "Runs off bat:" else "Extras run:")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val runsArray = if (type == ExtraType.WIDE) listOf(1, 2, 3, 4, 5) else listOf(0, 1, 2, 3, 4, 6)
                    runsArray.forEach { r ->
                        val label = if (type == ExtraType.WIDE) "${r}WD" else "$r"
                        FilterChip(selected = selectedRuns == r, onClick = { selectedRuns = r }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = isWicket, onCheckedChange = { isWicket = it })
                    Text("Wicket on this ball")
                }
            }
        },
        confirmButton = { 
            Button(onClick = { 
                val batRuns = if (type == ExtraType.NO_BALL) selectedRuns else 0
                val extRuns = if (type != ExtraType.NO_BALL) selectedRuns else 1
                onConfirm(batRuns, extRuns, isWicket)
            }) { Text("OK") } 
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PlayerRow(stats: BatterStats, isStriker: Boolean) {
    val color = if (isStriker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stats.name + (if (isStriker) "*" else ""), Modifier.weight(2.5f), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if(isStriker) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text("${stats.runs}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = if(isStriker) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text("${stats.balls}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Text("${stats.fours}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Text("${stats.sixes}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Text(stats.sr, Modifier.weight(1.2f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MatchFinishedScreen(viewModel: CricketViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Star, contentDescription = "Trophy", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Match Finished!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(), 
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(viewModel.teamA, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text("${viewModel.inn1Runs} / ${viewModel.inn1Wickets}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier.padding(horizontal = 32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Text(viewModel.teamB, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text("${viewModel.inn2Runs} / ${viewModel.inn2Wickets}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { viewModel.saveMatchAndReset() }, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save & Reset Match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerSelectionDialog(title: String, existingPlayers: List<String>, onConfirm: (String) -> Unit) {
    var text by remember(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { }, // Prevent accidental dismiss
        title = { Text(title) },
        text = { 
            Column {
                if (existingPlayers.isNotEmpty()) {
                    Text("Select existing:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existingPlayers.forEach { p ->
                            FilterChip(
                                selected = text == p,
                                onClick = { text = p },
                                label = { Text(p) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Or enter new:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text, 
                    onValueChange = { text = it }, 
                    singleLine = true, 
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if(text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text("OK") }
        }
    )
}

@Composable
fun ScoreBtn(text: String, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surfaceVariant, contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Button(
        onClick = onClick, 
        modifier = modifier.height(64.dp), 
        shape = RoundedCornerShape(16.dp), 
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor, 
            contentColor = contentColor
        )
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MatchHistoryScreen(viewModel: CricketViewModel) {
    val matches by viewModel.matchHistory.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Match History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        items(matches) { match ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(match.summary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun LeaderboardsScreen(viewModel: CricketViewModel) {
    val batters by viewModel.playersByRuns.collectAsState()
    val bowlers by viewModel.playersByWickets.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Top Batsmen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        items(batters.take(10)) { p ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${p.runsScored} runs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("${p.ballsFaced} balls", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item { Text("Top Bowlers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        items(bowlers.take(10)) { p ->
             Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${p.wicketsTaken} wkts", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("${p.runsConceded} runs", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
