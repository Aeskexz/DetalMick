// Workstation only: reuse the Firebase CLI session; never package credentials in Android.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { initializeApp } from 'firebase-admin/app';

const require = createRequire(import.meta.url);
export async function configuredAdmin(name) {
  const config = JSON.parse(await readFile(new URL('../../app/google-services.json', import.meta.url), 'utf8'));
  const { project_id: projectId, firebase_url: databaseURL } = config.project_info;
  if (!projectId || !databaseURL) throw new Error('Falta el proyecto o la URL de Realtime Database en app/google-services.json.');
  const account = require('firebase-tools/lib/auth').getGlobalDefaultAccount();
  if (!account) throw new Error('Inicia sesión con firebase login desde esta estación.');
  await require('firebase-tools/lib/requireAuth').requireAuth({ ...account, project: projectId, nonInteractive: true });
  return initializeApp({ projectId, databaseURL, credential: {
    getAccessToken: async () => ({ access_token: await require('firebase-tools/lib/apiv2').getAccessToken(), expires_in: 300 })
  } }, name);
}
