/** Deterministic localization of standalone pages; never translate data entered into calculators. */
import { readFileSync, writeFileSync, readdirSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse, serialize } from 'parse5';
import ts from 'typescript';
const root = fileURLToPath(new URL('../', import.meta.url));
const built = resolve(root, 'dist/frontend/browser');
const dictionary = JSON.parse(readFileSync(resolve(root, 'i18n/public.en.json'), 'utf8'));
const norm = text => text.replace(/\s+/g, ' ').trim();
const translate = text => dictionary[norm(text)] ? text.match(/^\s*/)[0] + dictionary[norm(text)] + text.match(/\s*$/)[0] : text;
const pages = readdirSync(resolve(root, 'public')).filter(f => f.endsWith('.html'));
const localPath = path => path === '/' ? '/en.html' : pages.includes(path.slice(1)) ? '/en-' + path.slice(1) : path;
function url(value) {
  if (value.startsWith('https://sarelaapp.com/')) return 'https://sarelaapp.com' + localPath(value.slice('https://sarelaapp.com'.length));
  const match = value.match(/^(\/[^?#]*)(.*)$/);
  return match ? localPath(match[1]) + match[2] : value;
}
function json(value) {
  if (typeof value === 'string') return translate(url(value));
  if (Array.isArray(value)) return value.map(json);
  if (value && typeof value === 'object') {
    const translated = Object.fromEntries(Object.entries(value).map(([k,v]) => [k, k === 'inLanguage' ? 'en' : json(v)]));
    if (['WebSite','WebPage','FAQPage','Article','WebApplication','CollectionPage'].includes(value['@type'])) translated.inLanguage = 'en';
    return translated;
  }
  return value;
}
function script(source) {
  const file = ts.createSourceFile('calculator.js', source, ts.ScriptTarget.Latest, true), edits = [];
  function visit(node) {
    if (ts.isStringLiteral(node)) {
      const translated = node.text === 'es-PE' ? 'en-US' : translate(node.text);
      if (translated !== node.text) edits.push([node.getStart(file), node.end, JSON.stringify(translated)]);
    }
    ts.forEachChild(node, visit);
  }
  visit(file);
  for (const [a,b,text] of edits.sort((a,b) => b[0]-a[0])) source = source.slice(0,a) + text + source.slice(b);
  return source;
}
function walk(node) {
  if (node.tagName === 'html') node.attrs.find(a => a.name === 'lang').value = 'en';
  if (node.nodeName === '#text' && !['script','style'].includes(node.parentNode?.tagName)) {
    const key = norm(node.value);
    if (dictionary[key]) node.value = node.value.match(/^\s*/)[0] + dictionary[key] + node.value.match(/\s*$/)[0];
  }
  for (const attribute of node.attrs ?? []) {
    if (['href','content'].includes(attribute.name)) attribute.value = url(attribute.value);
    if (['alt','title','aria-label','placeholder'].includes(attribute.name) || attribute.name === 'content') attribute.value = translate(attribute.value);
    if (attribute.name === 'hreflang') { /* alternates are replaced after localization */ }
  }
  if (node.tagName === 'script' && node.childNodes?.length) {
    const child = node.childNodes[0];
    child.value = node.attrs?.some(a => a.name === 'type' && a.value === 'application/ld+json')
      ? JSON.stringify(json(JSON.parse(child.value))) : script(child.value);
  } else for (const child of node.childNodes ?? []) walk(child);
}
for (const page of pages) {
  const original = readFileSync(resolve(root, 'public', page), 'utf8');
  const english = parse(original); walk(english);
  const esUrl = 'https://sarelaapp.com/' + page, enUrl = 'https://sarelaapp.com/en-' + page;
  const alternates = `<link rel="alternate" hreflang="es" href="${esUrl}"><link rel="alternate" hreflang="en" href="${enUrl}"><link rel="alternate" hreflang="x-default" href="${esUrl}">`;
  const decorate = (html, isEnglish) => html.replace('</head>', alternates + `<meta property="og:locale" content="${isEnglish?'en_US':'es_PE'}">` + '<link rel="stylesheet" href="/language-switcher.css"><script src="/public-language.js" defer></script></head>')
    .replace('</body>', `<nav class="public-language-switcher" aria-label="${isEnglish?'Language':'Idioma'}"><a href="/${page}?lang=es" lang="es" aria-label="Español" aria-current="${!isEnglish}">ES</a><a href="/en-${page}?lang=en" lang="en" aria-label="English" aria-current="${isEnglish}">EN</a></nav></body>`);
  writeFileSync(resolve(built, page), decorate(original, false));
  writeFileSync(resolve(built, 'en-' + page), decorate(serialize(english), true));
}
// Both language pages remain distinct, crawlable documents with reciprocal alternatives.
let sitemap = readFileSync(resolve(built, 'sitemap.xml'), 'utf8');
const additions = ['en.html', ...pages.filter(p => p !== '404.html').map(p => 'en-' + p)].map(p => `<url><loc>https://sarelaapp.com/${p}</loc></url>`).join('');
sitemap = sitemap.replace('</urlset>', additions + '</urlset>');
writeFileSync(resolve(built, 'sitemap.xml'), sitemap);
console.log(`PASS: localized ${pages.length} standalone pages, calculator scripts and sitemap.`);
