import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
const publicCheck = process.argv.includes('--public');
const build = fileURLToPath(new URL('../dist/frontend/browser/', import.meta.url));
const domain = 'https://sarelaapp.com';
const pages = [
 ['privacidad.html', 'Privacidad de Finora'],
 ['terminos.html', 'Términos de uso de Finora'],
 ['eliminar-datos.html', 'Desconexión y eliminación de datos']
];
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
for (const [name, heading] of pages) {
 const bytes = await readFile(resolve(build, name));
 const html = bytes.toString('utf8');
 if (!html.includes(`<h1>${heading}</h1>`) || !html.includes(`<title>${heading} | Finora</title>`)) throw new Error(`Wrong legal document: ${name}`);
 if (html.includes('<app-root') || /<script[\s>]/i.test(html)) throw new Error(`Legal page is not standalone: ${name}`);
 for (const target of ['privacidad.html','terminos.html','eliminar-datos.html','finora-legal.css','finora.svg']) {
  if (!html.includes(`href="/${target}"`)) throw new Error(`Missing link or asset ${target} in ${name}`);
  await readFile(resolve(build,target));
 }
 if (!html.includes(`href="${domain}/${name}"`)) throw new Error(`Wrong canonical URL: ${name}`);
 const reviewed = html.includes('name="finora-publication-status" content="operator-reviewed"');
 const draftCopy = /Borrador para revisión|No publicar todavía|Este es un borrador|Borrador revisado/i.test(html);
 if (!reviewed || draftCopy) throw new Error(`Legal page is still marked as draft or lacks operator review: ${name}`);
 console.log(`${name}: standalone document reviewed by operator; publication markers OK`);
 if (publicCheck) {
  // No cookie jar, Authorization header or authenticated session.
  const response = await fetch(`${domain}/${name}`, { redirect: 'manual', signal: AbortSignal.timeout(25000) });
  if (response.status !== 200 || !response.headers.get('content-type')?.includes('text/html')) throw new Error(`Public page ${name}: HTTP ${response.status} or wrong MIME`);
  const published = Buffer.from(await response.arrayBuffer());
  if (hash(published) !== hash(bytes)) throw new Error(`Public ${name} differs from reviewed build (SPA fallback, stale deployment or another version)`);
  console.log(`${domain}/${name}: HTTP 200, no session, exact reviewed content`);
 }
}
