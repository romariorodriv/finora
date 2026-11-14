/**
 * Build-time, deterministic public-home prerender for the existing Angular SPA.
 *
 * We deliberately prerender only the anonymous landing. We never serialize
 * the dashboard, a Gmail session, private API responses or admin screens.
 * Chrome uses a fresh temporary profile and the local build; production API
 * DNS is blocked so it cannot fetch an authenticated session during capture.
 *
 * This is a static browser-rendered snapshot, not an Angular SSR server.
 * Angular bootstraps normally afterward to restore signed-in sessions.
 */
import { createServer } from 'node:http';
import { readFile, writeFile, mkdtemp, rm, access } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, resolve, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const build = fileURLToPath(new URL('../dist/frontend/browser/', import.meta.url));
const indexFile = resolve(build, 'index.html');
const html = await readFile(indexFile, 'utf8');
if (!/<app-root><\/app-root>/.test(html)) {
  throw new Error('Expected the untouched Angular browser index; refusing to re-prerender or overwrite an unknown template.');
}

const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.json': 'application/json'
};

const server = createServer(async (req, res) => {
  try {
    const path = new URL(req.url || '/', 'http://127.0.0.1');
    const decoded = decodeURIComponent(path.pathname);
    const absolute = resolve(build, '.' + (decoded === '/' ? '/index.html' : decoded));
    if (absolute !== build && !absolute.startsWith(build + sep)) {
      res.writeHead(403); res.end(); return;
    }
    const contents = await readFile(absolute);
    res.writeHead(200, { 'Content-Type': mime[extname(absolute)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(contents);
  } catch {
    res.writeHead(404); res.end('Not found');
  }
});

function availableBrowserCandidates() {
  const pf = process.env.PROGRAMFILES || 'C:\\Program Files';
  const pf86 = process.env['PROGRAMFILES(X86)'] || 'C:\\Program Files (x86)';
  const local = process.env.LOCALAPPDATA || '';
  return [
    process.env.CHROME_BIN,
    process.env.GOOGLE_CHROME_BIN,
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    join(pf, 'Google', 'Chrome', 'Application', 'chrome.exe'),
    join(pf86, 'Google', 'Chrome', 'Application', 'chrome.exe'),
    ...(local ? [join(local, 'Google', 'Chrome', 'Application', 'chrome.exe')] : [])
  ].filter(Boolean);
}

async function findBrowser() {
  for (const executable of availableBrowserCandidates()) {
    try { await access(executable); return executable; } catch { /* try next */ }
  }
  throw new Error('Chrome/Chromium not found. Install Chrome or set CHROME_BIN to its full path. Prerender is required; do not deploy SPA-only index.');
}

function dumpDom(chrome, url, profile) {
  const args = [
    '--headless=new', '--no-sandbox', '--disable-gpu',
    '--disable-dev-shm-usage', '--disable-extensions',
    '--no-first-run', '--no-default-browser-check',
    '--disable-background-networking', '--disable-sync',
    '--host-resolver-rules=MAP api.sarelaapp.com ~NOTFOUND, MAP api-sarela.pgoapp.com ~NOTFOUND',
    '--user-data-dir=' + profile,
    '--virtual-time-budget=9000',
    '--dump-dom', url
  ];
  return new Promise((resolve, reject) => {
    const child = spawn(chrome, args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '', stderr = '';
    const timeout = setTimeout(() => { child.kill(); reject(new Error('Chrome prerender timed out after 50 seconds. ' + stderr.slice(-600))); }, 50000);
    child.stdout.on('data', x => { stdout += x; if (stdout.length > 12000000) child.kill(); });
    child.stderr.on('data', x => { stderr = (stderr + x).slice(-4000); });
    child.on('error', e => { clearTimeout(timeout); reject(e); });
    child.on('close', code => {
      clearTimeout(timeout);
      if (code !== 0 || stdout.length > 12000000) reject(new Error('Chrome prerender failed: ' + stderr.slice(-700)));
      else resolve(stdout);
    });
  });
}

let profile;
try {
  const chrome = await findBrowser();
  profile = await mkdtemp(join(tmpdir(), 'sarela-prerender-anonymous-'));
  await new Promise((resolveListen, rejectListen) => {
    server.once('error', rejectListen);
    server.listen(0, '127.0.0.1', resolveListen);
  });
  const url = 'http://127.0.0.1:' + server.address().port + '/';
  const rendered = await dumpDom(chrome, url, profile);
  const rootMatch = rendered.match(/<app-root\b[^>]*>([\s\S]*?)<\/app-root>/i);
  if (!rootMatch) throw new Error('Chrome did not render an Angular app-root. Initial DOM: ' + rendered.slice(0, 1500));
  const root = rootMatch[1];
  const required = [
    /<app-landing\b/i,
    /<h1\b/i,
    /id="productos"/,
    /id="como-funciona"/,
    /id="aprende"/,
    /href="\/mis-finanzas\.html"/,
    /href="\/suscripciones\.html"/,
    /href="\/calculadoras\.html"/,
    /href="\/aprende\.html"/
  ];
  for (const r of required) if (!r.test(root)) throw new Error('Anonymous landing missing expected render: ' + r);

  // The build must be anonymous. A private account, Gmail view, admin view,
  // dashboard or user-specific transfer-state is never eligible for snapshot.
  const forbidden = [
    /class="[^"]*\bdashboard-view\b/i,
    /class="[^"]*\bgoogle-access\b/i,
    /class="[^"]*\bsidebar\b/i,
    /<app-admin\b/i,
    /<input\b[^>]*type="password"/i,
    /<script\b/i,
    /<ng-state\b/i,
    /__Host-/i
  ];
  for (const r of forbidden) if (r.test(root)) throw new Error('Private or unexpected content in prerender snapshot: ' + r);

  // Capture component-scoped runtime styles. Angular's CSS is inserted into
  // <head> by its JS bundle; static crawlers need these styles without JS.
  const head = rendered.match(/<head>([\s\S]*?)<\/head>/i)?.[1] || '';
  const styleTags = [...head.matchAll(/<style\b[^>]*>[\s\S]*?<\/style>/gi)]
    .map(x => x[0])
    .filter(x => !/data-sarela-prerender/.test(x));
  if (styleTags.length === 0) throw new Error('Component styles missing from the rendered document.');

  const sanitized = root.replace(/<!--[\s\S]*?-->/g, '');
  const next = html
    .replace('</head>', '  <!-- Static anonymous landing CSS; Angular will take over on bootstrap. -->\n' +
      styleTags.join('\n') + '\n</head>')
    .replace('<app-root></app-root>',
      '<app-root data-sarela-prerender="anonymous-landing">' + sanitized + '</app-root>');

  if (!next.includes('data-sarela-prerender="anonymous-landing"')) throw new Error('Failed to install prerender snapshot.');
  if (next.length > 800000) throw new Error('Prerender index exceeded 800 KB; investigate asset inlining.');
  await writeFile(indexFile, next, 'utf8');
  console.log('PASS: anonymous Sarela home prerendered into index.html; ' + required.length +
    ' landing checks; ' + styleTags.length + ' scoped style tags; ' + next.length + ' bytes. No private views.');
} finally {
  await new Promise(resolveClose => server.close(() => resolveClose()));
  if (profile) await rm(profile, { recursive: true, force: true });
}
