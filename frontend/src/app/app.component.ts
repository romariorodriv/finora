import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiService } from './core/services/api.service';
import { AuthService } from './core/services/auth.service';
import {
  calculateDailySpending,
  calculateMerchantDistribution,
  calculateMacroCategoryDistribution,
  CategorySpendingPoint,
  DailySpendingPoint,
  generateFinancialInsight
} from './core/dashboard.utils';
import {
  CategoryDetailResponse,
  DashboardResponse,
  FeedbackRequest,
  GmailStatus,
  GmailSyncResponse,
  MerchantDetailResponse,
  TransactionRequest,
  TransactionResponse
} from './core/models/api.models';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  view: 'dashboard' | 'movements' | 'import' | 'feedback' = 'dashboard';
  authMode: 'login' | 'register' = 'login';
  readonly showDevTools = false;
  loginForm = { email: '', password: '' };
  registerForm = { name: '', email: '', password: '', confirmPassword: '' };
  txForm: TransactionRequest = this.emptyTransaction();
  editingId: number | null = null;
  importForm = {
    sender: 'notificaciones@bcp.com.pe',
    content: 'Realizaste un consumo de S/ 38.90 en Rappi con tu tarjeta BCP.'
  };
  feedbackForm: FeedbackRequest = this.emptyFeedback();
  feedbackSubmitting = false;
  feedbackSubmitted = false;
  feedbackError = '';
  dashboard?: DashboardResponse;
  transactions: TransactionResponse[] = [];
  categories: string[] = [];
  gmail?: GmailStatus;
  syncResult?: GmailSyncResponse;
  error = '';
  toast = '';
  syncingGmail = false;
  gmailUpdateStatus = '';
  selectedSpendingRange: 7 | 30 = 30;
  selectedCategory?: CategoryDetailResponse;
  selectedMerchant?: MerchantDetailResponse;
  loadingCategory = false;
  loadingMerchant = false;
  private readonly chartColors = ['#0f3d30', '#2f7d62', '#6fc39b', '#b6d7c4', '#dfe8e2'];
  private readonly merchantColors = ['#173c34', '#5f927d', '#9ec7b2', '#d3e2d9', '#eef2ef', '#c8d4cc'];

  constructor(public readonly auth: AuthService, private readonly api: ApiService) {}

  ngOnInit(): void {
    if (this.auth.user) {
      this.loadApp();
    }
    const gmailParam = new URLSearchParams(location.search).get('gmail');
    if (gmailParam) {
      history.replaceState({}, '', location.pathname);
      this.view = 'dashboard';
      this.showToast(gmailParam === 'connected' ? 'Gmail conectado correctamente' : 'No se autorizo la conexion');
    }
  }

  login(): void {
    this.error = '';
    this.auth.login(this.loginForm.email, this.loginForm.password).subscribe({
      next: () => this.loadApp(),
      error: e => {
        console.error('Login failed', e);
        this.error = this.authMessage(e, 'login');
      }
    });
  }

  register(): void {
    this.error = '';
    if (this.registerForm.password !== this.registerForm.confirmPassword) {
      this.error = 'Las contrasenas no coinciden.';
      return;
    }
    this.auth.register(this.registerForm.name, this.registerForm.email, this.registerForm.password).subscribe({
      next: () => {
        this.showToast('Tu cuenta esta lista');
        this.loadApp();
      },
      error: e => {
        console.error('Registration failed', e);
        this.error = this.authMessage(e, 'register');
      }
    });
  }

  logout(): void {
    this.auth.logout();
    this.dashboard = undefined;
    this.transactions = [];
    this.selectedCategory = undefined;
    this.selectedMerchant = undefined;
  }

  go(view: typeof this.view): void {
    this.view = view;
    if (view === 'movements') {
      this.loadTransactions();
    }
    if (view === 'import') {
      this.loadGmail();
    }
    if (view === 'feedback') {
      this.feedbackError = '';
    }
  }

  saveTransaction(): void {
    const action = this.editingId
      ? this.api.updateTransaction(this.editingId, this.txForm)
      : this.api.createTransaction(this.txForm);
    action.subscribe({
      next: () => {
        this.showToast(this.editingId ? 'Movimiento actualizado' : 'Movimiento guardado');
        this.cancelEdit();
        this.loadDashboard();
        this.loadTransactions();
      },
      error: e => this.showToast(this.message(e))
    });
  }

  editTransaction(tx: TransactionResponse): void {
    this.editingId = tx.id;
    this.txForm = {
      description: tx.description,
      amount: Number(tx.amount),
      date: tx.date,
      category: tx.category,
      type: tx.type,
      merchant: tx.merchant || tx.description,
      recurring: tx.recurring
    };
  }

  deleteTransaction(id: number): void {
    this.api.deleteTransaction(id).subscribe({
      next: () => {
        this.showToast('Movimiento eliminado');
        this.loadDashboard();
        this.loadTransactions();
      },
      error: e => this.showToast(this.message(e))
    });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.txForm = this.emptyTransaction();
  }

  importEmail(): void {
    this.api.importBankEmail(this.importForm.sender, this.importForm.content).subscribe({
      next: r => {
        this.showToast(`Gasto importado con ${Math.round(r.confidence * 100)}% de confianza`);
        this.loadDashboard();
      },
      error: e => this.showToast(this.message(e))
    });
  }

  connectGmail(): void {
    this.api.gmailAuthorizationUrl().subscribe({
      next: r => location.href = r.authorizationUrl,
      error: e => this.showToast(this.message(e))
    });
  }

  syncGmail(): void {
    if (this.syncingGmail) {
      return;
    }
    this.syncingGmail = true;
    this.gmailUpdateStatus = 'Actualizando tus movimientos...';
    this.api.gmailSync().pipe(finalize(() => this.syncingGmail = false)).subscribe({
      next: r => {
        this.syncResult = r;
        this.gmailUpdateStatus = r.transactionsCreated > 0
          ? `Actualizado hace unos segundos. ${r.transactionsCreated} movimientos nuevos.`
          : 'Todo esta al dia. No encontramos movimientos nuevos.';
        this.showToast(`${r.transactionsCreated} movimientos importados`);
        this.loadDashboard();
        this.loadGmail();
      },
      error: e => {
        this.gmailUpdateStatus = 'No pudimos actualizar tus movimientos. Intenta nuevamente.';
        this.showToast(this.message(e));
      }
    });
  }

  disconnectGmail(): void {
    this.api.gmailDisconnect().subscribe({
      next: () => {
        this.showToast('Gmail desconectado');
        this.loadGmail();
      },
      error: e => this.showToast(this.message(e))
    });
  }

  submitFeedback(): void {
    this.feedbackError = '';
    if (!this.feedbackForm.rating || this.feedbackForm.rating < 1 || this.feedbackForm.rating > 5) {
      this.feedbackError = 'Elige una calificacion del 1 al 5.';
      return;
    }
    if (!this.feedbackForm.understoodSpending) {
      this.feedbackError = 'Cuentanos si Sarela te ayudo a entender tus gastos.';
      return;
    }
    this.feedbackSubmitting = true;
    this.api.sendFeedback(this.feedbackForm).pipe(finalize(() => this.feedbackSubmitting = false)).subscribe({
      next: () => {
        this.feedbackSubmitted = true;
        this.feedbackForm = this.emptyFeedback();
        this.showToast('Gracias por tu opinion');
      },
      error: e => this.feedbackError = this.message(e)
    });
  }

  openFeedback(): void {
    this.go('feedback');
  }

  money(value: number | string | undefined): string {
    return `S/ ${Number(value || 0).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  selectSpendingRange(range: 7 | 30): void {
    this.selectedSpendingRange = range;
  }

  hasDailySpending(): boolean {
    return Boolean(this.dashboard && this.dashboard.expenses > 0 && Object.keys(this.dashboard.daily).length > 0);
  }

  dailyPoints(): DailySpendingPoint[] {
    return calculateDailySpending(this.dashboard?.daily ?? {}, this.selectedSpendingRange);
  }

  linePoints(): string {
    const points = this.dailyPoints();
    if (!points.length) {
      return '';
    }
    const max = Math.max(...points.map(point => point.value), 1);
    return points.map((point, index) => {
      const x = points.length === 1 ? 50 : (index / (points.length - 1)) * 100;
      const y = 100 - (point.value / max) * 82 - 8;
      return `${x},${y}`;
    }).join(' ');
  }

  categoryDistribution(): CategorySpendingPoint[] {
    return calculateMacroCategoryDistribution(this.dashboard?.macroCategories, this.chartColors);
  }

  merchantDistribution(): CategorySpendingPoint[] {
    return calculateMerchantDistribution(this.dashboard?.merchants, this.merchantColors);
  }

  donutStyle(items: CategorySpendingPoint[]): Record<string, string> {
    if (!items.length) {
      return {};
    }
    let cursor = 0;
    const stops = items.map(item => {
      const start = cursor;
      cursor += item.percentage;
      return `${item.color} ${start}% ${cursor}%`;
    });
    return { background: `conic-gradient(${stops.join(', ')})` };
  }

  financialInsightTitle(): string {
    return this.dashboard?.insightTitle || 'Una senal para este mes';
  }

  financialInsight(): string {
    return this.dashboard?.insightBody || generateFinancialInsight(this.categoryDistribution(), this.dashboard?.expenses ?? 0);
  }

  openCategory(item: CategorySpendingPoint): void {
    if (!item.macroCategory) {
      return;
    }
    this.loadingCategory = true;
    this.api.categoryDetail(item.macroCategory).pipe(finalize(() => this.loadingCategory = false)).subscribe({
      next: detail => this.selectedCategory = detail,
      error: e => this.showToast(this.message(e))
    });
  }

  openMerchant(item: CategorySpendingPoint): void {
    if (!item.merchant || item.merchant === '__OTHER_MERCHANTS__') {
      return;
    }
    this.loadingMerchant = true;
    this.api.merchantDetail(item.merchant).pipe(finalize(() => this.loadingMerchant = false)).subscribe({
      next: detail => this.selectedMerchant = detail,
      error: e => this.showToast(this.message(e))
    });
  }

  closeCategory(): void {
    this.selectedCategory = undefined;
  }

  closeMerchant(): void {
    this.selectedMerchant = undefined;
  }

  closeDrawer(): void {
    this.selectedCategory = undefined;
    this.selectedMerchant = undefined;
  }

  transactionCount(): number {
    return this.dashboard?.transactions?.filter(tx => tx.type === 'EXPENSE').length ?? 0;
  }

  averageTicket(): number {
    const count = this.transactionCount();
    return count ? Number(this.dashboard?.expenses ?? 0) / count : 0;
  }

  movementTitle(tx: TransactionResponse): string {
    return tx.merchant?.trim() || tx.description;
  }

  movementDate(date: string): string {
    return new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short' })
      .format(new Date(`${date}T12:00:00`));
  }

  lastSyncLabel(): string {
    if (!this.gmail?.lastSyncAt) {
      return 'Aun no has actualizado tus movimientos';
    }
    const syncDate = new Date(this.gmail.lastSyncAt);
    const today = new Date();
    if (syncDate.toDateString() === today.toDateString()) {
      return `Hoy, ${new Intl.DateTimeFormat('es-PE', { timeStyle: 'short' }).format(syncDate)}`;
    }
    return new Intl.DateTimeFormat('es-PE', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(this.gmail.lastSyncAt));
  }

  private loadApp(): void {
    this.loadDashboard();
    this.loadCategories();
    this.loadGmail();
  }

  private loadDashboard(): void {
    this.api.dashboard().subscribe({
      next: d => this.dashboard = d,
      error: e => this.showToast(this.message(e))
    });
  }

  private loadTransactions(): void {
    this.api.transactions().subscribe({ next: t => this.transactions = t, error: e => this.showToast(this.message(e)) });
  }

  private loadCategories(): void {
    this.api.categories().subscribe({
      next: c => {
        this.categories = c;
        if (!this.txForm.category) {
          this.txForm.category = c[0] || 'Otros';
        }
      },
      error: e => this.showToast(this.message(e))
    });
  }

  private loadGmail(): void {
    this.api.gmailStatus().subscribe({ next: s => this.gmail = s, error: e => this.showToast(this.message(e)) });
  }

  private emptyTransaction(): TransactionRequest {
    return {
      description: '',
      amount: 0,
      date: new Date().toISOString().slice(0, 10),
      category: '',
      type: 'EXPENSE',
      merchant: '',
      recurring: false
    };
  }

  private emptyFeedback(): FeedbackRequest {
    return {
      rating: 0,
      understoodSpending: '',
      liked: '',
      improvement: '',
      wantedFeature: ''
    };
  }

  private showToast(message: string): void {
    this.toast = message;
    setTimeout(() => this.toast = '', 2600);
  }

  private message(error: any): string {
    if (error?.status === 0) {
      return 'No pudimos conectarnos con Sarela. Intenta nuevamente.';
    }
    return error?.error?.message || 'Algo salio mal. Intenta nuevamente.';
  }

  private authMessage(error: any, mode: 'login' | 'register'): string {
    if (error?.status === 0) {
      return 'No pudimos conectarnos con Sarela. Intenta nuevamente.';
    }
    if (mode === 'login' && error?.status === 401) {
      return 'No pudimos iniciar sesion. Revisa tu correo y contrasena.';
    }
    if (mode === 'register' && (error?.status === 409 || error?.error?.code === 'EMAIL_ALREADY_REGISTERED')) {
      return 'Ya existe una cuenta con este correo.';
    }
    return 'Algo salio mal. Intenta nuevamente.';
  }
}
