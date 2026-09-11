import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from './core/services/api.service';
import { AuthService } from './core/services/auth.service';
import {
  AssistantAnswer,
  DashboardResponse,
  GmailStatus,
  GmailSyncResponse,
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
    this.api.gmailSync().subscribe({
      next: r => {
        this.syncResult = r;
        this.showToast(`${r.transactionsCreated} movimientos importados`);
        this.loadDashboard();
        this.loadGmail();
      },
      error: e => this.showToast(this.message(e))
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

  categoryEntries(): [string, number][] {
    return Object.entries(this.dashboard?.categories || {}).sort((a, b) => Number(b[1]) - Number(a[1]));
  }

  dailyEntries(): [string, number][] {
    return Object.entries(this.dashboard?.daily || {});
  }

  barHeight(value: number): number {
    const max = Math.max(...this.dailyEntries().map(([, v]) => Number(v)), 1);
    return Math.max(8, Number(value) / max * 100);
  }

  lastSyncLabel(): string {
    if (!this.gmail?.lastSyncAt) {
      return 'Aún no has actualizado tus movimientos';
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
    this.api.dashboard().subscribe({ next: d => this.dashboard = d, error: e => this.showToast(this.message(e)) });
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
