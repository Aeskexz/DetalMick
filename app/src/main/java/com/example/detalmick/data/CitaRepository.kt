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
    suspend fun rol(uid: String) = safe { db.child("roles/$uid").get().await() }
    suspend fun pacientes() = opciones("usuarios")
    suspend fun nombrePaciente(uid: String) = safe { db.child("usuarios/$uid/nombre").get().await().value as? String ?: "" }
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

    private fun ocupacion(c: Cita) = mapOf("uid" to c.uid, "id" to c.id)
    suspend fun agendar(c: Cita) = escribir(mapOf("citas/${c.uid}/${c.id}" to c, slot(c) to ocupacion(c)))

    suspend fun cancelar(c: Cita) = escribir(mapOf("citas/${c.uid}/${c.id}/estado" to "cancelada", slot(c) to null))

    suspend fun confirmar(c: Cita) = escribir(mapOf("citas/${c.uid}/${c.id}/asistenciaConfirmada" to true))

    suspend fun reprogramar(c: Cita, fecha: String, horario: String): Result<Unit> {
        val nueva = c.copy(fecha = fecha, horario = horario, slotKey = CitaValidation.slotKey(horario), estado = "reprogramada",
            anio = fecha.take(4).toInt(), mes = fecha.drop(5).take(2).toInt(), dia = fecha.takeLast(2).toInt(),
            inicioEn = CitaValidation.inicioMillis(fecha, horario) ?: return Result.failure(IllegalArgumentException("Fecha inválida")), asistenciaConfirmada = false)
        return escribir(mapOf(
            "citas/${c.uid}/${c.id}" to nueva,
            slot(c) to null,
            slot(nueva) to ocupacion(nueva)))
    }

    suspend fun citasPaciente(uid: String) = safe {
        ordenar(db.child("citas").child(uid).get().await().children.mapNotNull {
            it.getValue(Cita::class.java)?.copy(id = it.key.orEmpty(), uid = uid)
        })
    }

    // Para el dentista. Lee todo "citas" (permitido al personal); en una clínica pequeña basta.
    suspend fun agendaDentista(dentistaId: String, todos: Boolean = false) = safe {
        ordenar(db.child("citas").get().await().children.flatMap { u ->
            u.children.mapNotNull { it.getValue(Cita::class.java)?.copy(id = it.key.orEmpty(), uid = u.key.orEmpty()) }
        }.filter { todos || it.dentistaId == dentistaId })
    }
}
