import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AssistantAnswer,
  DashboardResponse,
  GmailAuthUrl,
  GmailStatus,
  GmailSyncResponse,
  TransactionRequest,
  TransactionResponse
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/categories`);
  }

  dashboard(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`${this.baseUrl}/dashboard`);
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

  importBankEmail(sender: string, content: string) {
    return this.http.post<{ transaction: TransactionResponse; confidence: number; method: string }>(
      `${this.baseUrl}/import/bank-email`,
      { sender, subject: `Notificación bancaria ${Date.now()}`, content }
    );
  }

  askAssistant(message: string): Observable<AssistantAnswer> {
    return this.http.post<AssistantAnswer>(`${this.baseUrl}/assistant`, { message });
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

  gmailDisconnect(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/integrations/gmail`);
  }
}
