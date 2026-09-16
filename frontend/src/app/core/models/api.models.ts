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
  macroCategory?: string;
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
  macroCategories?: MacroCategorySummary[];
  merchants?: MerchantSummary[];
  insightTitle?: string;
  insightBody?: string;
  daily: Record<string, number>;
  transactions?: TransactionResponse[];
  recurring?: TransactionResponse[];
}

export interface MacroCategorySummary {
  macroCategory: string;
  label: string;
  total: number;
  percentage: number;
}

export interface MerchantSummary {
  merchant: string;
  label: string;
  total: number;
  percentage: number;
}

export interface SubcategorySummary {
  name: string;
  total: number;
}

export interface CategoryDetailResponse {
  macroCategory: string;
  label: string;
  total: number;
  subcategories: SubcategorySummary[];
  transactions: TransactionResponse[];
}

export interface MerchantDetailResponse {
  merchant: string;
  label: string;
  total: number;
  transactions: TransactionResponse[];
}

export interface FeedbackRequest {
  rating: number;
  understoodSpending: 'YES' | 'PARTIALLY' | 'NO' | '';
  liked?: string;
  improvement?: string;
  wantedFeature?: string;
}

export interface FeedbackResponse extends Omit<FeedbackRequest, 'understoodSpending'> {
  id: number;
  understoodSpending: 'YES' | 'PARTIALLY' | 'NO';
  createdAt: string;
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
