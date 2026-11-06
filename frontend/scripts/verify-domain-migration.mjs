import { readFile, readdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../dist/frontend/browser/', import.meta.url));
const expectedFrontend = 'https://sarelaapp.com';
const expectedApi = 'https://api.sarelaapp.com/api/v1';
const forbidden = ['api-sarela.pgoapp.com', 'sarela.pgoapp.com'];
const paths = [];
async function visit(dir) {
  for (const item of await readdir(dir, { withFileTypes: true })) {
    const absolute = join(dir, item.name);
    if (item.isDirectory()) await visit(absolute);
    else if (['.html','.js','.css','.xml','.txt'].some(extension => item.name.toLowerCase().endsWith(extension))) paths.push(absolute);
  }
}
await visit(root);
if (!paths.length) throw new Error('Build output is missing. Run npm run build first.');
let foundApi = false;
for (const path of paths) {
  const body = await readFile(path, 'utf8');
  if (forbidden.some(domain => body.toLowerCase().includes(domain))) throw new Error('Legacy domain found in build asset: ' + path);
  if (body.includes(expectedApi)) foundApi = true;
}
if (!foundApi) throw new Error('Production API base URL was not included in compiled JS.');
for (const page of ['privacidad.html', 'terminos.html', 'eliminar-datos.html']) {
  const html = await readFile(resolve(root, page), 'utf8');
  if (!html.includes('href="' + expectedFrontend + '/' + page + '"')) throw new Error('Incorrect canonical: ' + page);
  if (!html.includes('finora-publication-status" content="operator-reviewed"')) throw new Error('Unreviewed legal page: ' + page);
}
const index = await readFile(resolve(root, 'index.html'), 'utf8');
if (!index.includes('rel="canonical" href="' + expectedFrontend + '/"')) throw new Error('Incorrect landing canonical URL');
const robots = await readFile(resolve(root, 'robots.txt'), 'utf8');
if (!robots.includes(expectedFrontend + '/sitemap.xml')) throw new Error('Missing canonical sitemap reference');
const sitemap = await readFile(resolve(root, 'sitemap.xml'), 'utf8');
if (!sitemap.includes(expectedFrontend + '/privacidad.html')) throw new Error('Invalid sitemap');
console.log('PASS: API domain, canonical frontend, static SEO, legal status and no legacy URLs in production build.');
