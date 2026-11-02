package com.example.detalmick.ui

import androidx.lifecycle.*
import com.example.detalmick.data.*
import com.example.detalmick.domain.CitaValidation
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.util.UUID

data class Aviso(val texto: String, val error: Boolean)

data class CitasState(
    val loading: Boolean = false,
    val servicios: List<Opcion> = emptyList(),
    val dentistas: List<Opcion> = emptyList(),
    val catalogoCargado: Boolean = false,
    val ocupados: Set<String> = emptySet(),
    val citas: List<Cita> = emptyList(),
    val citasCargadas: Boolean = false,
    val errorCarga: String? = null,
    val aviso: Aviso? = null,
    val operacionOk: Boolean = false
)

class CitaViewModel : ViewModel() {
    private val repo = CitaRepository()
    private val _state = MutableLiveData(CitasState())
    val state: LiveData<CitasState> = _state
    private val s get() = _state.value!!
    private var staff = false
    private fun uid() = FirebaseProvider.auth.currentUser?.uid

    fun limpiarAviso() { _state.value = s.copy(aviso = null, operacionOk = false) }

    private fun mensaje(e: Throwable, escritura: Boolean = false): String {
        val txt = e.message.orEmpty()
        return when {
            e is TimeoutCancellationException || e is FirebaseNetworkException || e.cause is FirebaseNetworkException ||
                    txt.contains("offline", true) -> "Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo."
            txt.contains("permission", true) ->
                if (escritura) "Ese horario acaba de ser tomado o no tienes permiso. Elige otro." else "No tienes permiso para ver esta información."
            else -> "No se pudo completar la operación. Inténtalo de nuevo."
        }
    }

    fun cargarCitas(esStaff: Boolean) {
        staff = esStaff
        val id = uid() ?: return
        viewModelScope.launch {
            _state.value = s.copy(loading = true, errorCarga = null)
            val r = if (staff) repo.agendaDentista(id) else repo.citasPaciente(id)
            _state.value = r.fold(
                { s.copy(loading = false, citas = it, citasCargadas = true) },
                { s.copy(loading = false, errorCarga = mensaje(it)) })
        }
    }

    fun cargarCatalogo() = viewModelScope.launch {
        _state.value = s.copy(loading = true)
        val sv = repo.servicios(); val dn = repo.dentistas()
        val fallo = sv.exceptionOrNull() ?: dn.exceptionOrNull()
        _state.value = s.copy(
            loading = false,
            servicios = sv.getOrDefault(emptyList()), dentistas = dn.getOrDefault(emptyList()),
            catalogoCargado = fallo == null, aviso = fallo?.let { Aviso(mensaje(it), true) })
    }

    fun cargarOcupados(dentistaId: String, fecha: String) = viewModelScope.launch {
        _state.value = s.copy(loading = true, ocupados = emptySet())
        repo.ocupados(dentistaId, fecha)
            .onSuccess { _state.value = s.copy(loading = false, ocupados = it) }
            .onFailure { _state.value = s.copy(loading = false, aviso = Aviso(mensaje(it), true)) }
    }

    fun agendar(paciente: String, sv: Opcion, dn: Opcion, fecha: String, horario: String) {
        val id = uid() ?: run { aviso("Tu sesión expiró. Vuelve a iniciar sesión."); return }
        if (!CitaValidation.validarServicio(sv.id) || !CitaValidation.validarFechaHorario(fecha, horario)) {
            aviso("Elige un servicio y un horario futuro válido (no domingos)."); return
        }
        val cita = Cita(UUID.randomUUID().toString(), id, paciente, dn.id, dn.nombre, sv.id, sv.nombre, fecha, horario)
        ejecutar("Cita confirmada para el $fecha a las ${horario.take(5)}.", dn.id, fecha) { repo.agendar(cita) }
    }

    fun reprogramar(c: Cita, fecha: String, horario: String) {
        if (!CitaValidation.validarFechaHorario(fecha, horario) || (fecha == c.fecha && horario == c.horario)) {
            aviso("Elige un horario futuro distinto al actual."); return
        }
        ejecutar("Cita reprogramada para el $fecha a las ${horario.take(5)}.", c.dentistaId, fecha) { repo.reprogramar(c, fecha, horario) }
    }

    fun cancelar(c: Cita) = ejecutar("Cita cancelada.", c.dentistaId, c.fecha) { repo.cancelar(c) }

    private fun aviso(t: String) { _state.value = s.copy(aviso = Aviso(t, true)) }

    private fun ejecutar(ok: String, dentistaId: String, fecha: String, op: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            op().onSuccess {
                _state.value = s.copy(loading = false, aviso = Aviso(ok, false), operacionOk = true)
                cargarCitas(staff)
            }.onFailure {
                _state.value = s.copy(loading = false, aviso = Aviso(mensaje(it, escritura = true), true))
                repo.ocupados(dentistaId, fecha).onSuccess { o -> _state.value = s.copy(ocupados = o) }
            }
        }
    }
}