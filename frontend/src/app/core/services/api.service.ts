import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { normalizeDashboard } from '../dashboard.utils';
import { environment } from '../../../environments/environment';
import {
  PlanningState, PlanningProfile, PlanningPaymentInput,
  AssistantAnswer,
  CategoryDetailResponse,
  DashboardResponse,
  FeedbackRequest,
  FeedbackResponse,
  GmailAuthUrl,
  GmailStatus,
  GmailSyncResponse,
  MerchantDetailResponse,
  TransactionRequest,
  TransactionResponse
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private dashboardCurrency = '';
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  getPlanningState(): Observable<PlanningState> { return this.http.get<PlanningState>(`${this.baseUrl}/planning/state`); }
  savePlanningProfile(profile: PlanningProfile): Observable<PlanningState> { return this.http.put<PlanningState>(`${this.baseUrl}/planning/state`, profile); }
  addPlanningPayment(payment: PlanningPaymentInput): Observable<PlanningState> { return this.http.post<PlanningState>(`${this.baseUrl}/planning/payments`, payment); }
  completePlanningPayment(id: number, resultingBalance: number): Observable<PlanningState> { return this.http.post<PlanningState>(`${this.baseUrl}/planning/payments/${id}/complete`, {resultingBalance}); }
  deletePlanningPayment(id: number): Observable<PlanningState> { return this.http.delete<PlanningState>(`${this.baseUrl}/planning/payments/${id}`); }

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/categories`);
  }

  dashboard(currency?:string): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`${this.baseUrl}/dashboard`,{params:currency?{currency}:{}}).pipe(map(response=>{
      this.dashboardCurrency=response.currency||'';return normalizeDashboard(response);
    }));
  }

  categoryDetail(macroCategory: string): Observable<CategoryDetailResponse> {
    return this.http.get<CategoryDetailResponse>(`${this.baseUrl}/dashboard/categories/${macroCategory}`,{params:this.dashboardCurrency?{currency:this.dashboardCurrency}:{}}).pipe(
      map(detail => ({
        ...detail,
        total: Number(detail.total),
        subcategories: (detail.subcategories ?? []).map(item => ({ ...item, total: Number(item.total) })),
        transactions: detail.transactions ?? []
      }))
    );
  }

  merchantDetail(merchant: string): Observable<MerchantDetailResponse> {
    return this.http.get<MerchantDetailResponse>(`${this.baseUrl}/dashboard/merchants/${encodeURIComponent(merchant)}`,{params:this.dashboardCurrency?{currency:this.dashboardCurrency}:{}}).pipe(
      map(detail => ({
        ...detail,
        total: Number(detail.total),
        transactions: detail.transactions ?? []
      }))
    );
  }

  transactionHistory(filters: {search: string; from: string; to: string; category: string; type: string; currency: string}, page: number, size: number): Observable<{items: TransactionResponse[]; total: number; page: number; totalPages: number}> {
    const params: Record<string,string> = {page: String(page),size: String(size)};
    for (const [key,value] of Object.entries(filters)) if(value) params[key] = value;
    return this.http.get<{items: TransactionResponse[]; total: number; page: number; totalPages: number}>(`${this.baseUrl}/transactions/history`,{params});
  }

  transactions(): Observable<TransactionResponse[]> {
    return this.http.get<TransactionResponse[]>(`${this.baseUrl}/transactions`);
  }

  createTransaction(request: TransactionRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.baseUrl}/transactions`, request);
  }

  updateTransaction(id: number, request: TransactionRequest): Observable<TransactionResponse> {
    return this.http.put<TransactionResponse>(`${this.baseUrl}/transactions/${id}`, request);
  }

  deleteTransaction(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/transactions/${id}`);
  }

  importBankEmail(sender: string, content: string, date: string) {
    return this.http.post<{ transaction: TransactionResponse; confidence: number; method: string }>(
      `${this.baseUrl}/import/bank-email`,
      { sender, subject: `Notificación bancaria ${Date.now()}`, content, date }
    );
  }

  askAssistant(message: string, currency?: string): Observable<AssistantAnswer> {
    return this.http.post<AssistantAnswer>(`${this.baseUrl}/assistant`, { message, currency });
  }

  sendFeedback(request: FeedbackRequest): Observable<FeedbackResponse> {
    return this.http.post<FeedbackResponse>(`${this.baseUrl}/feedback`, request);
  }

  gmailStatus(): Observable<GmailStatus> {
    return this.http.get<GmailStatus>(`${this.baseUrl}/integrations/gmail/status`);
  }

  gmailAuthorizationUrl(): Observable<GmailAuthUrl> {
    return this.http.get<GmailAuthUrl>(`${this.baseUrl}/integrations/gmail/authorization-url`);
  }

  gmailSync(): Observable<GmailSyncResponse> {
    return this.http.post<GmailSyncResponse>(`${this.baseUrl}/integrations/gmail/sync`, {});
  }

  gmailSyncStatus(): Observable<GmailSyncResponse> {
    return this.http.get<GmailSyncResponse>(this.baseUrl + '/integrations/gmail/sync/status');
  }
  gmailReview(page=0) {
    return this.http.get<import('../models/api.models').GmailReviewPage>(this.baseUrl + '/integrations/gmail/review', {params: {page}});
  }
  requestDataDeletion() {
    return this.http.post<{status:string;message:string}>(this.baseUrl + '/account/deletion-request', {confirmed:true});
  }
  gmailDisconnect(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/integrations/gmail`);
  }
}
