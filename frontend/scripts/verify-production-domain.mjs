import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const root = fileURLToPath(new URL('../dist/frontend/browser/', import.meta.url));
const expectedApi = 'https://api.sarelaapp.com/api/v1';
const domain = 'https://sarelaapp.com';
const previous = /(?:api-sarela\.pgoapp\.com|sarela\.pgoapp\.com)/i;
const allowed = new Set(['.js', '.html', '.css', '.json', '.xml', '.txt']);
const failures = [];

async function visit(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) { await visit(path); continue; }
    const ext = entry.name.slice(entry.name.lastIndexOf('.'));
    if (!allowed.has(ext)) continue;
    const text = await readFile(path, 'utf8');
    if (previous.test(text)) failures.push('Old domain in build: ' + path);
  }
}
await visit(root);
const index = await readFile(join(root, 'index.html'), 'utf8');
if (!index.includes('<link rel="canonical" href="' + domain + '/">')) failures.push('Missing new canonical homepage URL');
const scripts = (await readdir(root)).filter(name => name.endsWith('.js'));
const js = (await Promise.all(scripts.map(async name => readFile(join(root, name), 'utf8')))).join('\n');
if (!js.includes(expectedApi)) failures.push('Production bundle does not contain the new API URL');
for (const name of ['privacidad.html', 'terminos.html', 'eliminar-datos.html']) {
  const html = await readFile(join(root, name), 'utf8');
  if (!html.includes('href="' + domain + '/' + name + '"')) failures.push('Incorrect legal canonical: ' + name);
  if (!html.includes('name="finora-publication-status" content="operator-reviewed"')) failures.push('Missing reviewed marker: ' + name);
}
const robots = await readFile(join(root, 'robots.txt'), 'utf8');
const sitemap = await readFile(join(root, 'sitemap.xml'), 'utf8');
if (!robots.includes(domain + '/sitemap.xml')) failures.push('Incorrect sitemap URL in robots.txt');
if (!sitemap.includes(domain + '/')) failures.push('Incorrect homepage URL in sitemap.xml');
if (failures.length) { for (const error of failures) console.error('FAIL: ' + error); process.exitCode = 1; }
else console.log('PASS: production bundle, legal canonicals, sitemap, robots and API domain validated');
