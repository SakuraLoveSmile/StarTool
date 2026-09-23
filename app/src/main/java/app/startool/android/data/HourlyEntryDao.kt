package app.startool.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlyEntryDao {

    @Query("SELECT * FROM hourly_entries WHERE date = :date ORDER BY hour ASC")
    fun observeDay(date: String): Flow<List<HourlyEntryEntity>>

    @Query("SELECT DISTINCT date FROM hourly_entries ORDER BY date ASC")
    fun observeRecordedDates(): Flow<List<String>>

    @Query("SELECT * FROM hourly_entries WHERE date = :date AND hour = :hour LIMIT 1")
    suspend fun getEntry(date: String, hour: Int): HourlyEntryEntity?

    @Query("SELECT * FROM hourly_entries ORDER BY date ASC, hour ASC")
    suspend fun getAllEntries(): List<HourlyEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: HourlyEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HourlyEntryEntity>)

    @Query("DELETE FROM hourly_entries WHERE date = :date AND hour = :hour")
    suspend fun delete(date: String, hour: Int): Int
}
