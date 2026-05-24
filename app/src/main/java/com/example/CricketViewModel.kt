package com.example

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CricketRepository
import com.example.data.MatchEntity
import com.example.data.PlayerEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MatchPhase { SETUP, INNINGS_1, INNINGS_2, FINISHED }
enum class ExtraType { NONE, WIDE, NO_BALL, BYE, LEG_BYE }

data class BatterStats(
    var name: String = "",
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0
) {
    val sr: String get() = if (balls > 0) String.format(java.util.Locale.US, "%.1f", (runs.toFloat() / balls) * 100) else "0.0"
}

data class BowlerStats(
    var name: String = "",
    var runs: Int = 0,
    var balls: Int = 0,
    var wickets: Int = 0,
    var maidens: Int = 0
) {
    val econ: String get() = if (balls > 0) String.format(java.util.Locale.US, "%.1f", runs.toFloat() / (balls / 6.0f)) else "0.0"
    val overs: String get() = "${balls / 6}.${balls % 6}"
}

class CricketViewModel(private val repository: CricketRepository) : ViewModel() {
    val playersByRuns: StateFlow<List<PlayerEntity>> = repository.playersByRuns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playersByWickets: StateFlow<List<PlayerEntity>> = repository.playersByWickets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val matchHistory: StateFlow<List<MatchEntity>> = repository.matchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var phase by mutableStateOf(MatchPhase.SETUP)

    // Setup Params
    var teamA by mutableStateOf("Team A")
    var teamB by mutableStateOf("Team B")
    var tossWinner by mutableStateOf("Team A")
    var electedTo by mutableStateOf("Bat")
    var totalOversLimit by mutableIntStateOf(5)

    // Running State
    var battingTeam by mutableStateOf("")
    var bowlingTeam by mutableStateOf("")

    var runs by mutableIntStateOf(0)
    var wickets by mutableIntStateOf(0)
    var balls by mutableIntStateOf(0)
    var extras by mutableIntStateOf(0)
    var currentOverHistory = mutableStateListOf<String>()

    var target by mutableIntStateOf(0)
    var inn1Runs by mutableIntStateOf(0)
    var inn1Wickets by mutableIntStateOf(0)
    var inn1Balls by mutableIntStateOf(0)
    var inn2Runs by mutableIntStateOf(0)
    var inn2Wickets by mutableIntStateOf(0)
    var inn2Balls by mutableIntStateOf(0)

    var striker by mutableStateOf(BatterStats("Batsman 1"))
    var nonStriker by mutableStateOf(BatterStats("Batsman 2"))
    var currentBowler by mutableStateOf(BowlerStats("Bowler 1"))

    var activeExtra by mutableStateOf(ExtraType.NONE)

    var needsNewBatter by mutableStateOf(false)
    var needsNewNonStriker by mutableStateOf(false)
    var needsNewBowler by mutableStateOf(false)

    val sessionBatters = mutableMapOf<String, BatterStats>()
    val sessionBowlers = mutableMapOf<String, BowlerStats>()

    data class TempDbStats(var r: Int=0, var b: Int=0, var w: Int=0, var bb: Int=0, var rc: Int=0)
    val allPlayersStats = mutableMapOf<String, TempDbStats>()

    data class MatchStateSnapshot(
        val runs: Int, val wickets: Int, val balls: Int, val extras: Int,
        val overHistory: List<String>,
        val striker: BatterStats, val nonStriker: BatterStats,
        val currentBowler: BowlerStats,
        val dbStats: Map<String, TempDbStats>,
        val needsNewBatter: Boolean, val needsNewNonStriker: Boolean, val needsNewBowler: Boolean
    )
    
    private val historyStack = mutableListOf<MatchStateSnapshot>()

