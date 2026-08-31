package ru.slotelly.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="appointments")
data class AppointmentEntity(@PrimaryKey val id:String,val clientId:String?,val clientName:String,val startsAt:String,val endsAt:String,val status:String,val servicesJson:String="[]",val paymentsJson:String="[]",val pending:Boolean=false,val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="clients")
data class ClientEntity(@PrimaryKey val id:String,val name:String,val phone:String="",val messenger:String="")
@Entity(tableName="services")
data class ServiceEntity(@PrimaryKey val id:String,val name:String,val price:Double,val duration:Int,val active:Boolean=true)
@Entity(tableName="pending_mutations")
data class PendingMutationEntity(@PrimaryKey(autoGenerate=true) val localId:Long=0,val action:String,val payloadJson:String,val createdAt:Long=System.currentTimeMillis(),val tries:Int=0)

data class ApiEnvelope(val p_pin:String,val p_action:String,val p_data:Map<String,Any?> = emptyMap())
data class SnapshotDto(val services:List<Map<String,Any?>>?=null,val clients:List<Map<String,Any?>>?=null,val calendar:Map<String,Any?>?=null,val from:String?=null,val to:String?=null)
