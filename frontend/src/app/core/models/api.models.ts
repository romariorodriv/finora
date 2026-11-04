export interface AccountProfile {userId:number;name:string;email:string;role:'USER'|'ADMIN';}
export interface AuthResponse {
  role?: 'USER'|'ADMIN';
  token: string;
  userId: number;
  name: string;
  email: string;
}

export interface TransactionRequest {
  currency?: string;
  description: string;
  amount: number;
  date: string;
  category: string;
  type: string;
  merchant?: string;
  recurring: boolean;
}

export interface TransactionResponse extends TransactionRequest {
  dateSource?: string;
  categoryId?: number;
  classificationSource?: string;
  reviewStatus?: string;
  id: number;
  userId: number;
  macroCategory?: string;
  source: string;
  externalId?: string;
  createdAt: string;
}

export interface DashboardResponse {
  currency?: string;
  availableCurrencies?: string[];
  month: string;
  expenses: number;
  income: number;
  balance: number;
  expenseCount: number;
  averageTicket: number;
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
  color?: string;
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
  initialSyncDays?: number;
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
  runId?: string;
  status: 'NOT_STARTED'|'PENDING'|'IMPORTING'|'COMPLETED'|'PARTIAL'|'ERROR'|'CANCELLED';
  periodFrom?: string; periodUntil?: string;
  startedAt?: string; completedAt?: string; updatedAt?: string; nextAttemptAt?: string;
  messagesFound: number; messagesProcessed: number; transactionsCreated: number;
  duplicatesSkipped: number; reviewRequired: number; unsupported: number; rejected: number; failed: number;
  retryCount: number; errorCode?: string; message: string; stale: boolean; hasMorePages: boolean;
}
export interface GmailReviewPage {
  items: {id: number; subject: string; status: string; receivedAt?: string; reason?: string}[];
  total: number; page: number; totalPages: number;
}

export interface AssistantAnswer {
  answer: string;
  suggestions: string[];
  mode: string;
}

export interface PlanningProfile { confirmedBalance: number; reserve: number; nextIncomeDate: string; }
export interface PlanningPaymentInput { name: string; dueDate: string; amount: number; }
export interface PlanningPayment extends PlanningPaymentInput { id: number; paidAt?: string | null; }
export interface PlanningProjection { confirmedBalance: number; committedPayments: number; reserve: number; available: number; dailyLimit: number; days: number; deficit: boolean; note: string; }
export interface PlanningState { balanceConfirmedAt?: string | null; profile: PlanningProfile | null; payments: PlanningPayment[]; projection: PlanningProjection | null; alerts: string[]; }