    fun startMatch() {
        if (tossWinner == teamA) {
            battingTeam = if (electedTo == "Bat") teamA else teamB
        } else {
            battingTeam = if (electedTo == "Bat") teamB else teamA
        }
        bowlingTeam = if (battingTeam == teamA) teamB else teamA
        phase = MatchPhase.INNINGS_1
        needsNewBatter = true
        needsNewNonStriker = true
        needsNewBowler = true
    }

    fun swapStrike() {
        val t = striker; striker = nonStriker; nonStriker = t
    }

    fun undo() {
        if (historyStack.isEmpty() || phase == MatchPhase.FINISHED || phase == MatchPhase.SETUP) return
        val snapshot = historyStack.removeAt(historyStack.lastIndex)
        
        runs = snapshot.runs
        wickets = snapshot.wickets
        balls = snapshot.balls
        extras = snapshot.extras
        
        currentOverHistory.clear()
        currentOverHistory.addAll(snapshot.overHistory)
        
        striker = snapshot.striker.copy()
        nonStriker = snapshot.nonStriker.copy()
        currentBowler = snapshot.currentBowler.copy()
        
        allPlayersStats.clear()
        snapshot.dbStats.forEach { (k, v) ->
            allPlayersStats[k] = v.copy()
        }
        
        sessionBatters[striker.name] = striker
        sessionBatters[nonStriker.name] = nonStriker
        sessionBowlers[currentBowler.name] = currentBowler
        
        needsNewBatter = snapshot.needsNewBatter
        needsNewNonStriker = snapshot.needsNewNonStriker
        needsNewBowler = snapshot.needsNewBowler
        activeExtra = ExtraType.NONE
    }

    private fun takeSnapshot() {
        historyStack.add(
            MatchStateSnapshot(
                runs = runs, wickets = wickets, balls = balls, extras = extras,
                overHistory = currentOverHistory.toList(),
                striker = striker.copy(), nonStriker = nonStriker.copy(),
                currentBowler = currentBowler.copy(),
                dbStats = allPlayersStats.mapValues { it.value.copy() },
                needsNewBatter = needsNewBatter, needsNewNonStriker = needsNewNonStriker, needsNewBowler = needsNewBowler
            )
        )
    }

    fun recordExplicitAction(batRuns: Int, extraRuns: Int, isWicket: Boolean, eTypeRaw: ExtraType) {
        if (wickets >= 10 || phase == MatchPhase.FINISHED || phase == MatchPhase.SETUP) return

        takeSnapshot()

        var isLegal = true
        var eType = ""

        when (eTypeRaw) {
            ExtraType.WIDE -> { isLegal = false; eType = "WD" }
            ExtraType.NO_BALL -> { isLegal = false; eType = "NB" }
            ExtraType.BYE -> { eType = "B" }
            ExtraType.LEG_BYE -> { eType = "LB" }
            ExtraType.NONE -> { eType = if (isWicket) "W" else batRuns.toString() }
        }

        val totalRuns = batRuns + extraRuns

        // Stats
        val sSt = allPlayersStats.getOrPut(striker.name) { TempDbStats() }
        sSt.r += batRuns
        if (isLegal || eTypeRaw == ExtraType.NO_BALL) sSt.b++
        
        val bSt = allPlayersStats.getOrPut(currentBowler.name) { TempDbStats() }
        if (eTypeRaw != ExtraType.LEG_BYE && eTypeRaw != ExtraType.BYE) {
            bSt.rc += totalRuns
            currentBowler.runs += totalRuns
        }
        if (isLegal) {
            bSt.bb++
            currentBowler.balls++
        }
        if (isWicket) {
            bSt.w++
            currentBowler.wickets++
        }

        striker.runs += batRuns
        if (isLegal || eTypeRaw == ExtraType.NO_BALL) striker.balls++
        if (batRuns == 4) striker.fours++
        if (batRuns == 6) striker.sixes++

        runs += totalRuns
        extras += extraRuns
        if (isWicket) wickets++
        if (isLegal) balls++

        // Over History
        var label = eType
        if (eTypeRaw == ExtraType.WIDE) label = "${extraRuns}WD"
        if (eTypeRaw == ExtraType.NO_BALL && batRuns > 0) label = "${batRuns}NB"
        if (eTypeRaw == ExtraType.BYE || eTypeRaw == ExtraType.LEG_BYE) label = "${extraRuns}$eType"
        if (isWicket && eTypeRaw != ExtraType.NONE) label = "W+" + label

        val historyList = currentOverHistory.toMutableList()
        val legalCount = historyList.count { !it.contains("WD") && !it.contains("NB") }
        if (legalCount >= 6) historyList.clear()
        historyList.add(label)
        currentOverHistory.clear()
        currentOverHistory.addAll(historyList)

        // Auto swap Logic
        var swapNow = (batRuns + (if(eTypeRaw==ExtraType.BYE || eTypeRaw==ExtraType.LEG_BYE) extraRuns else 0)) % 2 != 0
        if (isLegal && balls > 0 && balls % 6 == 0) swapNow = !swapNow
        if (swapNow) swapStrike()

        sessionBatters[striker.name] = striker
        sessionBatters[nonStriker.name] = nonStriker
        sessionBowlers[currentBowler.name] = currentBowler

        if (isWicket) needsNewBatter = true
        if (isLegal && balls > 0 && balls % 6 == 0) needsNewBowler = true

        val isEndOfInnings = wickets >= 10 || balls >= totalOversLimit * 6 || (phase == MatchPhase.INNINGS_2 && runs >= target)
        if (isEndOfInnings) {
            endInnings()
        }
    }

