import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const slugs = [
  'mis-finanzas','suscripciones','calculadoras','aprende',
  'como-funciona','seguridad','guia-control-de-gastos',
  'calculadora-gastos-hormiga','calculadora-presupuesto',
  'calculadora-suscripciones'
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
if(!readFileSync(resolve(root,'src/index.html'),'utf8').includes('application/ld+json'))problems.push('Organization structured data missing');
if(problems.length){console.error(problems.join('\n'));process.exit(1);}
console.log('SEO static pages, canonical URLs, sitemap and deploy manifest: PASS');
