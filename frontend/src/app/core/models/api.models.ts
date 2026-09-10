export interface AuthResponse {
  token: string;
  userId: number;
  name: string;
  email: string;
}

export interface TransactionRequest {
  description: string;
  amount: number;
  date: string;
  category: string;
  type: string;
  merchant?: string;
  recurring: boolean;
}

export interface TransactionResponse extends TransactionRequest {
  id: number;
  userId: number;
  source: string;
  externalId?: string;
  createdAt: string;
}

export interface DashboardResponse {
  month: string;
  expenses: number;
  income: number;
  balance: number;
  dailyAverage: number;
  projection: number;
  categories: Record<string, number>;
  daily: Record<string, number>;
  transactions: TransactionResponse[];
  recurring: TransactionResponse[];
}

export interface GmailStatus {
  connected: boolean;
  configured: boolean;
  email?: string;
  status: string;
  lastSyncAt?: string;
}

export interface GmailAuthUrl {
  authorizationUrl: string;
  configured: boolean;
}

export interface GmailSyncResponse {
  messagesFound: number;
  messagesProcessed: number;
  transactionsCreated: number;
  duplicatesSkipped: number;
  reviewRequired: number;
  rejected: number;
  failed: number;
}

export interface AssistantAnswer {
  answer: string;
  suggestions: string[];
  mode: string;
}
