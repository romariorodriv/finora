import { IconComponent } from '../shared/icon.component';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, Subject, takeUntil } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { PlanningState, PlanningProjection, PlanningPayment } from '../core/models/api.models';

@Component({selector: 'app-planning', imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './planning.component.html', styleUrl: './planning.component.css'})
export class PlanningComponent implements OnInit, OnDestroy {
  private readonly destroyed = new Subject<void>();
  constructor(private readonly api: ApiService) {}
  ngOnInit(): void { this.loadPlanning(); }
  ngOnDestroy(): void { this.destroyed.next(); this.destroyed.complete(); }
  money(value: number): string { return `S/ ${Number(value).toLocaleString('es-PE', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`; }
  private message(error: any): string { return error?.error?.message || 'No pudimos guardar los cambios. Intenta nuevamente.'; }
  // What-if planner: values are entered and confirmed by the user, not inferred from monthly transactions.
  planningBalance: number | null = null;
  planningReserve = 0;
  planningIncomeDate = this.defaultNextIncomeDate();
  planningObligations: PlanningPayment[] = [];
  planningAlerts: string[] = [];
  planningSaved = false;
  planningNew = { name: '', dueDate: '', amount: 0 };
  planningResult?: PlanningProjection;
  planningDirty = false;
  planningLoaded = false;
  planningSuccess = '';
  planningError = '';
  planningLoading = false;

  private defaultNextIncomeDate(): string {
    const d = new Date();
    d.setDate(d.getDate() + 15);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }

  markPlanningDirty(): void {
    this.planningDirty = true;
    this.planningResult = undefined;
    this.planningSuccess = '';
    this.planningAlerts = [];
  }

  paymentDate(date: string): string {
    return new Intl.DateTimeFormat('es-PE', {day: 'numeric', month: 'short', year: 'numeric'}).format(new Date(`${date}T12:00:00`));
  }

  paymentStatus(date: string): string {
    const parts = new Intl.DateTimeFormat('en-US', {timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit'}).formatToParts(new Date());
    const part = (type: string) => parts.find(item => item.type === type)?.value;
    const today = `${part('year')}-${part('month')}-${part('day')}`;
    if (date < today) return 'Vencido';
    const end = new Date(`${today}T12:00:00`);
    end.setDate(end.getDate() + 3);
    const limit = `${end.getFullYear()}-${String(end.getMonth()+1).padStart(2,'0')}-${String(end.getDate()).padStart(2,'0')}`;
    return date <= limit ? 'Próximo' : 'Pendiente';
  }

  addObligation(): void {
    if (this.planningLoading) return;
    const entry = { ...this.planningNew };
    if (!entry.name.trim() || !entry.dueDate || !/^\d{4}-\d{2}-\d{2}$/.test(entry.dueDate) || !Number.isFinite(entry.amount) || entry.amount <= 0 || entry.amount > 100000000 || this.planningObligations.length >= 200) {
      this.planningError = 'Completa nombre, fecha y monto positivo del pago.';
      return;
    }
    this.planningSuccess = '';
    this.planningError = '';
    this.planningLoading = true;
    this.api.addPlanningPayment({ ...entry, name: entry.name.trim() }).pipe(finalize(() => this.planningLoading = false)).pipe(takeUntil(this.destroyed)).subscribe({
      next: state => { this.applyPlanningState(state, true); this.planningSuccess = 'Pago añadido a tu plan.'; this.planningNew = {name:'',dueDate:'',amount:0}; },
      error: err => this.planningError = this.message(err)
    });
  }

  removeObligation(index: number): void {
    if (this.planningLoading) return;
    const item = this.planningObligations[index];
    if (!item) return;
    this.planningError = "";
    this.planningLoading = true;
    this.api.deletePlanningPayment(item.id).pipe(finalize(() => this.planningLoading = false)).pipe(takeUntil(this.destroyed)).subscribe({next: state => { this.applyPlanningState(state, true); this.planningSuccess = 'Pago retirado de tu plan.'; }, error: err => this.planningError = this.message(err)});
  }

  previewPlanning(): void {
    this.planningSuccess = '';
    this.planningError = '';
    this.planningResult = undefined;
    if (this.planningBalance === null || !Number.isFinite(this.planningBalance) || this.planningBalance < 0 || !Number.isFinite(this.planningReserve) || this.planningReserve < 0 || !this.planningIncomeDate) {
      this.planningError = 'Confirma tu saldo, reserva y fecha de próximo ingreso.';
      return;
    }
    const asOf = new Date();
    const date = `${asOf.getFullYear()}-${String(asOf.getMonth()+1).padStart(2,'0')}-${String(asOf.getDate()).padStart(2,'0')}`;
    if (this.planningIncomeDate <= date || !/^\d{4}-\d{2}-\d{2}$/.test(this.planningIncomeDate)) {
      this.planningError = 'El próximo ingreso debe tener una fecha futura válida.';
      return;
    }
    if (this.planningBalance > 100000000 || this.planningReserve > 100000000) {
      this.planningError = 'Confirma los importes: el máximo permitido es S/100 millones.';
      return;
    }
    if (this.planningLoading) return;
    this.planningLoading = true;
    this.api.savePlanningProfile({ nextIncomeDate: this.planningIncomeDate, confirmedBalance: this.planningBalance, reserve: this.planningReserve }).pipe(finalize(() => this.planningLoading = false)).pipe(takeUntil(this.destroyed)).subscribe({
      next: state => { this.applyPlanningState(state); this.planningSuccess = 'Tu plan está guardado y actualizado.'; },
      error: err => this.planningError = err?.error?.message || 'No fue posible calcular la disponibilidad. Comprueba los datos y la conexión.'
    });
  }
  private applyPlanningState(state: PlanningState, preserveDraft = false): void {
    const keepDraft = preserveDraft && this.planningDirty;
    this.planningLoaded = true;
    this.planningSaved = !!state.profile;
    if (state.profile && !keepDraft) {
      this.planningBalance = state.profile.confirmedBalance;
      this.planningReserve = state.profile.reserve;
      this.planningIncomeDate = state.profile.nextIncomeDate;
    }
    this.planningDirty = keepDraft;
    this.planningObligations = state.payments || [];
    this.planningResult = keepDraft ? undefined : state.projection || undefined;
    this.planningAlerts = keepDraft ? [] : state.alerts || [];
  }
  loadPlanning(): void {
    this.planningError = "";
    this.planningLoading = true;
    this.api.getPlanningState().pipe(finalize(() => this.planningLoading = false)).pipe(takeUntil(this.destroyed)).subscribe({next: state => this.applyPlanningState(state), error: err => this.planningError = this.message(err)});
  }
}
