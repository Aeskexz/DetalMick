const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getDatabase } = require("firebase-admin/database");
const { onCall, HttpsError } = require("firebase-functions/v2/https");

initializeApp();

exports.deleteDentist = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Inicia sesión como administrador.");
  }

  const db = getDatabase();
  const callerRole = await db.ref(`roles/${request.auth.uid}`).get();
  if (callerRole.child("admin").val() !== true) {
    throw new HttpsError("permission-denied", "Solo un administrador puede eliminar cuentas.");
  }

  const uid = request.data && request.data.uid;
  if (typeof uid !== "string" || uid.length === 0 || uid === request.auth.uid) {
    throw new HttpsError("invalid-argument", "El dentista seleccionado no es válido.");
  }

  const targetRole = await db.ref(`roles/${uid}`).get();
  if (targetRole.child("personal").val() !== true || targetRole.child("admin").val() === true) {
    throw new HttpsError("failed-precondition", "Solo se pueden eliminar cuentas de dentistas no administradores.");
  }

  const auth = getAuth();
  try {
    await auth.getUser(uid);
    await auth.deleteUser(uid);
  } catch (error) {
    if (error.code !== "auth/user-not-found") {
      console.error("deleteDentist auth deletion failed", error);
      throw new HttpsError("internal", "No se pudo eliminar la cuenta de Authentication.");
    }
  }

  try {
    await db.ref().update({
      [`dentistas/${uid}`]: null,
      [`roles/${uid}`]: null
    });
    return { deleted: true };
  } catch (error) {
    console.error("deleteDentist database cleanup failed", error);
    throw new HttpsError("internal", "La cuenta de Authentication fue eliminada, pero no se pudieron borrar sus datos de Realtime Database.");
  }
});
