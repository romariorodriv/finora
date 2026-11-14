/**
 * Release gate for the real public-site SEO architecture.
 * Runs without any Google, AWS or analytics credentials.
 */
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { resolve, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const publicRoot=resolve(root,'public');
const canonicalHost='https://sarelaapp.com';
const sitemap=readFileSync(resolve(publicRoot,'sitemap.xml'),'utf8');
const css=readFileSync(resolve(publicRoot,'sarela-seo.css'),'utf8');
const deploy=readFileSync(resolve(root,'scripts/publish-google-frontend.ps1'),'utf8');
const landing=readFileSync(resolve(root,'src/app/landing/landing.component.html'),'utf8');
const index=readFileSync(resolve(root,'src/index.html'),'utf8');
const problems=[];
const files=readdirSync(publicRoot).filter(f=>f.endsWith('.html') && f!=='404.html');
const sitemapPaths=[...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(x=>x[1]);
const intendedPaths=new Set(['/','/index.html',...files.map(x=>'/'+x),'/404.html']);
const titles=new Map();
const metaDesc=new Map();
const socialFiles=files.filter(x=>!['privacidad.html','terminos.html','eliminar-datos.html'].includes(x));
function check(cond,msg){if(!cond)problems.push(msg)}
check(sitemapPaths.length===new Set(sitemapPaths).size,'Sitemap has duplicate URLs');
check(sitemapPaths.every(x=>x.startsWith(canonicalHost+'/')),'Sitemap contains external or noncanonical host');
check(sitemapPaths.length===files.length+1,'Sitemap must include home and every indexable public page exactly once');
check(sitemapPaths.includes(canonicalHost+'/'),'Sitemap must include home');
check(!sitemap.includes('404.html'),'404 must not be in sitemap');
check(css.includes('--lime:#d4fa60') && css.includes('--bg:#fdfcf9') && css.includes('--ink:#10110f'),'Public CSS must match Angular landing identity');
check(css.includes('@media(max-width:680px)'),'Mobile responsive rules missing');
check(index.includes('<title>Sarela |'),'Sarela home title must retain brand');
check(index.includes('og:image'),'Homepage social image missing');
check(landing.includes('/aprende.html')&&landing.includes('/calculadoras.html'),'Landing must link to learning and calculators');
check(landing.includes('/guia-donde-se-va-mi-dinero.html'),'Landing must link to current leading guide');
for(const file of files){
  const path=resolve(publicRoot,file),html=readFileSync(path,'utf8');
  const canonical=canonicalHost+'/'+file;
  const title=html.match(/<title>(.*?)<\/title>/is)?.[1]?.trim();
  const desc=html.match(/<meta\b[^>]*name="description"[^>]*content="([^"]+)"/i)?.[1]?.trim();
  const href=html.match(/<link\b[^>]*rel="canonical"[^>]*href="([^"]+)"/i)?.[1];
  const h1=[...html.matchAll(/<h1\b/gi)].length;
  check(h1===1,file+': expected exactly one H1, got '+h1);
  check(!!title&&title.length>=25&&title.length<=85,file+': unsuitable title');
  check(!!desc&&desc.length>=75&&desc.length<=190,file+': missing or unusually short/long description');
  check(href===canonical,file+': wrong canonical URL');
  check(!/name="robots"[^>]*content="[^"]*noindex/i.test(html),file+': noindex conflicts with sitemap');
  check(sitemapPaths.includes(canonical),file+': not in sitemap');
  check(deploy.includes("'"+file+"'"),file+': not listed in AWS deploy manifest');
  check(html.includes('href="/sarela-seo.css"'),file+': inconsistent CSS asset');
  check(html.includes('href="/aprende.html"')||file==='aprende.html',file+': learning link missing');
  check(html.includes('href="/calculadoras.html"')||file==='calculadoras.html',file+': calculator link missing');
  if(titles.has(title))problems.push('Duplicate title: '+file+' = '+titles.get(title));else titles.set(title,file);
  if(metaDesc.has(desc))problems.push('Duplicate description: '+file+' = '+metaDesc.get(desc));else metaDesc.set(desc,file);
  for(const m of html.matchAll(/href="(\/[^"#?]*)(?:[?#][^"]*)?"/g)){
    const target=m[1]; if(target==='/'||target==='/#registro'||target==='/index.html')continue;
    const asset=resolve(publicRoot,'.'+target);
    check(existsSync(asset),file+': broken public link '+target);
  }
  if(socialFiles.includes(file)){
    check(html.includes('property="og:image"'),file+': no OG image');
    check(html.includes('property="og:url"'),file+': missing OG URL');
    check(html.includes('application/ld+json'),file+': structured data missing');
  }
  for(const data of html.matchAll(/<script\s+type="application\/ld\+json">([\s\S]*?)<\/script>/gi)){
    try { JSON.parse(data[1]); } catch { problems.push(file+': invalid JSON-LD'); }
  }
  if(file.startsWith('guia-')){
    check(html.length>6000,file+': article is too thin for target');
    check(html.includes('class="prose"'),file+': missing readable editorial layout');
    check(html.includes('<h2'),file+': article has no subsections');
  }
  if(file.startsWith('calculadora-')){
    const script=html.match(/<script>([\s\S]*?)<\/script>\s*<\/body>/i)?.[1];
    check(!!script,file+': calculator script missing');
    if(script){try{new Function(script)}catch(e){problems.push(file+': calculator JS syntax '+e.message)}}
    check(html.includes('role="status"')&&html.includes('type="submit"'),file+': calculator accessibility controls missing');
  }
}
check(files.length>=20,'Fewer than 20 indexable public URLs detected');
check(socialFiles.every(x=>!x.includes('404')),'404 erroneously included');
check(readFileSync(resolve(publicRoot,'404.html'),'utf8').includes('noindex'),'404 must be noindex');
if(problems.length){console.error('PUBLIC SEO AUDIT FAILURES ('+problems.length+'):\n'+problems.map(x=>' - '+x).join('\n'));process.exit(1)}
console.log('PASS public SEO audit: '+files.length+' indexable static pages + 1 home; unique metadata; canonicals; sitemap; internal links; brand CSS; JSON-LD; calculator JS syntax; deploy manifest.');
