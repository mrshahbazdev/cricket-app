package com.example.data

import kotlinx.coroutines.flow.Flow

class CricketRepository(private val dao: CricketDao) {
    val playersByRuns: Flow<List<PlayerEntity>> = dao.getPlayersByRuns()
    val playersByWickets: Flow<List<PlayerEntity>> = dao.getPlayersByWickets()
    val matchHistory: Flow<List<MatchEntity>> = dao.getAllMatches()

    suspend fun getOrCreatePlayer(name: String): PlayerEntity {
        val existing = dao.getPlayerByName(name)
        if (existing != null) return existing
        val newPlayer = PlayerEntity(name = name)
        val id = dao.insertPlayer(newPlayer)
        return newPlayer.copy(id = id.toInt())
    }

    suspend fun updatePlayerStats(
        playerId: Int,
        runsScored: Int,
        ballsFaced: Int,
        wicketsTaken: Int,
        ballsBowled: Int,
        runsConceded: Int
    ) {
        val player = dao.getPlayerById(playerId) ?: return
        dao.updatePlayer(
            player.copy(
                runsScored = player.runsScored + runsScored,
                ballsFaced = player.ballsFaced + ballsFaced,
                wicketsTaken = player.wicketsTaken + wicketsTaken,
                ballsBowled = player.ballsBowled + ballsBowled,
                runsConceded = player.runsConceded + runsConceded,
                matchesPlayed = player.matchesPlayed + 1
            )
        )
    }

    suspend fun insertMatch(match: MatchEntity) {
        dao.insertMatch(match)
    }
}
