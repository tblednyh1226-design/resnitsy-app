package ru.slotelly.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface AppointmentDao {
    @Query("select * from appointments where status != 'cancelled' order by startsAt") fun observeActive():Flow<List<AppointmentEntity>>
    @Query("select * from appointments order by startsAt") suspend fun all():List<AppointmentEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(items:List<AppointmentEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(item:AppointmentEntity)
    @Query("update appointments set status=:status,pending=1,updatedAt=:now where id=:id") suspend fun setStatus(id:String,status:String,now:Long=System.currentTimeMillis())
    @Query("update appointments set pending=0 where id=:id") suspend fun clearPending(id:String)
}
@Dao interface ClientDao { @Query("select * from clients order by name") fun observeAll():Flow<List<ClientEntity>>; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(items:List<ClientEntity>) }
@Dao interface ServiceDao { @Query("select * from services where active=1 order by name") fun observeActive():Flow<List<ServiceEntity>>; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(items:List<ServiceEntity>) }
@Dao interface MutationDao { @Query("select * from pending_mutations order by localId") suspend fun all():List<PendingMutationEntity>; @Insert suspend fun add(item:PendingMutationEntity):Long; @Query("delete from pending_mutations where localId=:id") suspend fun delete(id:Long); @Query("update pending_mutations set tries=tries+1 where localId=:id") suspend fun fail(id:Long) }

@Database(entities=[AppointmentEntity::class,ClientEntity::class,ServiceEntity::class,PendingMutationEntity::class],version=1,exportSchema=false)
abstract class SlotellyDatabase:RoomDatabase(){abstract fun appointments():AppointmentDao;abstract fun clients():ClientDao;abstract fun services():ServiceDao;abstract fun mutations():MutationDao}
