# Validación técnica · Persona 1

## Entorno

Windows, JDK incluido en Android Studio, Gradle del proyecto, Android Emulator y Firebase Emulator Suite. Proyecto de pruebas `demo-detalmick`, sin cuentas ni datos de producción.

## Resultado de la ejecución · 30 de octubre de 2026

- Compilación del APK debug normal: correcta, con `USE_FIREBASE_EMULATORS = false`.
- Pruebas unitarias del módulo: dos pruebas correctas.
- Prueba Android del ciclo completo de cuenta: una prueba correcta en el emulador Android API 37.
- Pruebas locales de reglas: siete escenarios correctos.
- Android Lint: cero errores. Quedan avisos de versiones más recientes de herramientas/bibliotecas, targetSdk y sobrepintado del fondo; no bloquean la compilación. Las versiones de Firebase se fijaron deliberadamente para compatibilidad con el compilador actual.
- Se extrajeron cinco capturas de las pantallas ejecutadas, con datos sintéticos, en `docs/evidencias/evidence`.
- No se accedió a GitHub ni se publicó código.

Las capturas son evidencia funcional de pruebas locales. `android-flow.txt`, `firebase-rules.txt` y `build-verification.txt` conservan las salidas de verificación. El modo local usa un proyecto Firebase distinto del real.

## Pruebas implementadas

### Validación de campos

`ValidationTest`: correo inválido, espacios en correo, nombre vacío o demasiado largo, contraseña corta, teléfono opcional o inválido y dirección demasiado larga.

### Reglas de seguridad

`tools/firebase-tests/rules.test.mjs`: siete escenarios, con varias operaciones permitidas y denegadas por escenario:

1. Sin sesión: catálogo y clínica públicos; perfiles protegidos.
2. Paciente: lectura y actualización propias; otro paciente y listado completo bloqueados.
3. Esquema: tipos y campos inválidos, escalamiento de privilegios y eliminación del perfil bloqueados.
4. Paciente: modificación de pagos, historial, citas confirmadas y catálogo bloqueada.
5. Personal autorizado: lectura de perfiles y gestión de registros permitidas.
6. Recordatorios: solo marcar como leído un recordatorio propio existente y con tipo booleano.
7. Solicitudes: servicio existente, fecha futura, timestamp de servidor y propiedad del paciente.

### Flujo de interfaz Android

`AccountFlowTest`: registro, confirmación de contraseña incorrecta, guardado inicial, edición del perfil, conservación de campos al recrear la Activity, cierre de sesión, contraseña incorrecta, nuevo inicio, lectura de datos persistidos y solicitud de recuperación de contraseña.

El emulador comprueba la solicitud de recuperación; no entrega correos reales ni verifica la infraestructura de correo de producción.

## Evidencias

Los resultados de Gradle se generan en `app/build/reports/tests`, `app/build/reports/androidTests` y `app/build/reports/lint-results-debug.html`. Las capturas de prueba se conservan en `docs/evidencias` cuando se extraen del dispositivo de pruebas.

La validación técnica no sustituye las pruebas con un mínimo de cinco usuarios que exige la rúbrica. Aún deben registrarse participantes reales, fechas, metodología, tiempos de tareas, observaciones y decisiones. Las capturas de esta entrega muestran la implementación; no se presentan como prototipos previos ni como evidencia de feedback de usuarios.

## Comprobación pendiente en el entorno real

Desde el equipo se comprobó que la ruta pública `clinica` de la base real responde HTTP 200 y que una lectura sin autenticación a una ruta de perfil ficticia se rechaza con HTTP 401. No se leyeron perfiles reales ni se modificaron datos remotos. Esta comprobación no demuestra todas las reglas ni verifica Authentication.

El APK normal abre en la pantalla de acceso. El emulador Android presentó errores DNS al resolver servicios externos; la prueba funcional contra la nube requiere un dispositivo con conexión. No existe una sesión administrativa disponible para verificar el proveedor de correo/contraseña desde Firebase CLI.

Verificar en la consola el proveedor de correo/contraseña y las reglas publicadas. Después probar con una cuenta autorizada el ciclo de registro, acceso, actualización y cierre de sesión en un dispositivo con internet. Las pruebas locales no certifican la configuración de la consola real.
