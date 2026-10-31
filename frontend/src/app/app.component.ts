import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { LandingComponent } from './landing/landing.component';
import { IconComponent } from './shared/icon.component';
import { FocusTrapDirective } from './shared/focus-trap.directive';
import { PlanningComponent } from './planning/planning.component';
import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
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
  PlanningState,
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
  imports: [CommonModule, FormsModule, PlanningComponent, IconComponent, FocusTrapDirective, LandingComponent, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrls: ['./dashboard/dashboard.styles.css', './app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  view: 'dashboard' | 'movements' | 'import' | 'feedback' | 'planning' | 'admin' = 'dashboard';
  planningSummary?: PlanningState;
  planningSummaryLoading = false;
  planningSummaryError = '';
  dashboardLoading = false;
  dashboardError = '';
  transactionsLoading = false;
  selectedCurrency = '';
  private routeSubscription?: Subscription;
  private planningSummaryRequest?: Subscription;
  publicView: 'landing' | 'access' = 'landing';
  authSubmitting = false;
  savingTransaction = false;
  deletingTransactionIds = new Set<number>();
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
  movementFilters = {
    search: '',
    from: '',
    to: '',
    category: '',
    type: ''
  };
  movementPage = 1;
  readonly movementPageSize = 12;
  private readonly chartColors = ['#0f3d30', '#2f7d62', '#6fc39b', '#b6d7c4', '#dfe8e2'];
  private readonly merchantColors = ['#173c34', '#5f927d', '#9ec7b2', '#d3e2d9', '#eef2ef', '#c8d4cc'];

  constructor(public readonly auth: AuthService, private readonly api: ApiService, private readonly router: Router) {
    this.routeSubscription=router.events.subscribe(event=>{if(event instanceof NavigationEnd){if(event.urlAfterRedirects.startsWith('/admin'))this.view='admin';else if(this.view==='admin')this.view='dashboard';if(!this.auth.user)this.syncPublicView();}});
  }

  ngOnInit(): void {
    this.syncPublicView();
    if (location.pathname === '/admin') this.view='admin';
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

  ngOnDestroy(): void { this.planningSummaryRequest?.unsubscribe(); this.routeSubscription?.unsubscribe(); }

  openAccess(mode: 'login' | 'register'): void {
    this.authMode = mode;
    this.publicView = 'access';
    this.error = '';
    history.replaceState({}, '', location.pathname + location.search + (mode === 'register' ? '#registro' : '#acceso'));
    window.scrollTo({top: 0, behavior: 'instant'});
  }

  returnToLanding(): void {
    this.publicView = 'landing';
    this.error = '';
    history.replaceState({}, '', location.pathname + location.search);
    window.scrollTo({top: 0, behavior: 'instant'});
  }

  @HostListener('window:hashchange')
  syncPublicView(): void {
    if (this.auth.user) return;
    const hash = location.hash;
    this.publicView = hash === '#acceso' || hash === '#registro' ? 'access' : 'landing';
    if (this.publicView === 'access') this.authMode = hash === '#registro' ? 'register' : 'login';
  }

  login(): void {
    if (this.authSubmitting) return;
    this.authSubmitting = true;
    this.error = '';
    this.auth.login(this.loginForm.email, this.loginForm.password).pipe(finalize(() => this.authSubmitting = false)).subscribe({
      next: () => this.loadApp(),
      error: e => {
        console.error('Login failed', e);
        this.error = this.authMessage(e, 'login');
      }
    });
  }

  register(): void {
    if (this.authSubmitting) return;
    this.error = '';
    if (this.registerForm.password !== this.registerForm.confirmPassword) {
      this.error = 'Las contrasenas no coinciden.';
      return;
    }
    this.authSubmitting = true;
    this.auth.register(this.registerForm.name, this.registerForm.email, this.registerForm.password).pipe(finalize(() => this.authSubmitting = false)).subscribe({
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
    this.planningSummaryRequest?.unsubscribe();
    this.planningSummary = undefined;
    this.planningSummaryError = '';
    this.selectedCurrency='';
    void this.router.navigateByUrl('/');
    this.auth.logout();
    this.returnToLanding();
    this.view = 'dashboard';
    this.dashboard = undefined;
    this.transactions = [];
    this.selectedCategory = undefined;
    this.selectedMerchant = undefined;
  }

  openAdmin(): void { void this.router.navigateByUrl('/admin'); }

  go(view: typeof this.view): void {
    if (this.router.url.startsWith('/admin')) void this.router.navigateByUrl('/');
    this.view = view;
    if (view === 'dashboard') this.loadPlanningSummary();
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
    if (this.savingTransaction) return;
    this.savingTransaction = true;
    const action = this.editingId
      ? this.api.updateTransaction(this.editingId, this.txForm)
      : this.api.createTransaction(this.txForm);
    action.pipe(finalize(() => this.savingTransaction = false)).subscribe({
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
    if (this.deletingTransactionIds.has(id)) return;
    this.deletingTransactionIds.add(id);
    this.api.deleteTransaction(id).pipe(finalize(() => this.deletingTransactionIds.delete(id))).subscribe({
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

  money(value: number | string | undefined, currency?: string): string {
    const unit=currency||this.dashboard?.currency||'PEN';
    const amount=Number(value||0);
    if(unit==='UNK')return `${amount.toLocaleString('es-PE',{minimumFractionDigits:2,maximumFractionDigits:2})} (moneda no registrada)`;
    if(unit==='PEN')return `S/ ${amount.toLocaleString('es-PE',{minimumFractionDigits:2,maximumFractionDigits:2})}`;
    try{return new Intl.NumberFormat('es-PE',{style:'currency',currency:unit}).format(amount);}catch{return `${amount} ${unit}`;}
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

  trackSpendingPoint(index: number, item: CategorySpendingPoint): string {
    return item.macroCategory || item.merchant || item.name;
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

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeDrawer();
  }

  transactionCount(): number {
    return this.dashboard?.expenseCount ?? 0;
  }

  averageTicket(): number {
    return this.dashboard?.averageTicket ?? 0;
  }

  filteredTransactions(): TransactionResponse[] {
    const search = this.normalizeSearch(this.movementFilters.search);
    return this.transactions.filter(tx => {
      const text = this.normalizeSearch(`${tx.merchant || ''} ${tx.description || ''}`);
      const matchesSearch = !search || text.includes(search);
      const matchesCategory = !this.movementFilters.category
        || tx.category === this.movementFilters.category
        || tx.macroCategory === this.movementFilters.category;
      const matchesType = !this.movementFilters.type || tx.type === this.movementFilters.type;
      const matchesFrom = !this.movementFilters.from || tx.date >= this.movementFilters.from;
      const matchesTo = !this.movementFilters.to || tx.date <= this.movementFilters.to;
      return matchesSearch && matchesCategory && matchesType && matchesFrom && matchesTo;
    });
  }

  pagedTransactions(): TransactionResponse[] {
    const start = (this.movementPage - 1) * this.movementPageSize;
    return this.filteredTransactions().slice(start, start + this.movementPageSize);
  }

  movementTotalPages(): number {
    return Math.max(1, Math.ceil(this.filteredTransactions().length / this.movementPageSize));
  }

  updateMovementFilters(): void {
    this.movementPage = 1;
  }

  resetMovementFilters(): void {
    this.movementFilters = { search: '', from: '', to: '', category: '', type: '' };
    this.movementPage = 1;
  }

  hasMovementFilters(): boolean {
    return Object.values(this.movementFilters).some(Boolean);
  }

  previousMovementPage(): void {
    this.movementPage = Math.max(1, this.movementPage - 1);
  }

  nextMovementPage(): void {
    this.movementPage = Math.min(this.movementTotalPages(), this.movementPage + 1);
  }

  movementTitle(tx: TransactionResponse): string {
    return tx.merchant?.trim() || tx.description;
  }

  movementTypeLabel(type: string): string {
    const labels: Record<string, string> = { EXPENSE: 'Gasto', INCOME: 'Ingreso', TRANSFER: 'Transferencia',
      SAVINGS_WITHDRAWAL: 'Retiro de ahorro', SAVINGS_DEPOSIT: 'Aporte al ahorro', CASH_WITHDRAWAL: 'Retiro en efectivo', REFUND: 'Devolución' };
    return labels[type] || type;
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

  loadPlanningSummary(): void {
    this.planningSummaryRequest?.unsubscribe();
    this.planningSummaryLoading = true;
    this.planningSummaryError = '';
    this.planningSummaryRequest = this.api.getPlanningState().pipe(
      finalize(() => this.planningSummaryLoading = false)
    ).subscribe({
      next: state => this.planningSummary = state,
      error: () => this.planningSummaryError = 'No pudimos cargar tu plan. Puedes volver a intentarlo.'
    });
  }

  monthLabel(): string {
    const month = this.dashboard?.month;
    if (!month || !/^\d{4}-\d{2}$/.test(month)) return 'Este mes';
    return new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' }).format(new Date(`${month}-15T12:00:00`));
  }

  recurringTotal(): number {
    return (this.dashboard?.recurring ?? []).reduce((total, tx) => total + Number(tx.amount), 0);
  }

  dailyPeak(): number { return Math.max(...this.dailyPoints().map(point => point.value), 0); }

  private loadApp(): void {
    this.auth.refreshCurrent().subscribe({next:()=>{if(new URLSearchParams(location.search).get('returnUrl')==='/admin'&&this.auth.isAdmin)void this.router.navigateByUrl('/admin');},error:()=>{}});
    history.replaceState({}, '', location.pathname + location.search);
    this.loadDashboard();
    this.loadPlanningSummary();
    this.loadCategories();
    this.loadGmail();
  }

  loadDashboard(): void {
    this.dashboardLoading = true;
    this.dashboardError = '';
    this.api.dashboard(this.selectedCurrency||undefined).pipe(finalize(() => this.dashboardLoading = false)).subscribe({
      next: d => {this.dashboard = d;this.selectedCurrency=d.currency||'';},
      error: e => this.dashboardError = this.message(e)
    });
  }

  private loadTransactions(): void {
    this.transactionsLoading = true;
    this.api.transactions().pipe(finalize(() => this.transactionsLoading = false)).subscribe({ next: t => this.transactions = t, error: e => this.showToast(this.message(e)) });
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

  gmailConnectionLabel(): string {
    if (!this.gmail) {
      return 'Consultando estado';
    }
    if (!this.gmail.configured) {
      return 'No disponible';
    }
    if (!this.gmail.connected) {
      return 'No conectado';
    }
    if (this.syncingGmail) {
      return 'Actualizando';
    }
    return this.gmail.status === 'CONNECTED' ? 'Conectado' : this.gmail.status;
  }

  gmailStatusCopy(): string {
    if (!this.gmail) {
      return 'Estamos consultando la conexion.';
    }
    if (!this.gmail.configured) {
      return 'La integracion todavia no esta configurada para este entorno.';
    }
    if (!this.gmail.connected) {
      return 'Conecta Gmail cuando quieras importar movimientos desde notificaciones bancarias.';
    }
    return `Cuenta: ${this.gmail.email || 'Gmail'} | Ultima actualizacion: ${this.lastSyncLabel()}`;
  }

  private normalizeSearch(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es').trim();
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
