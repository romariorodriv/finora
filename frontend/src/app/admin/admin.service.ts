import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
export interface AdminPage<T> {items:T[];totalElements:number;totalPages:number;page:number;}
export interface AdminCategory {id:number;name:string;description:string;icon:string;color:string;macroGroup:string;active:boolean;version:number;transactionCount?:number;}
export interface AdminRule {id:number;merchant:string;matchType:'EXACT'|'PREFIX';scope:'USER'|'GLOBAL';userId:number|null;categoryId:number;priority:number;active:boolean;version:number;}
export interface AdminTx {id:number;accountId:number;date:string;merchant:string;normalizedMerchant:string;description:string;amount:number;currency:string;categoryId:number|null;category:string;classificationSource:string;reviewStatus:string;source:string;}
export interface CurrencyTotal {currency:string;total:number;count:number;}
export interface AdminBatch {id:string;kind:string;status:string;count?:number;changedCount?:number;skippedManual:number;targetCategoryId:number;ruleId:number|null;expiresAt:string;scope?:string;examples?:AdminTx[];totals?:CurrencyTotal[];createdAt?:string;actorId?:number;}
export interface AdminAudit {id:number;actorId:number|null;actorKind:string;action:string;entityType:string;entityId:string;beforeJson:string;afterJson:string;batchId:string|null;createdAt:string;}
export interface AdminFilters {q:string;from:string;to:string;categoryId:number|null;reviewStatus:string;userId:number|null;uncategorized:boolean;}
@Injectable({providedIn:'root'})
export class AdminService {
 private readonly base=environment.apiBaseUrl+'/admin';
 constructor(private readonly http:HttpClient){}
 private params(values:object){let p=new HttpParams();for(const [key,value] of Object.entries(values))if(value!==null&&value!==undefined&&value!=='')p=p.set(key,String(value));return p;}
 categories(q:string,active:string,page:number){return this.http.get<AdminPage<AdminCategory>>(this.base+'/categories',{params:this.params({q,active,page,size:20})});}
 options(){return this.http.get<AdminCategory[]>(this.base+'/category-options');}
 saveCategory(value:object,id?:number){return id?this.http.put<AdminCategory>(`${this.base}/categories/${id}`,value):this.http.post<AdminCategory>(this.base+'/categories',value);}
 transactions(filters:AdminFilters,page:number){return this.http.get<{page:AdminPage<AdminTx>;totals:CurrencyTotal[]}>(this.base+'/transactions',{params:this.params({...filters,page,size:20})});}
 correct(transactionIds:number[],categoryId:number){return this.http.post<AdminBatch>(this.base+'/corrections',{transactionIds,categoryId,confirmed:true});}
 rules(q:string,page:number){return this.http.get<AdminPage<AdminRule>>(this.base+'/rules',{params:this.params({q,page,size:20})});}
 saveRule(value:object,id?:number){return id?this.http.put<AdminRule>(`${this.base}/rules/${id}`,value):this.http.post<AdminRule>(this.base+'/rules',value);}
 previewRule(id:number,filters:AdminFilters){return this.http.post<AdminBatch>(`${this.base}/rules/${id}/preview`,filters);}
 previewReassign(id:number,targetCategoryId:number,filters:AdminFilters){return this.http.post<AdminBatch>(`${this.base}/categories/${id}/reassign-preview`,{targetCategoryId,filters});}
 applyBatch(id:string){return this.http.post<AdminBatch>(`${this.base}/batches/${id}/apply`,{confirmed:true});}
 revertBatch(id:string){return this.http.post<AdminBatch>(`${this.base}/batches/${id}/revert`,{confirmed:true});}
 batches(page:number){return this.http.get<AdminPage<AdminBatch>>(this.base+'/batches',{params:this.params({page,size:20})});}
 history(page:number){return this.http.get<AdminPage<AdminAudit>>(this.base+'/history',{params:this.params({page,size:20})});}
}
