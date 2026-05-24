package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import androidx.room.Database
import androidx.room.RoomDatabase

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val runsScored: Int = 0,
    val ballsFaced: Int = 0,
    val wicketsTaken: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val matchesPlayed: Int = 0
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val summary: String,
    val bestBatsman: String,
    val bestBowler: String
)

@Dao
interface CricketDao {
    @Query("SELECT * FROM players ORDER BY runsScored DESC, ballsFaced ASC")
    fun getPlayersByRuns(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY wicketsTaken DESC, runsConceded ASC")
    fun getPlayersByWickets(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM matches ORDER BY date DESC")
    fun getAllMatches(): Flow<List<MatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity): Long

    @Update
    suspend fun updatePlayer(player: PlayerEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity)
    
    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    suspend fun getPlayerByName(name: String): PlayerEntity?

    @Query("SELECT * FROM players WHERE id = :id LIMIT 1")
    suspend fun getPlayerById(id: Int): PlayerEntity?
}

@Database(entities = [PlayerEntity::class, MatchEntity::class], version = 1, exportSchema = false)
abstract class CricketDatabase : RoomDatabase() {
    abstract fun cricketDao(): CricketDao
}
