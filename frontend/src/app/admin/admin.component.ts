import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, Subject, finalize, takeUntil } from 'rxjs';
import { IconComponent } from '../shared/icon.component';
import { FocusTrapDirective } from '../shared/focus-trap.directive';
import { AdminService, AdminCategory, AdminRule, AdminTx, AdminPage, AdminFilters, AdminBatch, AdminAudit, CurrencyTotal } from './admin.service';
const emptyPage=<T>():AdminPage<T>=>({items:[],totalElements:0,totalPages:0,page:0});
@Component({selector:'app-admin',imports:[CommonModule,FormsModule,IconComponent,FocusTrapDirective],templateUrl:'./admin.component.html',styleUrl:'./admin.component.css'})
export class AdminComponent implements OnInit,OnDestroy {
 tab:'categories'|'transactions'|'rules'|'history'='categories';loading=false;error='';success='';
 catalog:AdminCategory[]=[];categoryPage=emptyPage<AdminCategory>();txPage=emptyPage<AdminTx>();rulePage=emptyPage<AdminRule>();auditPage=emptyPage<AdminAudit>();batchPage=emptyPage<AdminBatch>();totals:CurrencyTotal[]=[];
 categorySearch='';categoryActive='';ruleSearch='';selected=new Set<number>();bulkCategoryId:number|null=null;
 categoryEditId?:number;ruleEditId?:number;showCategoryForm=false;showRuleForm=false;
 categoryForm={name:'',description:'',icon:'grid',color:'#187650',macroGroup:'OTROS',active:true,version:0};
 ruleForm={merchant:'',matchType:'EXACT',scope:'USER',userId:null as number|null,categoryId:null as number|null,priority:100,active:true,version:0};
 readonly icons=['grid','wallet','rows','mail','message','trend','spark','shield','calendar','repeat','info'];readonly groups=['ALIMENTACION','TRANSPORTE','SALUD','SERVICIOS','SUSCRIPCIONES','OTROS'];
 filters:AdminFilters=this.emptyFilters();preview?:AdminBatch;confirmedScope=false;
 rulePreviewId?:number;reassignSource:number|null=null;reassignTarget:number|null=null;lastCorrected?:AdminTx;
 confirmation?:{title:string;message:string;action:()=>void};private readonly destroy$=new Subject<void>();
 constructor(private readonly api:AdminService){}
 ngOnInit(){this.refreshCatalog();this.loadCategories();}
 ngOnDestroy(){this.destroy$.next();this.destroy$.complete();}
 emptyFilters():AdminFilters{return {q:'',from:'',to:'',categoryId:null,reviewStatus:'',userId:null,uncategorized:false};}
 private run<T>(request:Observable<T>,next:(value:T)=>void){this.loading=true;this.error='';request.pipe(takeUntil(this.destroy$),finalize(()=>this.loading=false)).subscribe({next,error:e=>{this.error=e?.error?.message||'No pudimos completar la operación. Vuelve a intentarlo.';}});}
 refreshCatalog(){this.api.options().pipe(takeUntil(this.destroy$)).subscribe({next:c=>this.catalog=c,error:()=>this.error='No se pudo cargar el catálogo.'});}
 activeCategories(){return this.catalog.filter(c=>c.active);}
 categoryName(id:number|null){return this.catalog.find(c=>c.id===id)?.name||'Sin categoría';}
 go(tab:typeof this.tab){this.tab=tab;this.error='';this.success='';if(tab==='categories')this.loadCategories();if(tab==='transactions')this.loadTransactions();if(tab==='rules')this.loadRules();if(tab==='history')this.loadHistory();}
 loadCategories(page=0){this.run(this.api.categories(this.categorySearch,this.categoryActive,page),p=>this.categoryPage=p);}
 loadTransactions(page=0){this.selected.clear();this.run(this.api.transactions(this.filters,page),r=>{this.txPage=r.page;this.totals=r.totals;});}
 loadRules(page=0){this.run(this.api.rules(this.ruleSearch,page),p=>this.rulePage=p);}
 loadHistory(page=0){this.run(this.api.history(page),p=>this.auditPage=p);this.api.batches(0).pipe(takeUntil(this.destroy$)).subscribe({next:p=>this.batchPage=p,error:()=>this.error='No se pudieron cargar los lotes.'});}
 loadBatches(page:number){this.run(this.api.batches(page),p=>this.batchPage=p);}
 openCategory(c:AdminCategory){this.filters={...this.emptyFilters(),categoryId:c.id};this.go('transactions');}
 pending(){this.filters={...this.emptyFilters(),uncategorized:true};this.go('transactions');}
 editCategory(c?:AdminCategory){this.categoryEditId=c?.id;this.categoryForm=c?{name:c.name,description:c.description||'',icon:c.icon,color:c.color,macroGroup:c.macroGroup,active:c.active,version:c.version}:{name:'',description:'',icon:'grid',color:'#187650',macroGroup:'OTROS',active:true,version:0};this.showCategoryForm=true;}
 saveCategory(){this.confirmation={title:this.categoryEditId?'Guardar cambios de categoría':'Crear categoría',message:this.categoryForm.active?'El catálogo se actualizará en las pantallas de Sarela.':'La categoría quedará desactivada para nuevas clasificaciones. Su historial se conservará.',action:()=>this.run(this.api.saveCategory(this.categoryForm,this.categoryEditId),()=>{this.showCategoryForm=false;this.success='Categoría guardada.';this.refreshCatalog();this.loadCategories();})};}
 editRule(rule?:AdminRule){this.ruleEditId=rule?.id;this.ruleForm=rule?{merchant:rule.merchant,matchType:rule.matchType,scope:rule.scope,userId:rule.userId,categoryId:rule.categoryId,priority:rule.priority,active:rule.active,version:rule.version}:{merchant:'',matchType:'EXACT',scope:'USER',userId:null,categoryId:null,priority:100,active:true,version:0};this.showRuleForm=true;}
 saveRule(){this.confirmation={title:this.ruleForm.scope==='GLOBAL'?'Confirmar regla global':'Guardar regla de cuenta',message:(this.ruleForm.scope==='GLOBAL'?'Esta regla podrá clasificar consumos futuros de todas las cuentas. ':'Solo afectará a la cuenta indicada. ')+'No modificará el historial.',action:()=>this.run(this.api.saveRule(this.ruleForm,this.ruleEditId),()=>{this.showRuleForm=false;this.success='Regla guardada. El historial no se ha modificado.';this.loadRules();})};}
 toggle(id:number,checked:boolean){checked?this.selected.add(id):this.selected.delete(id);}
 correct(){if(!this.selected.size||!this.bulkCategoryId)return;const ids=[...this.selected],category=this.bulkCategoryId;const first=this.txPage.items.find(t=>this.selected.has(t.id));this.confirmation={title:'Corregir solo la selección',message:`Se cambiarán ${ids.length} consumos a «${this.categoryName(category)}». Se conservarán sus descripciones. No se creará ninguna regla.`,action:()=>this.run(this.api.correct(ids,category),()=>{this.lastCorrected=first;this.success='Corrección registrada. Puedes crear una regla por separado si corresponde.';this.loadTransactions(this.txPage.page);this.refreshCatalog();})};}
 createRuleFromCorrection(){if(!this.lastCorrected||!this.bulkCategoryId)return;this.tab='rules';this.editRule();this.ruleForm={...this.ruleForm,merchant:this.lastCorrected.normalizedMerchant,userId:this.lastCorrected.accountId,categoryId:this.bulkCategoryId};this.loadRules();}
 startPreview(rule:AdminRule){this.rulePreviewId=rule.id;this.filters=this.emptyFilters();this.preview=undefined;this.confirmedScope=false;}
 generatePreview(){if(this.rulePreviewId)this.run(this.api.previewRule(this.rulePreviewId,this.filters),p=>{this.preview=p;this.confirmedScope=false;});else if(this.reassignSource&&this.reassignTarget)this.run(this.api.previewReassign(this.reassignSource,this.reassignTarget,this.filters),p=>{this.preview=p;this.confirmedScope=false;});}
 reassign(){this.rulePreviewId=undefined;this.filters=this.emptyFilters();this.preview=undefined;this.confirmedScope=false;this.generatePreview();}
 cancelPreview(){this.preview=undefined;this.rulePreviewId=undefined;this.confirmedScope=false;}
 applyPreview(){if(!this.preview||!this.confirmedScope)return;this.run(this.api.applyBatch(this.preview.id),()=>{this.cancelPreview();this.success='Lote aplicado y registrado en el historial.';this.refreshCatalog();if(this.tab==='transactions')this.loadTransactions();});}
 revert(batch:AdminBatch){this.confirmation={title:'Revertir este lote',message:'Se restaurarán las clasificaciones anteriores. Si existen cambios posteriores, el servidor rechazará toda la reversión para protegerlos.',action:()=>this.run(this.api.revertBatch(batch.id),()=>{this.success='Lote revertido.';this.loadHistory();this.refreshCatalog();})};}
 confirm(){const action=this.confirmation?.action;this.confirmation=undefined;action?.();}
 label(code:string){const labels:Record<string,string>={AUTOMATIC:'Automática',LEGACY:'Histórica',GLOBAL_RULE:'Regla global',USER_RULE:'Regla de cuenta',MANUAL_USER:'Corrección del usuario',MANUAL_ADMIN:'Corrección administrativa',PREVIEW:'Vista previa',APPLIED:'Aplicado',REVERTED:'Revertido',MANUAL_CORRECTION:'Corrección de selección',RULE_APPLY:'Aplicación de regla',CATEGORY_REASSIGN:'Reasignación',CATEGORY_CREATE:'Categoría creada',CATEGORY_UPDATE:'Categoría editada',RULE_CREATE:'Regla creada',RULE_UPDATE:'Regla editada',PREVIEW_CREATE:'Vista previa creada',CORRECTION:'Clasificación corregida',BATCH_APPLY:'Lote aplicado',BATCH_REVERT:'Lote revertido',USER_CORRECTION:'Corrección personal',ROLE_GRANT:'Permiso concedido',CATEGORY:'Categoría',RULE:'Regla',USER:'Cuenta',TRANSACTION:'Consumo',BATCH:'Lote',DATABASE_OPERATOR:'Operador de base de datos',MANUAL:'Manual',GMAIL:'Gmail',BANK_EMAIL:'Correo bancario'};return labels[code]||code;}
 scopeLabel(scope?:string){try{const f=JSON.parse(scope||'{}') as AdminFilters;return `${f.userId?'Cuenta #'+f.userId:'Todas las cuentas'} · ${f.from||'Sin fecha inicial'} → ${f.to||'Sin fecha final'} · ${f.categoryId?this.categoryName(f.categoryId):'Todas las categorías'}${f.q?' · Búsqueda: '+f.q:''}${f.reviewStatus?' · '+(f.reviewStatus==='PENDING'?'Pendientes':'Revisados'):''}${f.uncategorized?' · Solo pendientes/sin categoría':''}`;}catch{return 'Revisa los filtros antes de confirmar.';}}
 money(amount:number,currency:string){if(currency==='UNK')return `${Number(amount).toLocaleString('es-PE',{minimumFractionDigits:2})} (sin moneda registrada)`;try{return new Intl.NumberFormat('es-PE',{style:'currency',currency}).format(amount);}catch{return `${amount} ${currency}`;}}
}
