// Trusted workstation tool. Never import this administrative code into the Android app.
// Password is supplied through a process environment variable, never a source file.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { initializeApp, deleteApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getDatabase } from 'firebase-admin/database';

const require = createRequire(import.meta.url);
const cliAuth = require('firebase-tools/lib/auth');
const { requireAuth } = require('firebase-tools/lib/requireAuth');
const { getAccessToken } = require('firebase-tools/lib/apiv2');
const args = process.argv.slice(2);
const value = flag => args[args.indexOf(flag) + 1];
const project = 'detalmick-a35b2';
const username = value('--username');
if (!username || !/^[a-zA-Z0-9_]{3,40}$/.test(username)) throw new Error('Indica --username con un usuario válido.');
const password = process.env.DETALMICK_DENTIST_PASSWORD;
if (!password || password.length < 6) throw new Error('Falta la contraseña temporal en DETALMICK_DENTIST_PASSWORD.');
const account = cliAuth.getGlobalDefaultAccount();
if (!account) throw new Error('Inicia sesión con firebase login usando la cuenta administradora del proyecto.');
await requireAuth({ ...account, project, nonInteractive: true });
const app = initializeApp({ projectId: project,
  databaseURL: 'https://detalmick-a35b2-default-rtdb.firebaseio.com',
  credential: { getAccessToken: async () => ({ access_token: await getAccessToken(), expires_in: 300 }) }
}, 'dentist-provisioning');
try {
  const auth = getAuth(app);
  const database = getDatabase(app);
  const email = `${username.toLowerCase()}@dentistas.detalmick.invalid`;
  let user;
  try {
    user = await auth.getUserByEmail(email);
    const existingRole = await database.ref(`roles/${user.uid}/personal`).get();
    if (existingRole.val() !== true) throw new Error('Ya existe una cuenta sin autorización con ese identificador. No se elevarán permisos de una cuenta preexistente.');
  }
  catch (error) {
    if (error.code !== 'auth/user-not-found') throw error;
    user = await auth.createUser({ email, password, displayName: username });
  }
  const profile = await database.ref(`dentistas/${user.uid}`).get();
  const updates = { [`roles/${user.uid}/personal`]: true };
  if (!profile.exists()) updates[`dentistas/${user.uid}`] = { nombre: username, telefono: '', direccion: '' };
  await database.ref().update(updates);
  if (args.includes('--publish-rules')) {
    const rules = (await readFile(new URL('../../database.rules.json', import.meta.url), 'utf8')).replace(/^\uFEFF/, '');
    await database.setRules(rules);
  }
  console.log(JSON.stringify({ project, username, uid: user.uid, dentistAuthorized: true, rulesPublished: args.includes('--publish-rules') }));
} finally { await deleteApp(app); }
