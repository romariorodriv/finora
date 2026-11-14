import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const slugs = [
  'mis-finanzas','suscripciones','calculadoras','aprende',
  'como-funciona','seguridad','guia-control-de-gastos',
  'calculadora-gastos-hormiga','calculadora-presupuesto',
  'calculadora-suscripciones',
  'acerca-de-sarela', 'guia-donde-se-va-mi-dinero', 'guia-presupuesto-sueldo', 'guia-gastos-hormiga', 'guia-revisar-suscripciones', 'guia-fecha-corte-pago', 'guia-fin-de-mes'
];
const sitemap = readFileSync(resolve(root, 'public/sitemap.xml'),'utf8');
const landing = readFileSync(resolve(root, 'src/app/landing/landing.component.html'),'utf8');
const deploy = readFileSync(resolve(root,'scripts/publish-google-frontend.ps1'),'utf8');
const problems=[];
for(const slug of slugs){
  const name=slug+'.html';
  const path=resolve(root,'public',name);
  if(!existsSync(path)){problems.push('Missing '+name);continue;}
  const html=readFileSync(path,'utf8');
  if(!html.includes('<h1')||!html.includes('name="description"')||!html.includes('rel="canonical"')){
    problems.push('Missing SEO metadata '+name);
  }
  const canonical='https://sarelaapp.com/'+name;
  if(!html.includes(canonical))problems.push('Canonical missing '+name);
  if(!sitemap.includes('<loc>'+canonical+'</loc>'))problems.push('Sitemap missing '+name);
  if(!deploy.includes(name))problems.push('Deployment script missing '+name);
}
for(const slug of ['mis-finanzas','suscripciones','calculadoras','aprende']){
  if(!landing.includes('/'+slug+'.html'))problems.push('Landing navigation missing '+slug);
}

const publicLandingLinks = [
  '/mis-finanzas.html','/suscripciones.html','/calculadoras.html','/aprende.html',
  '/como-funciona.html','/guia-donde-se-va-mi-dinero.html','/calculadora-gastos-hormiga.html'
];
for (const target of publicLandingLinks) {
  if (!landing.includes('href="'+target+'"')) problems.push('Landing link missing '+target);
}
for (const id of ['productos','como-funciona','aprende']) {
  if (!landing.includes('id="'+id+'"')) problems.push('Landing anchor missing #'+id);
}
for (const marker of ['landing-process-grid','landing-learn-grid','landing-learn-card','landing-card-link']) {
  if (!landing.includes(marker)) problems.push('Landing content missing '+marker);
}
for (const image of ['images/sarela-calm-desk-640.webp','images/sarela-emerald-sculpture-640.webp']) {
  if (!landing.includes(image) || !existsSync(resolve(root,'public',image))) problems.push('Landing image missing '+image);
}
const staticInternalLinks = new Set(['/','/#registro']);
for (const slug of slugs) staticInternalLinks.add('/'+slug+'.html');
for (const legal of ['privacidad.html','terminos.html','eliminar-datos.html']) staticInternalLinks.add('/'+legal);
for (const slug of slugs) {
  const html=readFileSync(resolve(root,'public',slug+'.html'),'utf8');
  for (const match of html.matchAll(/href="(\/[^"#?]*)(?:[#?][^"]*)?"/g)) {
    const target=match[1];
    if (!target || target==='/') continue;
    if (!staticInternalLinks.has(target) && !existsSync(resolve(root,'public',target.replace(/^\//,'')))) {
      problems.push('Broken internal link '+slug+'.html -> '+target);
    }
  }
}

if(!readFileSync(resolve(root,'src/index.html'),'utf8').includes('application/ld+json'))problems.push('Organization structured data missing');
if(problems.length){console.error(problems.join('\n'));process.exit(1);}
console.log('SEO static pages, canonical URLs, sitemap and deploy manifest: PASS');
