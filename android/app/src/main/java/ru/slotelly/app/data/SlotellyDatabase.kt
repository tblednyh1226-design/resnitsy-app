package ru.slotelly.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("select * from appointments where status != 'cancelled' order by startsAt") fun observeActive(): Flow<List<AppointmentEntity>>
    @Query("select * from appointments order by startsAt") suspend fun all(): List<AppointmentEntity>
    @Query("select * from appointments where id=:id limit 1") suspend fun get(id: String): AppointmentEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(items: List<AppointmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: AppointmentEntity)
    @Query("update appointments set status=:status,pending=1,updatedAt=:now where id=:id") suspend fun setStatus(id: String, status: String, now: Long = System.currentTimeMillis())
    @Query("update appointments set status='completed_paid',paymentsJson=:payments,pending=1,updatedAt=:now where id=:id") suspend fun setPayment(id: String, payments: String, now: Long = System.currentTimeMillis())
    @Query("update appointments set pending=0 where id=:id") suspend fun clearPending(id: String)
    @Query("delete from appointments where id=:id") suspend fun delete(id: String)
}

@Dao
interface ClientDao {
    @Query("select * from clients order by name") fun observeAll(): Flow<List<ClientEntity>>
    @Query("select * from clients order by name") suspend fun all(): List<ClientEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(items: List<ClientEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ClientEntity)
}

@Dao
interface ServiceDao {
    @Query("select * from services where active=1 order by name") fun observeActive(): Flow<List<ServiceEntity>>
    @Query("select * from services where active=1 order by name") suspend fun all(): List<ServiceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(items: List<ServiceEntity>)
}

@Dao
interface CalendarMetaDao {
    @Query("select * from calendar_blocks order by startsAt") fun observeBlocks(): Flow<List<CalendarBlockEntity>>
    @Query("select * from availability_overrides order by slotStart") fun observeOverrides(): Flow<List<AvailabilityOverrideEntity>>
    @Query("select * from app_state where key='main' limit 1") fun observeState(): Flow<AppStateEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBlocks(items: List<CalendarBlockEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOverrides(items: List<AvailabilityOverrideEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertState(item: AppStateEntity)
    @Query("delete from calendar_blocks") suspend fun clearBlocks()
    @Query("delete from availability_overrides") suspend fun clearOverrides()
}

@Dao
interface MutationDao {
    @Query("select * from pending_mutations order by localId") suspend fun all(): List<PendingMutationEntity>
    @Insert suspend fun add(item: PendingMutationEntity): Long
    @Query("delete from pending_mutations where localId=:id") suspend fun delete(id: Long)
    @Query("update pending_mutations set tries=tries+1 where localId=:id") suspend fun fail(id: Long)
}

@Database(
    entities = [AppointmentEntity::class, ClientEntity::class, ServiceEntity::class, CalendarBlockEntity::class, AvailabilityOverrideEntity::class, AppStateEntity::class, PendingMutationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SlotellyDatabase : RoomDatabase() {
    abstract fun appointments(): AppointmentDao
    abstract fun clients(): ClientDao
    abstract fun services(): ServiceDao
    abstract fun calendarMeta(): CalendarMetaDao
    abstract fun mutations(): MutationDao
}
