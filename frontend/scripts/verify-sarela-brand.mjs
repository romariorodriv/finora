import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const build = fileURLToPath(new URL('../dist/frontend/browser/', import.meta.url));
const pages = ['index.html', 'privacidad.html', 'terminos.html', 'eliminar-datos.html'];
for (const name of pages) {
  const html = await readFile(resolve(build, name), 'utf8');
  if (/\\bFinora\\b|\\bFINORA\\b|>\\s*finora\\s*</.test(html)) {
    throw new Error('Old brand found in visible or metadata HTML: ' + name);
  }
  if (name !== 'index.html') {
    if (!html.includes('href="/sarela-icon.svg"') || !html.includes('href="/sarela-legal.css"')) {
      throw new Error('Legal page has outdated asset references: ' + name);
    }
    if (!html.includes('name="sarela-publication-status" content="operator-reviewed"')) {
      throw new Error('Legal approval marker missing in: ' + name);
    }
  }
}
const index = await readFile(resolve(build, 'index.html'), 'utf8');
if (!index.includes('<title>Sarela |') || !index.includes('href="sarela-icon.svg"')) {
  throw new Error('Sarela title or favicon missing in main index');
}
const assets = await readFile(resolve(build, 'sarela-icon.svg'), 'utf8');
if (!assets.includes('<svg')) throw new Error('Sarela favicon missing');
for (const file of ['sarela-legal.css']) await readFile(resolve(build, file));
console.log('PASS: Sarela brand, landing title, favicon, legal pages and asset references');
