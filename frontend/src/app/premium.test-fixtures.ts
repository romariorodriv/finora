// Test-only data: never imported by the application entry point.
import { of } from 'rxjs';
import { ApiService } from './core/services/api.service';
import { AuthService } from './core/services/auth.service';
import { DashboardResponse, PlanningState, TransactionResponse } from './core/models/api.models';
export const testTransaction: TransactionResponse = {
  id: 7, userId: 1, description: 'Cargo de prueba', merchant: 'Comercio QA', amount: 120,
  date: '2026-10-28', category: 'Suscripciones', macroCategory: 'SUSCRIPCIONES',
  type: 'EXPENSE', recurring: true, source: 'MANUAL', createdAt: '2026-10-28T12:00:00Z'
};
export const testDashboard: DashboardResponse = {
  month: '2026-10', expenses: 120, income: 1500, balance: 1380, expenseCount: 1,
  averageTicket: 120, dailyAverage: 4, projection: 124, categories: {Suscripciones: 120},
  macroCategories: [{macroCategory: 'SUSCRIPCIONES', label: 'Suscripciones', total: 120, percentage: 100}],
  merchants: [{merchant: 'Comercio QA', label: 'Comercio QA', total: 120, percentage: 100}],
  daily: {'2026-10-27': 30, '2026-10-28': 90}, transactions: [testTransaction], recurring: [testTransaction]
};
export const testPlan: PlanningState = {
  profile: {confirmedBalance: 1000, reserve: 50, nextIncomeDate: '2099-01-01'},
  payments: [{id: 9, name: 'Pago QA', dueDate: '2098-12-31', amount: 300}],
  projection: {confirmedBalance: 1000, committedPayments: 300, reserve: 50, available: 650, dailyLimit: 65, days: 10, deficit: false, note: 'Proyección de prueba'},
  alerts: ['Pago próximo: Pago QA']
};
export function premiumTestServices() {
  const api = jasmine.createSpyObj<ApiService>('ApiService', ['dashboard','getPlanningState','categories','gmailStatus','transactions','transactionHistory','categoryDetail','merchantDetail','savePlanningProfile','addPlanningPayment','deletePlanningPayment','sendFeedback','gmailSync','gmailSyncStatus','gmailReview','requestDataDeletion','gmailDisconnect','gmailAuthorizationUrl','createTransaction','updateTransaction','deleteTransaction']);
  api.dashboard.and.returnValue(of(testDashboard)); api.getPlanningState.and.returnValue(of(testPlan));
  api.categories.and.returnValue(of(['Suscripciones','Gasolina','Otros']));
  api.gmailStatus.and.returnValue(of({connected: true,configured: true,status: 'CONNECTED',email: 'qa@example.test'}));
  api.transactions.and.returnValue(of([testTransaction]));
  api.transactionHistory.and.returnValue(of({items:[testTransaction],total:1,page:0,totalPages:1}));
  api.categoryDetail.and.returnValue(of({macroCategory: 'SUSCRIPCIONES',label: 'Suscripciones',total: 120,subcategories: [{name: 'Software',total: 120}],transactions: [testTransaction]}));
  api.merchantDetail.and.returnValue(of({merchant: 'Comercio QA',label: 'Comercio QA',total: 120,transactions: [testTransaction]}));
  api.savePlanningProfile.and.returnValue(of(testPlan)); api.addPlanningPayment.and.returnValue(of(testPlan));
  api.deletePlanningPayment.and.returnValue(of({...testPlan,payments: []}));
  api.sendFeedback.and.returnValue(of({id: 3,rating: 5,understoodSpending: 'YES',createdAt: '2026-10-30T12:00:00Z'}));
  const completeJob = {status:'COMPLETED' as const,message:'Importación completada.',stale:false,hasMorePages:false,retryCount:0,unsupported:0,messagesFound: 0,messagesProcessed: 0,transactionsCreated: 0,duplicatesSkipped: 0,reviewRequired: 0,rejected: 0,failed: 0};
  api.gmailSync.and.returnValue(of(completeJob));api.gmailSyncStatus.and.returnValue(of(completeJob));api.gmailReview.and.returnValue(of({items:[],total:0,page:0,totalPages:0}));api.requestDataDeletion.and.returnValue(of({status:'PENDING',message:'Solicitud registrada'}));
  api.gmailDisconnect.and.returnValue(of(undefined)); api.deleteTransaction.and.returnValue(of(undefined));
  api.createTransaction.and.returnValue(of(testTransaction)); api.updateTransaction.and.returnValue(of(testTransaction));
  const auth = {user: {token: 'test-only',userId: 1,name: 'Cuenta QA',email: 'qa@example.test'},isAdmin:false,refreshCurrent:jasmine.createSpy('refreshCurrent').and.returnValue(of({userId:1,name:'Cuenta QA',email:'qa@example.test',role:'USER'})),restoreSession:jasmine.createSpy('restoreSession').and.returnValue(of(null)),googleStart:jasmine.createSpy('googleStart'),linkGoogle:jasmine.createSpy('linkGoogle'),logout: jasmine.createSpy('logout').and.returnValue(of(undefined))} as unknown as AuthService;
  return {api,auth};
}
