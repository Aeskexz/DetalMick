package com.example.detalmick.data

import com.example.detalmick.domain.CitaValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class CitaRepository {
    private val db get() = FirebaseProvider.database.reference

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(15_000) { block() })
    } catch (e: TimeoutCancellationException) { Result.failure(e)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { Result.failure(e) }

    // Escritura atómica multi-ruta. Si falla, descarta lo pendiente para que no se guarde "después".
    private suspend fun escribir(cambios: Map<String, Any?>): Result<Unit> {
        val r = safe { db.updateChildren(cambios).await(); Unit }
        if (r.isFailure) FirebaseProvider.database.purgeOutstandingWrites()
        return r
    }

    private fun slot(c: Cita) = "agenda/${c.dentistaId}/${c.fecha}/${CitaValidation.slotKey(c.horario)}"
    private fun ordenar(l: List<Cita>) = l.sortedWith(compareBy({ it.fecha }, { it.horario }))

    suspend fun servicios() = opciones("servicios")
    suspend fun dentistas() = opciones("clinica/dentistas")
    private suspend fun opciones(ruta: String) = safe {
        db.child(ruta).get().await().children.mapNotNull { n ->
            val nombre = n.child("nombre").getValue(String::class.java)
            val id = n.key
            if (id != null && !nombre.isNullOrBlank()) Opcion(id, nombre) else null
        }
    }

    suspend fun ocupados(dentistaId: String, fecha: String) = safe {
        db.child("agenda/$dentistaId/$fecha").get().await().children.mapNotNull { it.key }.toSet()
    }

    suspend fun agendar(c: Cita) = escribir(mapOf("citas/${c.uid}/${c.id}" to c, slot(c) to c.uid))

    suspend fun cancelar(c: Cita) = escribir(mapOf("citas/${c.uid}/${c.id}/estado" to "cancelada", slot(c) to null))

    suspend fun reprogramar(c: Cita, fecha: String, horario: String): Result<Unit> {
        val nueva = c.copy(fecha = fecha, horario = horario)
        return escribir(mapOf(
            "citas/${c.uid}/${c.id}/fecha" to fecha,
            "citas/${c.uid}/${c.id}/horario" to horario,
            "citas/${c.uid}/${c.id}/estado" to "reprogramada",
            slot(c) to null,
            slot(nueva) to c.uid))
    }

    suspend fun citasPaciente(uid: String) = safe {
        ordenar(db.child("citas").child(uid).get().await().children.mapNotNull {
            it.getValue(Cita::class.java)?.copy(id = it.key.orEmpty(), uid = uid)
        })
    }

    // Para el dentista. Lee todo "citas" (permitido al personal); en una clínica pequeña basta.
    suspend fun agendaDentista(dentistaId: String) = safe {
        ordenar(db.child("citas").get().await().children.flatMap { u ->
            u.children.mapNotNull { it.getValue(Cita::class.java)?.copy(id = it.key.orEmpty(), uid = u.key.orEmpty()) }
        }.filter { it.dentistaId == dentistaId })
    }
}