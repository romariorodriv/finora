import { TestBed } from '@angular/core/testing';
import { provideRouter, UrlTree } from '@angular/router';
import { of, throwError, firstValueFrom, isObservable } from 'rxjs';
import { adminGuard } from './admin.guard';
import { AuthService } from '../core/services/auth.service';
import { AdminComponent } from './admin.component';
import { AdminService, AdminCategory, AdminTx, AdminRule, AdminBatch } from './admin.service';
const category:AdminCategory={id:1,name:'Categoría QA',description:'Descripción',icon:'grid',color:'#187650',macroGroup:'OTROS',active:true,version:0,transactionCount:1};
const tx:AdminTx={id:7,accountId:3,date:'2026-10-30',merchant:'Comercio QA',normalizedMerchant:'comercio qa',description:'Consumo de prueba',amount:10,currency:'USD',categoryId:1,category:'Categoría QA',classificationSource:'AUTOMATIC',reviewStatus:'PENDING',source:'MANUAL'};
const rule:AdminRule={id:1,merchant:'comercio qa',matchType:'EXACT',scope:'GLOBAL',userId:null,categoryId:1,priority:100,active:true,version:0};
const batch:AdminBatch={id:'preview-qa',kind:'RULE_APPLY',status:'PREVIEW',count:1,skippedManual:1,targetCategoryId:1,ruleId:1,expiresAt:'2099-01-01T00:00:00Z',examples:[tx],totals:[{currency:'USD',total:10,count:1}]};
function apiMock(){
 const api=jasmine.createSpyObj<AdminService>('AdminService',['options','categories','transactions','rules','batches','history','saveCategory','saveRule','correct','previewRule','previewReassign','applyBatch','revertBatch']);
 const page=<T>(items:T[])=>({items,totalElements:items.length,totalPages:1,page:0});
 api.options.and.returnValue(of([category]));api.categories.and.returnValue(of(page([category])));api.transactions.and.returnValue(of({page:page([tx]),totals:[{currency:'USD',total:10,count:1}]}));api.rules.and.returnValue(of(page([rule])));api.batches.and.returnValue(of(page([{...batch,status:'APPLIED',changedCount:1}])));api.history.and.returnValue(of(page([])));api.saveCategory.and.returnValue(of(category));api.saveRule.and.returnValue(of(rule));api.correct.and.returnValue(of({...batch,kind:'MANUAL_CORRECTION',status:'APPLIED'}));api.previewRule.and.returnValue(of(batch));api.previewReassign.and.returnValue(of(batch));api.applyBatch.and.returnValue(of({...batch,status:'APPLIED'}));api.revertBatch.and.returnValue(of({...batch,status:'REVERTED'}));return api;
}
describe('Admin route authorization',()=>{
 for(const role of ['USER','ADMIN','FAIL'] as const)it(`checks server role ${role} rather than cached ADMIN`,async()=>{
  const auth={user:{role:'ADMIN'},refreshCurrent:jasmine.createSpy().and.returnValue(role==='FAIL'?throwError(()=>new Error('offline')):of({role}))};
  TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AuthService,useValue:auth}]});
  const result=TestBed.runInInjectionContext(()=>adminGuard({} as never,{} as never));const decision=isObservable(result)?await firstValueFrom(result):await result;
  if(role==='ADMIN')expect(decision).toBeTrue();else expect(decision instanceof UrlTree).toBeTrue();
 });
 it('requires login for direct /admin access',()=>{TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AuthService,useValue:{user:null}}]});const result=TestBed.runInInjectionContext(()=>adminGuard({} as never,{} as never));expect(result instanceof UrlTree).toBeTrue();});
});
describe('Admin workflows',()=>{
 let api:ReturnType<typeof apiMock>;
 beforeEach(()=>{api=apiMock();TestBed.configureTestingModule({imports:[AdminComponent],providers:[{provide:AdminService,useValue:api}]});});
 it('lists real API category counts and requires confirmation before editing',()=>{const fixture=TestBed.createComponent(AdminComponent);fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Categoría QA');const component=fixture.componentInstance;component.editCategory(category);component.saveCategory();expect(api.saveCategory).not.toHaveBeenCalled();component.confirm();expect(api.saveCategory).toHaveBeenCalledWith(component.categoryForm,1);});
 it('filters consumptions on the server and separates a manual correction from rule creation',()=>{const fixture=TestBed.createComponent(AdminComponent);fixture.detectChanges();const c=fixture.componentInstance;c.openCategory(category);expect(api.transactions).toHaveBeenCalledWith(jasmine.objectContaining({categoryId:1}),0);c.toggle(7,true);c.bulkCategoryId=1;c.correct();expect(api.correct).not.toHaveBeenCalled();c.confirm();expect(api.correct).toHaveBeenCalledWith([7],1);expect(api.saveRule).not.toHaveBeenCalled();c.createRuleFromCorrection();expect(c.ruleForm.scope).toBe('USER');expect(c.ruleForm.userId).toBe(3);});
 it('offers an explicit global rule confirmation without applying the history',()=>{const fixture=TestBed.createComponent(AdminComponent);fixture.detectChanges();const c=fixture.componentInstance;c.editRule(rule);c.saveRule();expect(c.confirmation?.title).toContain('global');expect(api.saveRule).not.toHaveBeenCalled();c.confirm();expect(api.saveRule).toHaveBeenCalled();expect(api.applyBatch).not.toHaveBeenCalled();});
 it('previews before historical application and supports cancelling without a write',()=>{const c=TestBed.createComponent(AdminComponent).componentInstance;c.startPreview(rule);c.generatePreview();expect(c.preview?.count).toBe(1);c.applyPreview();expect(api.applyBatch).not.toHaveBeenCalled();c.cancelPreview();expect(api.applyBatch).not.toHaveBeenCalled();c.startPreview(rule);c.generatePreview();c.confirmedScope=true;c.applyPreview();expect(api.applyBatch).toHaveBeenCalledWith('preview-qa');});
 it('shows server conflicts and does not claim success on a rejected reversal',()=>{api.revertBatch.and.returnValue(throwError(()=>({error:{message:'Hay cambios posteriores'}})));const c=TestBed.createComponent(AdminComponent).componentInstance;c.revert({...batch,status:'APPLIED'});c.confirm();expect(c.error).toBe('Hay cambios posteriores');expect(c.success).toBe('');});
 for(const width of [1440,1024,768,390])it(`fits the four tabs, forms and preview within ${width}px`,async()=>{
  const fixture=TestBed.createComponent(AdminComponent);fixture.detectChanges();const c=fixture.componentInstance;
  const frame=document.createElement('iframe');frame.title='Admin layout QA';frame.style.cssText=`position:fixed;top:0;left:0;width:${width}px;height:1000px;border:0;visibility:hidden`;document.body.appendChild(frame);
  try{const doc=frame.contentDocument!;doc.open();doc.write('<!doctype html><html><head></head><body></body></html>');doc.close();doc.body.appendChild(fixture.nativeElement);
   for(const tab of ['categories','transactions','rules','history'] as const){c.go(tab);if(tab==='categories')c.editCategory(category);if(tab==='rules')c.editRule(rule);fixture.detectChanges();await fixture.whenStable();doc.head.replaceChildren();for(const sheet of Array.from(document.styleSheets)){const style=doc.createElement('style');style.textContent=Array.from(sheet.cssRules).map(r=>r.cssText).join('\n');doc.head.appendChild(style);}expect(doc.documentElement.scrollWidth).withContext(`${tab}, ${width}`).toBeLessThanOrEqual(width+1);}
   c.preview=batch;fixture.detectChanges();await fixture.whenStable();expect(doc.querySelector('[role="dialog"]')).not.toBeNull();expect(doc.documentElement.scrollWidth).toBeLessThanOrEqual(width+1);
  }finally{fixture.destroy();frame.remove();}
 });
});
