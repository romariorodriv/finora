import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ArcElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Filler,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip
} from 'chart.js';
import { finalize } from 'rxjs';
import { ApiService } from './core/services/api.service';
import { AuthService } from './core/services/auth.service';
import {
  calculateCategoryDistribution,
  calculateDailySpending,
  CategorySpendingPoint,
  generateFinancialInsight
} from './core/dashboard.utils';
import {
  AssistantAnswer,
  DashboardResponse,
  GmailStatus,
  GmailSyncResponse,
  TransactionRequest,
  TransactionResponse
} from './core/models/api.models';

Chart.register(
  ArcElement,
  CategoryScale,
  DoughnutController,
  Filler,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip
);

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('spendingChart') set spendingChartCanvas(canvas: ElementRef<HTMLCanvasElement> | undefined) {
    this.spendingCanvas = canvas;
    if (canvas) {
      setTimeout(() => this.renderSpendingChart());
    }
  }

  @ViewChild('categoryChart') set categoryChartCanvas(canvas: ElementRef<HTMLCanvasElement> | undefined) {
    this.categoryCanvas = canvas;
    if (canvas) {
      setTimeout(() => this.renderCategoryChart());
    }
  }

  view: 'dashboard' | 'movements' | 'import' | 'assistant' = 'dashboard';
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
  dashboard?: DashboardResponse;
  transactions: TransactionResponse[] = [];
  categories: string[] = [];
  gmail?: GmailStatus;
  syncResult?: GmailSyncResponse;
  chatInput = '';
  messages: { role: 'bot' | 'user'; text: string }[] = [
    { role: 'bot', text: 'Hola. Puedo ayudarte a entender tus gastos sin juzgarte. ¿Qué te gustaría saber?' }
  ];
  suggestions = ['¿En qué gasté más?', '¿Cuánto llevo este mes?', '¿Qué pagos se repiten?'];
  error = '';
  toast = '';
  syncingGmail = false;
  gmailUpdateStatus = '';
  selectedSpendingRange: 7 | 30 = 30;
  private spendingChart?: Chart<'line'>;
  private categoryChart?: Chart<'doughnut'>;
  private spendingCanvas?: ElementRef<HTMLCanvasElement>;
  private categoryCanvas?: ElementRef<HTMLCanvasElement>;
  private readonly chartColors = ['#0f3d30', '#276f5b', '#48b88e', '#78e6b0', '#a9d9bf', '#c0d4cc'];

  constructor(public readonly auth: AuthService, private readonly api: ApiService) {}

  ngOnInit(): void {
    if (this.auth.user) {
      this.loadApp();
    }
    const gmailParam = new URLSearchParams(location.search).get('gmail');
    if (gmailParam) {
      history.replaceState({}, '', location.pathname);
      this.view = 'dashboard';
      this.showToast(gmailParam === 'connected' ? 'Gmail conectado correctamente' : 'No se autorizó la conexión');
    }
  }

  ngAfterViewInit(): void {
    this.renderDashboardCharts();
  }

  ngOnDestroy(): void {
    this.spendingChart?.destroy();
    this.categoryChart?.destroy();
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
      this.error = 'Las contraseñas no coinciden.';
      return;
    }
    this.auth.register(this.registerForm.name, this.registerForm.email, this.registerForm.password).subscribe({
      next: () => {
        this.showToast('Tu cuenta está lista');
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
  }

  go(view: typeof this.view): void {
    this.view = view;
    if (view === 'movements') {
      this.loadTransactions();
    }
    if (view === 'import') {
      this.loadGmail();
    }
    if (view === 'dashboard') {
      setTimeout(() => this.renderDashboardCharts());
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
          : 'Todo está al día. No encontramos movimientos nuevos.';
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

  ask(text = this.chatInput): void {
    const message = text.trim();
    if (!message) {
      return;
    }
    this.messages.push({ role: 'user', text: message });
    this.chatInput = '';
    this.api.askAssistant(message).subscribe({
      next: (answer: AssistantAnswer) => {
        this.messages.push({ role: 'bot', text: answer.answer });
        this.suggestions = answer.suggestions;
      },
      error: e => this.showToast(this.message(e))
    });
  }

  money(value: number | string | undefined): string {
    return `S/ ${Number(value || 0).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  selectSpendingRange(range: 7 | 30): void {
    this.selectedSpendingRange = range;
    this.renderSpendingChart();
  }

  hasDailySpending(): boolean {
    return Boolean(this.dashboard && this.dashboard.expenses > 0 && Object.keys(this.dashboard.daily).length > 0);
  }

  categoryDistribution(): CategorySpendingPoint[] {
    return calculateCategoryDistribution(this.dashboard?.categories ?? {}, this.chartColors);
  }

  financialInsight(): string {
    return generateFinancialInsight(this.categoryDistribution(), this.dashboard?.expenses ?? 0);
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
      return 'Aún no has actualizado tus movimientos';
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
      next: d => {
        this.dashboard = d;
        setTimeout(() => this.renderDashboardCharts());
      },
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

  private renderDashboardCharts(): void {
    if (this.view !== 'dashboard' || !this.dashboard) {
      return;
    }
    this.renderSpendingChart();
    this.renderCategoryChart();
  }

  private renderSpendingChart(): void {
    if (!this.spendingCanvas || !this.dashboard) {
      return;
    }
    const points = calculateDailySpending(this.dashboard.daily, this.selectedSpendingRange);
    this.spendingChart?.destroy();
    this.spendingChart = new Chart(this.spendingCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: points.map(point => point.label),
        datasets: [{
          data: points.map(point => point.value),
          borderColor: '#276f5b',
          backgroundColor: 'rgba(120, 230, 176, 0.18)',
          borderWidth: 2,
          fill: true,
          tension: 0.32,
          pointRadius: 0,
          pointHoverRadius: 4,
          pointBackgroundColor: '#0f3d30'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { intersect: false, mode: 'index' },
        plugins: {
          legend: { display: false },
          tooltip: {
            displayColors: false,
            callbacks: {
              title: items => {
                const date = points[items[0].dataIndex]?.date;
                return date
                  ? new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long' }).format(new Date(`${date}T12:00:00`))
                  : '';
              },
              label: item => this.money(Number(item.raw))
            }
          }
        },
        scales: {
          x: { grid: { display: false }, ticks: { maxTicksLimit: 10, color: '#708078' } },
          y: {
            beginAtZero: true,
            border: { display: false },
            grid: { color: '#e3eae5' },
            ticks: { color: '#708078', callback: value => `S/ ${Number(value).toLocaleString('es-PE')}` }
          }
        }
      }
    });
  }

  private renderCategoryChart(): void {
    if (!this.categoryCanvas || !this.dashboard) {
      return;
    }
    const categories = this.categoryDistribution();
    this.categoryChart?.destroy();
    this.categoryChart = new Chart(this.categoryCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: categories.map(item => item.name),
        datasets: [{
          data: categories.map(item => item.value),
          backgroundColor: categories.map(item => item.color),
          borderColor: '#ffffff',
          borderWidth: 2,
          hoverOffset: 3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '70%',
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: item => `${item.label}: ${this.money(Number(item.raw))}` } }
        }
      }
    });
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

  private showToast(message: string): void {
    this.toast = message;
    setTimeout(() => this.toast = '', 2600);
  }

  private message(error: any): string {
    if (error?.status === 0) {
      return 'No pudimos conectarnos con Sarela. Intenta nuevamente.';
    }
    return error?.error?.message || 'Algo salió mal. Intenta nuevamente.';
  }

  private authMessage(error: any, mode: 'login' | 'register'): string {
    if (error?.status === 0) {
      return 'No pudimos conectarnos con Sarela. Intenta nuevamente.';
    }
    if (mode === 'login' && error?.status === 401) {
      return 'No pudimos iniciar sesión. Revisa tu correo y contraseña.';
    }
    if (mode === 'register' && (error?.status === 409 || error?.error?.code === 'EMAIL_ALREADY_REGISTERED')) {
      return 'Ya existe una cuenta con este correo.';
    }
    return 'Algo salió mal. Intenta nuevamente.';
  }
}
