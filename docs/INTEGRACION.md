# Contrato de integración · Persona 1

## Arquitectura

`DetalMickApplication` inicializa `FirebaseProvider`. `AccountRepository` encapsula Authentication y las operaciones del perfil. `AccountViewModel` mantiene la sesión de pantalla durante recreaciones de la Activity y evita envíos repetidos mientras hay una operación en curso. `MainActivity` renderiza las vistas XML mediante View Binding.

No almacenar contraseñas en Realtime Database, preferencias, logs ni estados guardados. El correo de acceso proviene de Firebase Authentication, no de un campo de perfil editable. El teléfono no se emplea como mecanismo de autenticación.

## Rutas comunes

| Ruta | Contenido | Escritura desde el cliente |
|---|---|---|
| `usuarios/{uid}` | `nombre`, `telefono`, `direccion` | Solo el titular; no se permite borrar el perfil completo |
| `roles/{uid}/personal` | Booleano administrativo | Denegada, incluso al personal; se administra desde entorno confiable |
| `servicios/{servicioId}` | Catálogo público | Personal autorizado |
| `clinica` | Información pública de contacto | Personal autorizado |
| `solicitudesCitas/{uid}/{id}` | `servicioId`, `fechaSolicitada`, `creadoEn` | Titular con perfil; solo creación válida |
| `citas/{uid}/{id}` | Citas confirmadas | Personal autorizado; reserva automática por implementar |
| `tratamientos/{uid}/{id}` | Historial y programación | Personal autorizado |
| `pagos/{uid}/{id}` | Saldos y estados | Personal autorizado |
| `recordatorios/{uid}/{id}` | Recordatorios | Personal; titular puede cambiar `leido` de un recordatorio existente |

`uid` siempre es el identificador de Authentication, nunca un correo ni un número de teléfono. Pacientes leen únicamente sus subárboles; no deben consultar la raíz de `usuarios`, `pagos` o `citas` para filtrarlos en Android. Las reglas no funcionan como filtros.

Ejemplo de perfil, sin credenciales:

```json
{
  "nombre": "Nombre del paciente",
  "telefono": "+503 7123-4567",
  "direccion": "San Salvador"
}
```

## Responsabilidades de los siguientes módulos

- **Persona 2:** servicio y horario, reserva, cancelación, reprogramación y agenda del personal. Una solicitud no equivale a una cita confirmada. Antes de habilitar reservas directas deberá incorporar un bloqueo transaccional del horario y reglas comprobadas para evitar duplicados. Las reglas actuales no conceden a pacientes modificación directa de citas confirmadas.
- **Persona 3:** tratamientos y pagos; definir sus campos y validaciones antes de ampliar el esquema. Mantener saldos y estados bajo control del personal o de lógica confiable. No permitir que el paciente marque una deuda como pagada.
- **Persona 4:** recordatorios y contacto; registrar estados reales. Abrir WhatsApp para conversar no equivale a enviar recordatorios automáticos.
- **Persona 1:** revisar cada integración, la compilación y las pruebas de acceso al incorporar esos módulos.

Usar `FirebaseProvider.auth.currentUser?.uid` para identificar al titular y `FirebaseProvider.database` para acceder a sus rutas. Centralizar operaciones nuevas en repositorios de cada módulo. No inicializar otro proyecto Firebase ni habilitar persistencia en disco unilateralmente. La configuración local de pruebas ya está centralizada.

Desde el inicio, agregar navegación real al módulo cuando exista. Por ahora, el panel de próximos módulos es informativo. Las consultas del personal se autorizan en reglas; ocultar un botón no constituye seguridad.

## Límites y comportamiento del módulo actual

- Nombre obligatorio, hasta 120 caracteres. Teléfono y dirección opcionales.
- Teléfono: de 8 a 15 dígitos y signos de formato; dirección: hasta 300 caracteres.
- Registro: correo válido, contraseña de 8 a 128 caracteres y confirmación coincidente. Firebase puede aplicar una política adicional configurada por el administrador.
- El perfil se confirma cuando Firebase reconoce la escritura. Después de 15 segundos sin confirmación, se informa que podría completarse al recuperar conexión; no se muestra un éxito anticipado.
- Si Authentication crea la cuenta y falla el perfil, se conserva la sesión y se permite completar los datos. El usuario no tiene que crear otra cuenta.
- Al cerrar sesión se descartan escrituras locales no confirmadas y se vacía el estado de la interfaz. El SDK gestiona las credenciales de sesión. No se habilitó persistencia de Realtime Database en disco.
- Las reglas de citas, pagos y tratamientos restringen quién escribe; la validación detallada de sus campos pertenece a sus módulos pendientes.

## Tareas de esta entrega para registrar en Kanban

| ID sugerido | Tarea sustancial | Responsable | Evidencia |
|---|---|---|---|
| DM-01 | Corregir dependencias y restaurar la compilación | Persona 1 | Catálogo, Gradle y compilación |
| DM-02 | Implementar registro, validación y recuperación del perfil incompleto | Persona 1 | Prueba Android |
| DM-03 | Implementar acceso, recuperación de contraseña y cierre de sesión | Persona 1 | Prueba Android |
| DM-04 | Implementar consulta y actualización de datos personales | Persona 1 | Prueba de persistencia y recreación |
| DM-05 | Definir y comprobar aislamiento de datos y permisos | Persona 1 | Reglas y siete pruebas locales |
| DM-06 | Integrar inicio, documentar contratos y generar APK | Persona 1 | APK y documentación |

Estas filas son una propuesta para el tablero real. No representan historial retrospectivo de sprints ni commits. Registrar fechas, estados y responsables reales en la herramienta que elija el equipo.