    fun recordAction(runsScored: Int, isWicket: Boolean) {
        if (wickets >= 10 || phase == MatchPhase.FINISHED || phase == MatchPhase.SETUP) return

        takeSnapshot()

        var batRuns = runsScored
        var extraRuns = 0
        var eType = ""
        var isLegal = true

        if (isWicket) {
            batRuns = 0; extraRuns = 0; eType = "W"
        } else {
            when (activeExtra) {
                ExtraType.WIDE -> { batRuns = 0; extraRuns = runsScored + 1; eType = "WD"; isLegal = false }
                ExtraType.NO_BALL -> { batRuns = runsScored; extraRuns = 1; eType = "NB"; isLegal = false }
                ExtraType.BYE -> { batRuns = 0; extraRuns = runsScored; eType = "B" }
                ExtraType.LEG_BYE -> { batRuns = 0; extraRuns = runsScored; eType = "LB" }
                ExtraType.NONE -> { eType = runsScored.toString() }
            }
        }

        val totalRuns = batRuns + extraRuns

        // Stats
        val sSt = allPlayersStats.getOrPut(striker.name) { TempDbStats() }
        sSt.r += batRuns
        if (isLegal || activeExtra == ExtraType.NO_BALL) sSt.b++
        
        val bSt = allPlayersStats.getOrPut(currentBowler.name) { TempDbStats() }
        if (activeExtra != ExtraType.LEG_BYE && activeExtra != ExtraType.BYE) {
            bSt.rc += totalRuns
            currentBowler.runs += totalRuns
        }
        if (isLegal) {
            bSt.bb++
            currentBowler.balls++
        }
        if (isWicket) {
            bSt.w++
            currentBowler.wickets++
        }

        striker.runs += batRuns
        if (isLegal || activeExtra == ExtraType.NO_BALL) striker.balls++
        if (batRuns == 4) striker.fours++
        if (batRuns == 6) striker.sixes++

        runs += totalRuns
        extras += extraRuns
        if (isWicket) wickets++
        if (isLegal) balls++

        // Over History
        var label = eType
        if (activeExtra == ExtraType.WIDE) label = "${extraRuns}WD"
        if (activeExtra == ExtraType.NO_BALL && batRuns > 0) label = "${batRuns}NB"
        if (activeExtra == ExtraType.BYE || activeExtra == ExtraType.LEG_BYE) label = "${extraRuns}$eType"

        val historyList = currentOverHistory.toMutableList()
        val legalCount = historyList.count { !it.contains("WD") && !it.contains("NB") }
        if (legalCount >= 6) historyList.clear()
        historyList.add(label)
        currentOverHistory.clear()
        currentOverHistory.addAll(historyList)

        // Auto swap Logic
        var swapNow = (batRuns + (if(activeExtra==ExtraType.BYE || activeExtra==ExtraType.LEG_BYE) extraRuns else 0)) % 2 != 0
        if (isLegal && balls > 0 && balls % 6 == 0) swapNow = !swapNow
        if (swapNow) swapStrike()

        activeExtra = ExtraType.NONE
        sessionBatters[striker.name] = striker
        sessionBatters[nonStriker.name] = nonStriker
        sessionBowlers[currentBowler.name] = currentBowler

        if (isWicket) needsNewBatter = true
        if (isLegal && balls > 0 && balls % 6 == 0) needsNewBowler = true

        val isEndOfInnings = wickets >= 10 || balls >= totalOversLimit * 6 || (phase == MatchPhase.INNINGS_2 && runs >= target)
        if (isEndOfInnings) {
            endInnings()
        }
    }

