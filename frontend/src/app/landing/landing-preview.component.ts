import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { IconComponent } from '../shared/icon.component';
@Component({
 selector: 'app-landing-preview', imports: [IconComponent],
 template: `
 <div class="preview-shell" [class.detail]="detail">
  <aside class="preview-sidebar" aria-hidden="true"><span class="preview-brand"><i></i>sarela</span><span class="selected"><app-icon name="home" [size]="14" />Inicio</span><span><app-icon name="wallet" [size]="14" />Gastos</span><span><app-icon name="grid" [size]="14" />Categorías</span><span><app-icon name="shield" [size]="14" />Ajustes</span></aside>
  <div class="preview-main">
   <div class="phone-status" aria-hidden="true"><b>9:41</b><span>▮▮▮ ▰</span></div>
   <div class="mobile-brand" aria-hidden="true"><span class="preview-brand"><i></i>sarela</span><app-icon name="rows" [size]="17" /></div>
   @if (!detail) {
    <div class="preview-greeting"><h3>Hola, Alex <span>👋</span></h3><span class="example-label">Datos de ejemplo</span></div><p class="preview-subtitle">Tus gastos, en orden.</p>
    <div class="preview-total"><div><span>Gastos del mes</span><strong>S/ 1,480</strong></div><div class="mini-chart" aria-hidden="true"><i></i><i></i><i></i></div></div>
    <div class="category-rows">@for (category of categories; track category.name) {<div class="category-row"><app-icon [name]="category.icon" [size]="19" /><div><b>{{category.name}}</b><span class="bar"><i [style.width.%]="category.width"></i></span></div><strong>{{category.amount}}</strong></div>}</div>
   } @else {
    <h3 class="movement-title"><span aria-hidden="true">‹</span>Movimientos</h3><div class="preview-tabs" aria-hidden="true"><span>Todos</span><span>Alimentación</span><span>Transporte</span><span>Compras</span></div>
   }
   <h4>{{detail ? 'Hoy' : 'Movimientos recientes'}}</h4>
   <div class="transaction-list">@for (movement of movements; track movement.name) {<div class="preview-transaction"><span class="merchant-mark" [class.dark]="movement.name === 'Uber'">{{movement.mark}}</span><div><b>{{movement.name}}</b><span>{{movement.category}}</span></div><strong>{{movement.amount}}</strong></div>}</div>
   <span class="phone-home" aria-hidden="true"></span>
  </div>
 </div>`,
 styleUrl: './landing-preview.component.css', changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingPreviewComponent {
 @Input() detail = false;
 readonly categories = [
  {name:'Alimentación',icon:'utensils' as const,amount:'S/ 620',width:77},
  {name:'Transporte',icon:'car' as const,amount:'S/ 320',width:46},
  {name:'Compras',icon:'bag' as const,amount:'S/ 260',width:33}
 ];
 readonly movements = [
  {name:'OXXO',mark:'oxxo',category:'Alimentación',amount:'S/ 24.90'},
  {name:'Uber',mark:'Uber',category:'Transporte',amount:'S/ 18.00'},
  {name:'Plaza Vea',mark:'vea',category:'Compras',amount:'S/ 89.90'}
 ];
}
