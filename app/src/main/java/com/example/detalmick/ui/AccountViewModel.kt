package com.example.detalmick.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.detalmick.data.AccountRepository
import com.example.detalmick.data.PatientProfile
import com.example.detalmick.domain.Validation
import com.example.detalmick.domain.AccessPortal
import com.example.detalmick.domain.DentistIdentity
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeoutException

enum class Screen { LOGIN, REGISTER, RESET, HOME, PROFILE }

data class AccountState(
    val screen: Screen = Screen.LOGIN,
    val busy: Boolean = false,
    val message: String = "",
    val error: Boolean = false,
    val connected: Boolean = false,
    val profile: PatientProfile? = null,
    val profileSaved: Boolean = false,
    val staff: Boolean = false,
    val admin: Boolean = false,
    val reception: Boolean = false,
    val mustChangePassword: Boolean = false,
    val email: String = "",
    val portal: AccessPortal = AccessPortal.PATIENT
)

class AccountViewModel : ViewModel() {
    private val repository = AccountRepository()
    private val mutable = MutableLiveData(AccountState())
    val state: LiveData<AccountState> = mutable
    private var generation = 0
    private val current get() = mutable.value!!
    private fun update(value: AccountState) { mutable.value = value }

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            update(current.copy(connected = snapshot.value == true))
        }
        override fun onCancelled(error: DatabaseError) {
            update(current.copy(connected = false))
        }
    }

    init {
        repository.connection.addValueEventListener(connectionListener)
        if (repository.auth.currentUser != null) authorizeAndLoad(restore = true)
    }

    fun selectPortal(portal: AccessPortal) {
        if (current.busy || repository.auth.currentUser != null) return
        if (current.portal == portal) return
        update(current.copy(portal = portal, screen = Screen.LOGIN, message = "", error = false))
    }

    fun navigate(screen: Screen) {
        if (current.busy) return
        if (repository.auth.currentUser == null && screen in listOf(Screen.HOME, Screen.PROFILE)) return
        if (current.mustChangePassword && screen != Screen.PROFILE) return
        update(current.copy(screen = screen, message = "", error = false))
    }

    fun login(email: String, password: String) {
        if (current.busy) return
        val raw = email.trim()
        val address = if (Validation.email(raw)) raw else DentistIdentity.email(raw)
        if (address == null || password.isEmpty()) {
            fail("Escribe tu correo o usuario y tu contraseña."); return
        }
        val operation = start()
        repository.login(address, password).addOnCompleteListener {
            if (operation != generation) return@addOnCompleteListener
            if (it.isSuccessful) authorizeAndLoad() else fail(errorMessage(it.exception))
        }
    }

    fun register(name: String, email: String, password: String, confirmation: String) {
        if (current.busy) return
        if (!Validation.name(name)) { fail("Escribe tu nombre (máximo 120 caracteres)."); return }
        if (!Validation.email(email.trim())) { fail("Escribe un correo electrónico válido."); return }
        if (DentistIdentity.isUsernameAccount(email.trim())) { fail("Usa tu correo personal para crear una cuenta de paciente."); return }
        if (!Validation.password(password)) { fail("Usa una contraseña de 8 a 128 caracteres."); return }
        if (password != confirmation) { fail("Las contraseñas no coinciden."); return }
        val operation = start()
        repository.register(email.trim(), password).addOnCompleteListener { result ->
            if (operation != generation) return@addOnCompleteListener
            if (!result.isSuccessful) { fail(errorMessage(result.exception)); return@addOnCompleteListener }
            val profile = PatientProfile(nombre = name.trim())
            update(current.copy(screen = Screen.PROFILE, profile = profile,
                email = repository.auth.currentUser?.email.orEmpty(), busy = true))
            repository.save(result.result.user!!.uid, profile).addOnCompleteListener saveComplete@ { saved ->
                if (operation != generation) return@saveComplete
                update(current.copy(busy = false, screen = if (saved.isSuccessful) Screen.HOME else Screen.PROFILE,
                    profileSaved = saved.isSuccessful,
                    error = !saved.isSuccessful, message = if (saved.isSuccessful)
                        "Tu cuenta fue creada. Ya puedes completar tu perfil."
                    else "Tu cuenta se creó, pero no se confirmó el guardado del perfil. Completa tus datos y vuelve a guardar."))
            }
        }
    }

    fun loadProfile() {
        if (current.busy) return
        authorizeAndLoad()
    }

    private fun authorizeAndLoad(restore: Boolean = false) {
        val user = repository.auth.currentUser ?: return
        val operation = start()
        repository.role(user.uid).addOnCompleteListener roleComplete@ { role ->
            if (operation != generation) return@roleComplete
            if (!role.isSuccessful) {
                rejectAccess("No pudimos verificar los permisos de tu cuenta. Revisa tu conexión e intenta de nuevo.")
                return@roleComplete
            }
            if (role.result.child("activo").value == false) {
                rejectAccess("Esta cuenta ha sido deshabilitada por la administración. Comunícate con la clínica.")
                return@roleComplete
            }
            val reception = role.result.child("recepcionista").value == true
            val isStaff = role.result.child("personal").value == true || reception
            val isAdmin = role.result.child("admin").value == true
            val mustChangePassword = (isStaff || isAdmin) && role.result.child("primerIngreso").value == true
            val portal = when {
                isAdmin -> AccessPortal.ADMIN
                isStaff -> AccessPortal.DENTIST
                else -> AccessPortal.PATIENT
            }
            update(current.copy(staff = isStaff || isAdmin, admin = isAdmin, reception = reception,
                mustChangePassword = mustChangePassword, portal = portal, email = user.email.orEmpty(),
                screen = if (mustChangePassword) Screen.PROFILE else Screen.HOME,
                message = if (mustChangePassword) "Cambia tu contraseña temporal para continuar." else ""))
            readAuthorizedProfile(user.uid, operation, isStaff || isAdmin)
        }
    }

    private fun rejectAccess(message: String) {
        val portal = current.portal
        generation++
        repository.logout()
        update(AccountState(portal = portal, connected = current.connected, message = message, error = true))
    }

    private fun readAuthorizedProfile(uid: String, operation: Int, staff: Boolean) {
        repository.profile(uid, staff).addOnCompleteListener {
            if (operation != generation) return@addOnCompleteListener
            if (it.isSuccessful) {
                val profile = try {
                    PatientProfile.fromValue(it.result.value)
                } catch (_: IllegalArgumentException) {
                    update(current.copy(profile = null, profileSaved = false))
                    fail("Tu perfil contiene datos que no se pueden mostrar. Comunícate con la clínica para revisarlos.")
                    return@addOnCompleteListener
                }
                val fallback = if (staff) PatientProfile(repository.auth.currentUser?.email?.substringBefore('@').orEmpty()) else PatientProfile()
                val needsProfile = profile == null || profile.nombre.isBlank() || (staff && !current.admin && !current.reception && (profile.nombre == "Dr/Dra. Invitado" || profile.especialidad.isBlank()))
                val mustChangePassword = current.mustChangePassword
                update(current.copy(busy = false,
                    screen = if (needsProfile || mustChangePassword) Screen.PROFILE else Screen.HOME,
                    profile = profile ?: fallback, profileSaved = profile != null && !needsProfile,
                    message = when {
                        mustChangePassword -> "Cambia tu contraseña temporal para continuar."
                        needsProfile && staff -> "¡Bienvenido! Completa tus datos y tu especialidad para activar tu cuenta."
                        needsProfile -> "Completa tu nombre para terminar el registro."
                        else -> ""
                    }))
            } else {
                fail("No pudimos cargar tu perfil. Revisa tu conexión e intenta de nuevo. Si continúa, verifica los permisos de la base de datos.")
            }
        }
    }

    fun save(name: String, phone: String, address: String, especialidad: String = "", newPassword: String = "") {
        if (current.busy) return
        if (!Validation.name(name)) { fail("Escribe tu nombre (máximo 120 caracteres)."); return }
        if (!Validation.phone(phone.trim())) { fail("Escribe un teléfono de 8 a 15 dígitos, o deja el campo vacío."); return }
        if (!Validation.address(address.trim())) { fail("La dirección admite hasta 300 caracteres."); return }
        if (current.staff && especialidad.trim().length > 120) { fail("La especialidad admite hasta 120 caracteres."); return }
        if (current.mustChangePassword && newPassword.isBlank()) {
            fail("Debes establecer una contraseña nueva para continuar."); return
        }
        if (newPassword.isNotBlank() && !Validation.password(newPassword)) { fail("La nueva contraseña debe tener entre 8 y 128 caracteres."); return }

        val user = repository.auth.currentUser ?: return
        val uid = user.uid
        val profile = PatientProfile(name.trim(), phone.trim(), address.trim(), especialidad.trim())
        val operation = start()
        val mustChangePassword = current.mustChangePassword

        fun saveProfile() {
            repository.save(uid, profile, current.staff, !current.reception && !current.admin).addOnCompleteListener profileComplete@ { saved ->
                if (operation != generation) return@profileComplete
                if (!saved.isSuccessful) {
                    fail(if (saved.exception is TimeoutException)
                        "No se confirmó el guardado del perfil. Revisa la conexión e intenta de nuevo."
                        else "No se pudo guardar el perfil. Verifica los permisos de la base de datos e intenta de nuevo.")
                    return@profileComplete
                }
                if (!mustChangePassword) {
                    update(current.copy(busy = false, screen = Screen.HOME, profile = profile,
                        profileSaved = true, error = false, message = "Tus datos se guardaron correctamente."))
                    return@profileComplete
                }
                repository.completeFirstLogin(uid).addOnCompleteListener roleComplete@ { completed ->
                    if (operation != generation) return@roleComplete
                    if (completed.isSuccessful) update(current.copy(busy = false, screen = Screen.HOME,
                        mustChangePassword = false, profile = profile, profileSaved = true,
                        error = false, message = "Contraseña y perfil actualizados. Ya puedes continuar."))
                    else fail("Se guardaron tus datos, pero no se pudo confirmar el cambio obligatorio. Verifica las reglas de Firebase e intenta guardar de nuevo.")
                }
            }
        }

        if (newPassword.isNotBlank()) {
            user.updatePassword(newPassword).addOnCompleteListener passwordComplete@ { changed ->
                if (operation != generation) return@passwordComplete
                if (changed.isSuccessful) saveProfile()
                else fail("No se pudo cambiar la contraseña. Debe tener entre 8 y 128 caracteres; vuelve a iniciar sesión si expiró la sesión.")
            }
        } else saveProfile()
    }

    fun reset(email: String) {
        if (current.busy) return
        val raw = email.trim()
        if (DentistIdentity.isUsernameAccount(raw)) {
            fail("Para recuperar una cuenta de dentista con nombre de usuario, comunícate con la administración."); return
        }
        if (!Validation.email(raw)) { fail("Escribe un correo electrónico válido."); return }
        val operation = start()
        repository.reset(raw).addOnCompleteListener {
            if (operation != generation) return@addOnCompleteListener
            if (it.isSuccessful) update(current.copy(busy = false, error = false,
                message = "Si existe una cuenta con ese correo, recibirás instrucciones. Revisa también la carpeta de spam."))
            else fail(errorMessage(it.exception))
        }
    }

    fun logout() {
        generation++
        repository.logout()
        update(AccountState(connected = current.connected, message = "Cerraste tu sesión."))
    }

    private fun start(): Int {
        generation++
        update(current.copy(busy = true, message = "", error = false))
        return generation
    }
    private fun fail(message: String) { update(current.copy(busy = false, error = true, message = message)) }

    private fun errorMessage(error: Exception?): String = when (error) {
        is FirebaseNetworkException -> "No se pudo conectar. Revisa tu conexión a internet e intenta de nuevo."
        is FirebaseTooManyRequestsException -> "Hubo demasiados intentos. Espera unos minutos e intenta de nuevo."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_CONFIGURATION_NOT_FOUND" -> "El acceso con correo aún no está habilitado en Firebase Authentication."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya tiene una cuenta. Inicia sesión o recupera tu contraseña."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple la política de seguridad. Prueba con una más larga que incluya letras, números y símbolos."
            "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada. Comunícate con la clínica."
            else -> "No pudimos acceder. Revisa el correo y la contraseña, o recupera tu acceso."
        }
        else -> "No pudimos completar la operación. Intenta de nuevo."
    }

    override fun onCleared() {
        generation++
        repository.connection.removeEventListener(connectionListener)
        super.onCleared()
    }
}
