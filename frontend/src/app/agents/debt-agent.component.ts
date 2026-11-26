import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
interface Result { monthly_payment:number; total_cost:number; search:{status:string;message:string;results:{title:string;url:string;excerpt:string}[]}; }
@Component({selector:'app-debt-agent',imports:[CommonModule,FormsModule],template:`
<section class="view"><article class="panel"><p class="eyebrow">COACH FINANCIERO · BETA</p><h1>Mi agente de deudas</h1><p>Introduce las condiciones de una deuda en soles. No necesitas conectar Gmail.</p>
<form (ngSubmit)="analyze()">
<label>Saldo pendiente (S/)<input type="number" name="balance" [(ngModel)]="balance" min="1" max="1000000" required></label>
<label>TEA contractual (%)<input type="number" name="tea" [(ngModel)]="tea" min="0" max="300" required></label>
<label>Meses restantes<input type="number" name="months" [(ngModel)]="months" min="1" max="120" step="1" required></label>
<button class="primary" [disabled]="busy" type="submit">{{busy?'Investigando…':'Analizar deuda'}}</button></form>
<p role="alert" *ngIf="error">{{error}}</p>
<div *ngIf="result as r"><h2>Escenario estimado</h2><p>Cuota: S/ {{r.monthly_payment | number:'1.2-2'}} · Total: S/ {{r.total_cost | number:'1.2-2'}}</p><p>No incluye seguros, comisiones ni condiciones particulares. No es una oferta.</p><h3>Investigación preliminar</h3><p>{{r.search.message}}</p><article *ngFor="let source of r.search.results"><a [href]="source.url" target="_blank" rel="noopener noreferrer">{{source.title}}</a><p>{{source.excerpt}}</p></article></div>
<p>Esta beta realiza análisis bajo demanda. El seguimiento automático y la memoria persistente aún no están habilitados.</p></article></section>`})
export class DebtAgentComponent {
 balance:number|null=null; tea:number|null=null; months:number|null=null; busy=false; error=''; result?:Result;
 constructor(private http:HttpClient){}
 analyze(){if(this.busy)return; if(this.balance===null||this.tea===null||this.months===null||!Number.isInteger(this.months)||this.balance<1||this.balance>1000000||this.tea<0||this.tea>300||this.months<1||this.months>120){this.error='Completa las condiciones válidas de tu deuda.';return;} this.busy=true;this.error='';this.result=undefined;this.http.post<Result>(environment.apiBaseUrl+'/agents/debts/analyze',{balance:this.balance,tea:this.tea,months:this.months}).subscribe({next:r=>{this.result=r;this.busy=false;},error:()=>{this.error='No se pudo completar el análisis. Comprueba la configuración del agente.';this.busy=false;}});}
}