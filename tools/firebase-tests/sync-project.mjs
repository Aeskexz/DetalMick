// Checks by default. --apply publishes tested rules and initializes missing public catalog entries.
// Does not reset passwords, grant roles, or migrate clinical records between projects.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { deleteApp } from 'firebase-admin/app';
import { getDatabase } from 'firebase-admin/database';
import { getAuth } from 'firebase-admin/auth';
import { configuredAdmin } from './admin-session.mjs';

const app = await configuredAdmin('project-sync');
try {
  const db = getDatabase(app);
  const [roles, profiles, currentRules] = await Promise.all([
    db.ref('roles').get(), db.ref('dentistas').get(), db.getRules()
  ]);
  const rules = await readFile(new URL('../../database.rules.json', import.meta.url), 'utf8');
  JSON.parse(rules);
  const admins = [];
  const roleEntries = []; roles.forEach(entry => { roleEntries.push(entry); });
  const profileEntries = []; profiles.forEach(entry => { profileEntries.push(entry); });
  for (const entry of roleEntries) {
    if (entry.child('admin').val() === true && entry.child('activo').val() !== false) {
      const user = await getAuth(app).getUser(entry.key);
      admins.push({ email: user.email, disabled: user.disabled, profileReady: !!profiles.child(entry.key).child('nombre').val() });
    }
  }
  const apply = process.argv.includes('--apply');
  if (apply) {
    const folder = new URL('../../tmp/', import.meta.url);
    await mkdir(folder, { recursive: true });
    await writeFile(new URL(`firebase-rules-before-${Date.now()}.json`, folder), currentRules);
    await db.setRules(rules);
    await db.ref('clinica/nombre').transaction(value => value ?? 'DetalMick');
    // Preserve an existing catalog, including the clinic's chosen service identifiers.
    await db.ref('servicios').transaction(value => value ?? {
      consulta: { nombre: 'Consulta general' }, limpieza: { nombre: 'Limpieza dental' }
    });
    for (const profile of profileEntries) {
      const role = roles.child(profile.key);
      const name = profile.child('nombre').val();
      const specialty = profile.child('especialidad').val();
      if (role.child('personal').val() === true && role.child('activo').val() !== false && name && specialty) {
        await db.ref(`clinica/dentistas/${profile.key}`).transaction(value => value ?? { nombre: name, especialidad: specialty });
      }
    }
  }
  const published = await db.getRules();
  console.log(JSON.stringify({ project: app.options.projectId, databaseURL: app.options.databaseURL,
    applied: apply, rulesMatch: JSON.stringify(JSON.parse(published)) === JSON.stringify(JSON.parse(rules)), admins }, null, 2));
} finally { await deleteApp(app); }
