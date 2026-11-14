import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const build = fileURLToPath(new URL('../dist/frontend/browser/', import.meta.url));
const index = readFileSync(resolve(build, 'index.html'), 'utf8');
const failures = [];
const root = index.match(/<app-root\b[^>]*>([\s\S]*?)<\/app-root>/i)?.[1] || '';
const checks = [
  ['prerender marker', index.includes('data-sarela-prerender="anonymous-landing"')],
  ['landing rendered inside root', /<app-landing\b/i.test(root)],
  ['hero heading rendered', /<h1\b[^>]*id="hero-title"/i.test(root)],
  ['products section', /id="productos"/i.test(root)],
  ['how it works section', /id="como-funciona"/i.test(root)],
  ['learning section', /id="aprende"/i.test(root)],
  ['Mis Finanzas link', /href="\/mis-finanzas\.html"/i.test(root)],
  ['Suscripciones link', /href="\/suscripciones\.html"/i.test(root)],
  ['Calculadoras link', /href="\/calculadoras\.html"/i.test(root)],
  ['Aprende link', /href="\/aprende\.html"/i.test(root)],
  ['Angular client bundle remains available', /<script\b[^>]*src="main-[^"]+\.js"/i.test(index)],
  ['anonymous-only snapshot', !/<app-admin\b|class="[^"]*\bgoogle-access\b|class="[^"]*\bdashboard-view\b|__Host-/i.test(root)],
  ['no private transfer state', !/<ng-state\b|<script\b/i.test(root)],
  ['component styles in initial HTML', /<style\b[^>]*>[\s\S]*?<\/style>/i.test(index)]
];
for (const [name, ok] of checks) if (!ok) failures.push(name);
for (const file of ['index.html', 'mis-finanzas.html','suscripciones.html','calculadoras.html','aprende.html','como-funciona.html','seguridad.html','guia-control-de-gastos.html']) {
  const html = readFileSync(resolve(build,file), 'utf8');
  if (!/<h1\b/i.test(html)) failures.push('H1 missing in '+file);
  if (!/rel="canonical"/i.test(html)) failures.push('Canonical missing in '+file);
}
const robots = readFileSync(resolve(build,'robots.txt'),'utf8');
if (!robots.includes('Sitemap: https://sarelaapp.com/sitemap.xml')) failures.push('sitemap not declared');
const cssAssets = readdirSync(build).filter(x=>x.endsWith('.css'));
if (!cssAssets.length) failures.push('CSS file missing');
if (failures.length) {
  console.error('Prerender verification FAILED:\n- ' + failures.join('\n- '));
  process.exit(1);
}
console.log('PASS: initial home HTML includes all public sections and links without JS; all static pages remain indexable; private views excluded.');
