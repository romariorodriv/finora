import assert from 'node:assert/strict';
import fs from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'parse5';
import { parseTemplate } from '@angular/compiler';
const root=fileURLToPath(new URL('../',import.meta.url));
const getTs=lang=>{const s=fs.readFileSync(resolve(root,`src/app/core/i18n/${lang}.ts`),'utf8');return JSON.parse(s.slice(s.indexOf('{',s.indexOf('export const')),s.lastIndexOf('}')+1));};
const es=getTs('es'),en=getTs('en');
const slots=value=>(value.match(/\{\d+\}/g)||[]).sort();
assert.deepEqual(Object.keys(es).sort(),Object.keys(en).sort(),'UI language keys must match');
for(const key of Object.keys(es)){assert(en[key].length>0,key);assert.deepEqual(slots(es[key]),slots(en[key]),`Placeholders: ${key}`);}
const ps=JSON.parse(fs.readFileSync(resolve(root,'i18n/public.es.json'))),pe=JSON.parse(fs.readFileSync(resolve(root,'i18n/public.en.json')));
assert.deepEqual(Object.keys(ps).sort(),Object.keys(pe).sort(),'Public language keys must match');
for(const file of ['app.component.html','admin/admin.component.html','landing/landing.component.html']){
 const source=fs.readFileSync(resolve(root,'src/app',file),'utf8');const tree=parseTemplate(source,file,{preserveWhitespaces:true});assert(!tree.errors?.length,JSON.stringify(tree.errors));
 function walk(nodes){for(const node of nodes){if(node.constructor.name==='Text')assert(!/[a-záéíóúñ]/i.test(node.value),`Unlocalized template copy: ${file}: ${node.value}`);for(const a of node.attributes??[])if(['aria-label','placeholder','title','alt'].includes(a.name))assert(!/[a-záéíóúñ]/i.test(a.value),`Unlocalized attribute: ${file}: ${a.value}`);walk(node.children??[]);for(const branch of node.branches??[])walk(branch.children??[]);}}
 walk(tree.nodes);
 for(const match of source.matchAll(/(?:lang\.t\(|\{\{\s*)'((?:\\.|[^'])*)'\s*(?:\)|\|\s*t)/g)){
  const key=match[1].replace(/\\'/g,"'").replace(/\\\\/g,'\\');assert(key in es,`Missing UI key ${file}: ${key}`);
 }
}
const built=resolve(root,'dist/frontend/browser');
for(const file of fs.readdirSync(resolve(root,'public')).filter(f=>f.endsWith('.html'))){
 const english=fs.readFileSync(resolve(built,'en-'+file),'utf8'),spanish=fs.readFileSync(resolve(built,file),'utf8');
 assert(english.includes('<html lang="en">'),file+': English lang');
 assert(english.includes('https://sarelaapp.com/en-'+file),file+': English canonical');
 for(const html of [english,spanish]){assert(html.includes('hreflang="es"')&&html.includes('hreflang="en"'),file+': reciprocal alternatives');assert(html.includes('public-language-switcher'),file+': no-JS language selector');}
 const findText=html=>{const text=[];function walk(node){if(node.nodeName==='#text'&&!['script','style'].includes(node.parentNode?.tagName))text.push(node.value.replace(/\s+/g,' ').trim());for(const c of node.childNodes??[])walk(c)}walk(parse(html));return text};
 const englishText=findText(english);
 for(const source of findText(spanish))if(pe[source]&&pe[source]!==source)assert(!englishText.includes(source),file+': Spanish copy left in English: '+source);
 assert.equal((english.match(/<h1\b/g)||[]).length,1,file+': H1');
}
const home=fs.readFileSync(resolve(built,'en.html'),'utf8');assert(home.includes('Where did')&&home.includes('lang="en"'),'English home must be prerendered');
assert(home.includes('rel="canonical" href="https://sarelaapp.com/en.html"'),'English home canonical');
assert(!/class="[^"]*dashboard-view/.test(home),'No private English snapshot');
const sitemap=fs.readFileSync(resolve(built,'sitemap.xml'),'utf8');assert(sitemap.includes('/en.html</loc>')&&sitemap.includes('/en-privacidad.html</loc>'),'English URLs in built sitemap');
console.log(`PASS: ${Object.keys(es).length} UI keys, ${Object.keys(pe).length} public keys, parameter parity, template coverage, bilingual static documents and SEO.`);
