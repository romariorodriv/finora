import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiService } from '../core/services/api.service';

interface HousingResult {
  agent: string;
  status: string;
  budget: {
    monthly_budget: number;
    estimated_max_loan: number;
    estimated_max_price: number;
    available_down_payment: number;
    annual_effective_rate_assumed: number;
    years_assumed: number;
    assumptions: string[];
  };
  search: {
    status: string;
    message: string;
    results: {title: string; url: string; excerpt: string; verified: boolean}[];
  };
  note: string;
}

@Component({
  selector: 'app-housing-agent',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="housing-agent">
      <header class="housing-heading">
        <span class="agent-eyebrow">AGENTE 01 · VIVIENDA</span>
        <h1>¿Cuál es el departamento que puedes pagar?</h1>
        <p>Cuéntanos lo esencial. Investigamos opciones públicas y calculamos un escenario financiero sin prometer una aprobación bancaria.</p>
      </header>
      <form class="housing-form" (ngSubmit)="analyze()" #housingForm="ngForm">
        <div class="housing-fields">
          <label>¿Cuánto ganas al mes? (S/)
            <input type="number" name="income" min="1" max="1000000" required [(ngModel)]="income">
          </label>
          <label>¿Cuánto gastas en necesidades básicas? (S/)
            <input type="number" name="expenses" min="0" max="1000000" required [(ngModel)]="expenses">
          </label>
          <label>¿Cuánto pagas al mes por deudas? (S/)
            <input type="number" name="debts" min="0" max="1000000" required [(ngModel)]="debts">
          </label>
          <label>¿Cuánto tienes ahorrado? (S/)
            <input type="number" name="savings" min="0" max="20000000" required [(ngModel)]="savings">
          </label>
          <label>¿En qué distrito de Lima buscas?
            <select name="district" required [(ngModel)]="district">
              <option value="San Miguel">San Miguel</option>
              <option value="Pueblo Libre">Pueblo Libre</option>
              <option value="Jesús María">Jesús María</option>
              <option value="Santiago de Surco">Santiago de Surco</option>
              <option value="Los Olivos">Los Olivos</option>
              <option value="Independencia">Independencia</option>
              <option value="Ate">Ate</option>
              <option value="Lima">Explorar Lima</option>
            </select>
          </label>
        </div>
        <button class="agent-primary" type="submit" [disabled]="loading || !housingForm.valid">{{loading ? 'Investigando…' : 'Investigar mi primera vivienda'}}</button>
        <p class="agent-disclaimer">Estos datos se envían a Sarela para calcular esta solicitud. No conectamos Gmail ni solicitamos una contraseña bancaria.</p>
      </form>
      <p *ngIf="error" role="alert" class="agent-error">{{error}}</p>
      <section *ngIf="result as report" class="housing-results" aria-live="polite">
        <h2>Tu primer análisis</h2>
        <div class="housing-summary">
          <div><span>Cuota mensual de planificación</span><strong>{{report.budget.monthly_budget | currency:'PEN':'symbol':'1.0-0'}}</strong></div>
          <div><span>Precio de compra ilustrativo máximo</span><strong>{{report.budget.estimated_max_price | currency:'PEN':'symbol':'1.0-0'}}</strong></div>
          <div><span>Inicial disponible estimada</span><strong>{{report.budget.available_down_payment | currency:'PEN':'symbol':'1.0-0'}}</strong></div>
        </div>
        <h3>Fuentes inmobiliarias encontradas</h3>
        <p>{{report.search.message}}</p>
        <article class="housing-source" *ngFor="let item of report.search.results">
          <a [href]="item.url" target="_blank" rel="noopener noreferrer">{{item.title || 'Consultar publicación'}}</a>
          <p>{{item.excerpt}}</p>
          <small>Enlace preliminar sin verificación de precio ni disponibilidad</small>
        </article>
        <p *ngIf="!report.search.results.length">Todavía no hay publicaciones verificables para mostrar. No inventamos inmuebles.</p>
        <h3>Cómo se calculó</h3>
        <ul><li *ngFor="let assumption of report.budget.assumptions">{{assumption}}</li></ul>
        <p class="agent-disclaimer">{{report.note}}</p>
      </section>
    </section>
  `,
  styles: [`
    .housing-agent{max-width:980px;margin:auto;padding:10px 0 55px}
    .housing-heading{margin:10px 0 28px}.housing-heading h1{font-size:clamp(26px,3.3vw,42px);line-height:1.14;letter-spacing:-.04em;margin:9px 0 14px}
    .housing-heading p{max-width:650px;color:#587063;line-height:1.6}
    .agent-eyebrow{font-size:11px;color:#21794d;font-weight:800;letter-spacing:.12em}
    .housing-form,.housing-results{background:white;border:1px solid #dfe8e2;border-radius:20px;padding:clamp(19px,3vw,34px)}
    .housing-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:17px}
    .housing-fields label{display:flex;flex-direction:column;gap:8px;font-size:13px;font-weight:600;color:#314a3b}
    .housing-fields input,.housing-fields select{background:#f7faf8;border:1px solid #d7e3dc;border-radius:10px;padding:12px;width:100%;min-height:44px;color:#173d2b}
    .agent-primary{margin-top:24px;border:0;border-radius:12px;background:#17683c;color:white;padding:15px 20px;font-weight:750;cursor:pointer;width:100%}
    .agent-primary:disabled{opacity:.55;cursor:wait}.agent-disclaimer{margin-top:16px;font-size:12px;color:#66796e;line-height:1.6}
    .agent-error{padding:15px;margin:20px 0;color:#9b302d;background:#fff0f0;border-radius:12px}
    .housing-results{margin-top:24px}.housing-results h2{font-size:25px;margin-bottom:16px}
    .housing-results h3{font-size:18px;margin:23px 0 12px}.housing-results ul{padding-left:22px;line-height:1.6;color:#516758}
    .housing-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
    .housing-summary>div{padding:17px;background:#edf7f0;border-radius:12px}
    .housing-summary span{font-size:12px;color:#496655;display:block;margin-bottom:9px}.housing-summary strong{font-size:21px;display:block}
    .housing-source{padding:15px 0;border-bottom:1px solid #e2eae5}.housing-source a{font-weight:700;color:#16623d}
    .housing-source p{font-size:13px;color:#506957;line-height:1.5}.housing-source small{color:#956b1d}
    @media(max-width:650px){.housing-fields,.housing-summary{grid-template-columns:1fr}}
  `]
})
export class HousingAgentComponent {
  income = 5000;
  expenses = 2200;
  debts = 0;
  savings = 40000;
  district = 'San Miguel';
  loading = false;
  result?: HousingResult;
  error = '';

  constructor(private readonly api: ApiService) {}

  analyze(): void {
    if (this.loading || !this.district || this.income <= 0 || [this.expenses,this.debts,this.savings].some(v=>v<0 || !Number.isFinite(v))) return;
    this.loading = true;
    this.error = '';
    this.result = undefined;
    this.api.analyzeHousing({
      income:this.income, essential_expenses:this.expenses,
      monthly_debts:this.debts, savings:this.savings, districts:[this.district]
    }).pipe(finalize(()=>this.loading=false)).subscribe({
      next: result=>this.result=result,
      error: err=>this.error=err?.error?.message || 'No pudimos completar el análisis. Intenta nuevamente.'
    });
  }
}
