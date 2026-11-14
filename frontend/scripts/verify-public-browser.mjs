import { createServer } from 'node:http';
import { readFile, mkdtemp, rm, access } from 'node:fs/promises';
import { resolve, extname, sep, join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root=resolve(fileURLToPath(new URL('../dist/frontend/browser/',import.meta.url)));
const paths=[
  {path:'/calculadora-presupuesto.html',expect:'600.00'},
  {path:'/calculadora-gastos-hormiga.html',expect:'260.00'},
  {path:'/calculadora-suscripciones.html',expect:'74.00'},
  {path:'/aprende.html',expect:''},
  {path:'/mis-finanzas.html',expect:''},
  {path:'/guia-donde-se-va-mi-dinero.html',expect:''}
];
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.webp':'image/webp','.png':'image/png','.svg':'image/svg+xml'};
const auditInjection='<script>' + [
"window.addEventListener('load',()=>{",
"const result=document.getElementById('results');",
"const report={width:window.innerWidth,scroll:document.documentElement.scrollWidth,title:document.title,h1:document.querySelectorAll('h1').length,lime:getComputedStyle(document.documentElement).getPropertyValue('--lime').trim(),learn:!!document.querySelector('a[href=\"/aprende.html\"]'),calculators:!!document.querySelector('a[href=\"/calculadoras.html\"]'),result:result?result.textContent.replace(/\\s+/g,' ').trim():'',fields:document.querySelectorAll('#calc input').length};",
"const node=document.createElement('pre');node.id='sarela-audit-dom-result';node.textContent=JSON.stringify(report);document.body.append(node);",
"});"
].join('')+'</script>';
const server=createServer(async(req,res)=>{
  try{
    const url=new URL(req.url,'http://127.0.0.1');
    const decoded=decodeURIComponent(url.pathname);
    const target=resolve(root,'.'+(decoded==='/'?'/index.html':decoded));
    if(target!==root&&!target.startsWith(root+sep)){res.writeHead(403);res.end();return;}
    let bytes=await readFile(target);
    if(extname(target)==='.html'&&url.searchParams.has('audit')){
      bytes=Buffer.from(bytes.toString('utf8').replace('</body>',auditInjection+'</body>'),'utf8');
    }
    res.writeHead(200,{'Content-Type':types[extname(target)]||'application/octet-stream','Cache-Control':'no-store'});
    res.end(bytes);
  }catch{res.writeHead(404);res.end('Not found');}
});
function chromeCandidates(){
  const pf=process.env.PROGRAMFILES||'C:\\Program Files';
  const pf86=process.env['PROGRAMFILES(X86)']||'C:\\Program Files (x86)';
  return [process.env.CHROME_BIN,'/usr/bin/google-chrome','/usr/bin/google-chrome-stable','/usr/bin/chromium','/usr/bin/chromium-browser',join(pf,'Google','Chrome','Application','chrome.exe'),join(pf86,'Google','Chrome','Application','chrome.exe')].filter(Boolean);
}
async function findChrome(){
  for(const p of chromeCandidates()){try{await access(p);return p;}catch{}}
  throw Error('Chrome not found. Set CHROME_BIN for browser QA.');
}
function dump(chrome,url,profile,width){
  const args=['--headless=new','--no-sandbox','--no-proxy-server','--disable-gpu','--disable-dev-shm-usage','--disable-extensions','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--user-data-dir='+profile,'--window-size='+width+',900','--virtual-time-budget=3500','--dump-dom',url];
  return new Promise((ok,fail)=>{
    const p=spawn(chrome,args,{windowsHide:true,stdio:['ignore','pipe','pipe']});
    let out='',err='';
    const t=setTimeout(()=>{p.kill();fail(Error('Chrome timed out'))},40000);
    p.stdout.on('data',b=>{out+=b;if(out.length>1200000)p.kill();});
    p.stderr.on('data',b=>{err=(err+b).slice(-1500)});
    p.on('error',e=>{clearTimeout(t);fail(e)});
    p.on('close',code=>{clearTimeout(t);if(code!==0)fail(Error('Chrome exited '+code+': '+err));else ok(out)});
  });
}
const problems=[];
let profile;
try{
  const chrome=await findChrome();
  profile=await mkdtemp(join(tmpdir(),'sarela-public-browser-qa-'));
  await new Promise(resolveListen=>server.listen(0,'127.0.0.1',resolveListen));
  for(const item of paths){
    for(const width of (item.expect?[390,1280]:[390])){
      const url='http://127.0.0.1:'+server.address().port+item.path+'?audit=1';
      const html=await dump(chrome,url,profile,width);
      const result=html.match(/<pre id="sarela-audit-dom-result">([\s\S]*?)<\/pre>/i);
      if(!result){problems.push(item.path+': no browser runtime report');continue;}
      let data;
      try{data=JSON.parse(result[1].replace(/&quot;/g,'"').replace(/&amp;/g,'&'));}catch{problems.push(item.path+': invalid runtime report');continue;}
      if(data.h1!==1)problems.push(item.path+': wrong H1 count');
      if(!data.learn||!data.calculators)problems.push(item.path+': missing public navigation');
      if(data.lime!=='#d4fa60')problems.push(item.path+': landing palette not used');
      if(data.scroll>data.width+3)problems.push(item.path+': horizontal overflow '+data.scroll+'/'+data.width);
      if(item.expect&&(!data.result.includes(item.expect)||data.fields===0))problems.push(item.path+': calculator returned incorrect initial result '+data.result);
      console.log('QA '+item.path+' viewport='+data.width+' scroll='+data.scroll+' result='+data.result.slice(0,80));
    }
  }
  if(problems.length)throw Error(problems.join('\n'));
  console.log('PASS: all public Chrome calculator, SEO navigation, palette and responsive smoke checks.');
}finally{
  await new Promise(resolveClose=>server.close(resolveClose));
  if(profile)await rm(profile,{recursive:true,force:true});
}