    fun setNewBatter(name: String) {
        val bName = name.trim().takeIf { it.isNotEmpty() } ?: "Player"
        striker = sessionBatters[bName] ?: BatterStats(name = bName)
        needsNewBatter = false
    }

    fun setNewNonStriker(name: String) {
        val bName = name.trim().takeIf { it.isNotEmpty() } ?: "Player"
        nonStriker = sessionBatters[bName] ?: BatterStats(name = bName)
        needsNewNonStriker = false
    }

    fun setNewBowler(name: String) {
        val bName = name.trim().takeIf { it.isNotEmpty() } ?: "Bowler"
        currentBowler = sessionBowlers[bName] ?: BowlerStats(name = bName)
        needsNewBowler = false
    }

    private fun endInnings() {
        if (phase == MatchPhase.INNINGS_1) {
            inn1Runs = runs
            inn1Wickets = wickets
            inn1Balls = balls
            target = runs + 1

            val t = battingTeam
            battingTeam = bowlingTeam
            bowlingTeam = t

            runs = 0
            wickets = 0
            balls = 0
            extras = 0
            currentOverHistory.clear()

            striker = BatterStats("Batsman 1")
            nonStriker = BatterStats("Batsman 2")
            currentBowler = BowlerStats("Bowler 1")
            
            needsNewBatter = true 
            needsNewNonStriker = true
            needsNewBowler = true 

            historyStack.clear()

            phase = MatchPhase.INNINGS_2
        } else {
            inn2Runs = runs
            inn2Wickets = wickets
            inn2Balls = balls
            phase = MatchPhase.FINISHED
        }
    }

    fun saveMatchAndReset() {
        viewModelScope.launch {
            repository.insertMatch(MatchEntity(
                summary = "$battingTeam $inn2Runs/$inn2Wickets vs $bowlingTeam $inn1Runs/$inn1Wickets",
                bestBatsman = "Check Leaderboards",
                bestBowler = "Check Leaderboards"
            ))
            for ((pName, st) in allPlayersStats) {
                val p = repository.getOrCreatePlayer(pName)
                repository.updatePlayerStats(p.id, st.r, st.b, st.w, st.bb, st.rc)
            }
            
            phase = MatchPhase.SETUP
            runs = 0; wickets = 0; balls = 0; extras = 0; inn1Runs = 0; inn1Wickets = 0; inn1Balls = 0; inn2Runs = 0; inn2Wickets = 0; inn2Balls = 0; target = 0
            currentOverHistory.clear()
            historyStack.clear()
            allPlayersStats.clear()
            sessionBatters.clear()
            sessionBowlers.clear()
            teamA = "Team A"; teamB = "Team B"
            tossWinner = "Team A"; electedTo = "Bat"
        }
    }
}
