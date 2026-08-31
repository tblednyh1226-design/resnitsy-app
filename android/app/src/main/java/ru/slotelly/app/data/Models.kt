package ru.slotelly.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey val id: String,
    val clientId: String?,
    val clientName: String,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val servicesJson: String = "[]",
    val paymentsJson: String = "[]",
    val comment: String = "",
    val pending: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val messenger: String = "",
    val lastServicesJson: String = "[]"
)

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    val duration: Int,
    val active: Boolean = true,
    val slotType: String = "normal",
    val category: String = ""
)

data class ServiceSelection(
    val service: ServiceEntity,
    val price: Double = service.price,
    val duration: Int = service.duration
)

@Entity(tableName = "calendar_blocks")
data class CalendarBlockEntity(
    @PrimaryKey val id: String,
    val startsAt: String,
    val endsAt: String,
    val label: String = "",
    val source: String = ""
)

@Entity(tableName = "availability_overrides")
data class AvailabilityOverrideEntity(
    @PrimaryKey val id: String,
    val slotStart: String,
    val available: Boolean,
    val reason: String = "",
    val pending: Boolean = false
)

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String = "main",
    val settingsJson: String = "{}",
    val syncedAt: Long = 0L
)

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val action: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tries: Int = 0
)

data class ApiEnvelope(
    val p_pin: String,
    val p_action: String,
    val p_data: Map<String, Any?> = emptyMap()
)
