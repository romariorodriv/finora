import { provideRouter } from '@angular/router';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AppComponent } from './app.component';
import { ApiService } from './core/services/api.service';
import { AuthService } from './core/services/auth.service';
import { PlanningState, DashboardResponse } from './core/models/api.models';
import { premiumTestServices, testDashboard, testPlan, testTransaction } from './premium.test-fixtures';

describe('Premium UI functional flows', () => {
  let fixture: ComponentFixture<AppComponent>;
  let services: ReturnType<typeof premiumTestServices>;
  const root = () => fixture.nativeElement as HTMLElement;
  const click = (selector: string) => { (root().querySelector(selector) as HTMLButtonElement).click(); fixture.detectChanges(); };
  beforeEach(() => {
    services=premiumTestServices();
    TestBed.configureTestingModule({imports: [AppComponent],providers: [provideRouter([]),{provide: ApiService,useValue: services.api},{provide: AuthService,useValue: services.auth}]});
    fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();
  });
  it('does not delete a user unless they type the irreversible confirmation phrase', () => {
    const prompt=spyOn(window,'prompt').and.returnValue(null);
    fixture.componentInstance.requestDataDeletion();
    expect(prompt).toHaveBeenCalled();
    expect(services.api.requestDataDeletion).not.toHaveBeenCalled();
    prompt.and.returnValue('eliminar');
    fixture.componentInstance.requestDataDeletion();
    expect(services.api.requestDataDeletion).not.toHaveBeenCalled();
  });
  it('renders recorded monthly metrics, expenses and confirmed planning availability from API data', () => {
    expect(root().querySelector('.finora-home-summary-balance')?.textContent).toContain('1,380.00');
    expect(root().querySelector('.finora-home-summary')?.textContent).toContain('1,500.00');
    expect(root().querySelector('.finora-home-donut')?.textContent).toContain('120.00');
    expect(root().querySelector('.finora-home-recent')?.textContent).toContain('Comercio QA');
    expect(root().querySelector('.finora-home-headline')?.textContent).toContain('650.00');
  });
  it('keeps all five mobile destinations wired to working views and current-page states', () => {
    for (const [index,view] of ['dashboard','planning','movements','import','feedback'].entries()) {
      click(`.mobile-nav button:nth-child(${index+1})`);
      expect(fixture.componentInstance.view).toBe(view as typeof fixture.componentInstance.view);
      expect(root().querySelectorAll('.mobile-nav [aria-current="page"]').length).toBe(1);
    }
    expect(services.api.transactionHistory).toHaveBeenCalled();expect(services.api.gmailStatus).toHaveBeenCalled();
  });
  it('reloads persisted planning state when returning from the planner', () => {
    click('.sidebar nav button:nth-child(2)');
    expect(root().querySelector('app-planning .available-amount')?.textContent).toContain('650.00');
    services.api.getPlanningState.and.returnValue(of({...testPlan,projection: {...testPlan.projection!,available: 725}}));
    click('.sidebar nav button:nth-child(1)');
    expect(root().querySelector('.finora-home-headline')?.textContent).toContain('725.00');
  });
  it('preserves category and merchant drilldowns, closes with Escape and restores focus', () => {
    const category=root().querySelector('.finora-home-category-list button') as HTMLButtonElement;category.focus();category.click();fixture.detectChanges();
    expect(services.api.categoryDetail).toHaveBeenCalledWith('SUSCRIPCIONES');
    expect(root().querySelector('[role="dialog"]')?.textContent).toContain('Software');
    expect(document.activeElement?.className).toBe('drawer-close');
    (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));
    expect(document.activeElement?.className).toBe('drawer-close');
    document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));fixture.detectChanges();
    expect(root().querySelector('[role="dialog"]')).toBeNull();expect(document.activeElement).toBe(category);
    fixture.componentInstance.openMerchant(fixture.componentInstance.merchantDistribution()[0]);fixture.detectChanges();expect(services.api.merchantDetail).toHaveBeenCalledWith('Comercio QA');
    click('.drawer-close');expect(root().querySelector('[role="dialog"]')).toBeNull();
  });
  it('preserves movement filtering, editing, recurring state and deletion', () => {
    fixture.componentInstance.go('movements');fixture.detectChanges();
    services.api.transactionHistory.and.returnValue(of({items:[],total:0,page:0,totalPages:0}));
    fixture.componentInstance.movementFilters.search='does-not-match';fixture.componentInstance.updateMovementFilters();fixture.detectChanges();expect(root().querySelectorAll('.tx-actions').length).toBe(0);
    services.api.transactionHistory.and.returnValue(of({items:[testTransaction],total:1,page:0,totalPages:1}));
    fixture.componentInstance.resetMovementFilters();fixture.detectChanges();expect(root().querySelectorAll('.tx-actions').length).toBe(1);
    click('.tx-actions .ghost');expect(fixture.componentInstance.txForm.recurring).toBeTrue();
    fixture.componentInstance.saveTransaction();expect(services.api.updateTransaction).toHaveBeenCalledWith(7,jasmine.objectContaining({recurring:true}));
    click('.tx-actions .danger');expect(services.api.deleteTransaction).toHaveBeenCalledWith(7);
  });
  it('preserves Gmail synchronization and its status confirmation', () => {
    fixture.componentInstance.go('import');fixture.detectChanges();click('.connection-actions .primary');expect(services.api.gmailSync).toHaveBeenCalled();
    expect(fixture.componentInstance.gmailUpdateStatus).toContain('Importación completada');
    fixture.componentInstance.go('import');fixture.detectChanges();click('.connection-actions .ghost');expect(services.api.gmailDisconnect).toHaveBeenCalled();
  });
  it('validates and submits feedback then renders confirmation', () => {
    fixture.componentInstance.go('feedback');fixture.detectChanges();fixture.componentInstance.submitFeedback();expect(services.api.sendFeedback).not.toHaveBeenCalled();
    fixture.componentInstance.feedbackForm={rating:5,understoodSpending:'YES'};fixture.componentInstance.submitFeedback();fixture.detectChanges();
    expect(services.api.sendFeedback).toHaveBeenCalledWith({rating:5,understoodSpending:'YES'});expect(root().querySelector('.feedback-thanks')?.textContent).toContain('Gracias por compartir');
  });
  it('uses honest empty states without rendering a fabricated balance or chart', () => {
    services.api.dashboard.and.returnValue(of({...testDashboard,expenses:0,income:0,balance:0,daily:{},transactions:[],recurring:[],macroCategories:[],merchants:[]}));
    services.api.getPlanningState.and.returnValue(of({profile:null,payments:[],projection:null,alerts:[]}));
    fixture.componentInstance.loadDashboard();fixture.componentInstance.loadPlanningSummary();fixture.detectChanges();
    expect(root().querySelector('.finora-home-donut')).toBeNull();expect(root().querySelector('.finora-home-headline')).toBeNull();expect(root().textContent).toContain('Crear mi plan');
  });
  it('shows loading and retry states for actual read failures', () => {
    const dashboard=new Subject<DashboardResponse>();services.api.dashboard.and.returnValue(dashboard);
    fixture.componentInstance.loadDashboard();expect(fixture.componentInstance.dashboardLoading).toBeTrue();
    dashboard.error({error:{message:'No disponible'}});fixture.detectChanges();expect(root().querySelector('[role="alert"]')?.textContent).toContain('No disponible');
    services.api.getPlanningState.and.returnValue(throwError(()=>new Error('offline')));fixture.componentInstance.loadPlanningSummary();fixture.detectChanges();expect(root().querySelector('.finora-home-hero-error')?.textContent).toContain('Reintentar');
  });
  it('cancels a pending summary and clears planning data when logging out', () => {
    const request=new Subject<PlanningState>();services.api.getPlanningState.and.returnValue(request);fixture.componentInstance.loadPlanningSummary();
    fixture.componentInstance.logout();request.next(testPlan);expect(services.auth.logout).toHaveBeenCalled();expect(fixture.componentInstance.planningSummary).toBeUndefined();
  });
  it('shows a safe Google sign-in error and never exposes a password login', () => {
    (services.auth.googleStart as jasmine.Spy).and.returnValue(throwError(()=>({error:{message:'Google no está configurado'}})));
    fixture.componentInstance.continueWithGoogle();
    expect(fixture.componentInstance.error).toContain('Google no está configurado');
    expect(fixture.componentInstance.authSubmitting).toBeFalse();
  });
  it('prevents repeated transaction writes while the server is processing them', () => {
    const pending = new Subject<typeof testTransaction>();services.api.createTransaction.and.returnValue(pending);
    fixture.componentInstance.saveTransaction();fixture.componentInstance.saveTransaction();
    expect(services.api.createTransaction).toHaveBeenCalledTimes(1);expect(fixture.componentInstance.savingTransaction).toBeTrue();
    pending.next(testTransaction);pending.complete();expect(fixture.componentInstance.savingTransaction).toBeFalse();
  });
  it('offers Gmail reconnection after revoked authorization without logging out of Sarela', () => {
    services.api.gmailStatus.and.returnValue(of({
      connected: false, configured: true, status: 'REAUTHORIZATION_REQUIRED', email: 'qa@example.test'
    }));
    services.api.gmailSync.and.returnValue(throwError(() => ({
      status: 401, error: {code: 'GMAIL_RECONNECT_REQUIRED', message: 'Vuelve a conectar Gmail'}
    })));
    fixture.componentInstance.go('import');
    fixture.componentInstance.syncGmail();
    fixture.detectChanges();
    expect(fixture.componentInstance.syncingGmail).toBeFalse();
    expect(fixture.componentInstance.gmail?.connected).toBeFalse();
    expect(root().querySelector('.connection-actions')?.textContent).toContain('Conectar Gmail');
    expect(root().querySelector('.gmail-panel, .connection-panel, .connection-card')?.textContent
      || root().textContent).toContain('venció o fue revocada');
    expect(services.auth.logout).not.toHaveBeenCalled();
  });

  it('keeps Gmail connected on a temporary Google failure and releases the sync button', () => {
    services.api.gmailSync.and.returnValue(throwError(() => ({
      status: 503, error: {code: 'GMAIL_TEMPORARILY_UNAVAILABLE', message: 'Google no está disponible'}
    })));
    fixture.componentInstance.syncGmail();
    fixture.detectChanges();
    expect(fixture.componentInstance.gmail?.connected).toBeTrue();
    expect(fixture.componentInstance.gmailUpdateStatus).toContain('Google no está disponible');
    expect(fixture.componentInstance.syncingGmail).toBeFalse();
    expect(services.auth.logout).not.toHaveBeenCalled();
  });

  it('explains Gmail before asking Google for consent and allows skipping it', () => {
    fixture.componentInstance.go('import');fixture.componentInstance.connectGmail();fixture.detectChanges();
    expect(root().querySelector('.gmail-consent')?.textContent).toContain('solo lectura');
    expect(root().querySelector('.gmail-consent')?.textContent).toContain('no solo de correos bancarios');
    expect(root().querySelector('.gmail-consent')?.textContent).toContain('90');
    expect(services.api.gmailAuthorizationUrl).not.toHaveBeenCalled();
    const skip=Array.from(root().querySelectorAll<HTMLButtonElement>('.gmail-consent button')).find(button=>button.textContent?.includes('Ahora no'))!;
    skip.click();fixture.detectChanges();
    expect(fixture.componentInstance.view).toBe('dashboard');
    expect(services.api.gmailAuthorizationUrl).not.toHaveBeenCalled();
  });

  it('shows pending import instead of presenting an empty dashboard as no expenses', () => {
    services.api.dashboard.and.returnValue(of({...testDashboard,expenses:0,income:0,balance:0,transactions:[],recurring:[],macroCategories:[],merchants:[],daily:{}}));
    fixture.componentInstance.loadDashboard();
    services.api.gmailSyncStatus.and.returnValue(of({
      status:'PENDING',runId:'queued',message:'Importación pendiente.',messagesFound:0,messagesProcessed:0,transactionsCreated:0,
      duplicatesSkipped:0,reviewRequired:0,unsupported:0,rejected:0,failed:0,retryCount:0,stale:true,hasMorePages:false
    }));
    fixture.componentInstance.loadGmailJob();fixture.detectChanges();
    expect(root().querySelector('.import-wait')?.textContent).toContain('no significa que no tengas gastos');
    expect(root().querySelector('.dashboard-view')).toBeNull();
    fixture.destroy();
  });

  it('labels partial imports and exposes real review counts', () => {
    fixture.componentInstance.go('import');
    services.api.gmailSyncStatus.and.returnValue(of({
      status:'PARTIAL',runId:'partial',message:'Importación parcial.',messagesFound:5,messagesProcessed:4,transactionsCreated:2,
      duplicatesSkipped:1,reviewRequired:1,unsupported:0,rejected:0,failed:1,retryCount:0,stale:true,hasMorePages:false
    }));
    fixture.componentInstance.loadGmailJob();fixture.detectChanges();
    expect(root().querySelector('.gmail-job')?.textContent).toContain('Parcial');
    expect(root().querySelector('.gmail-job')?.textContent).toContain('información está incompleta');
    expect(root().querySelector('.import-counts')?.textContent).toContain('Por revisar');
    expect(fixture.componentInstance.syncResult?.transactionsCreated).toBe(2);
  });

  it('keeps existing data visible when an import fails instead of claiming everything is up to date', () => {
    services.api.gmailSyncStatus.and.returnValue(of({
      status:'ERROR',message:'No se pudo completar la importación.',messagesFound:0,messagesProcessed:0,transactionsCreated:0,
      duplicatesSkipped:0,reviewRequired:0,unsupported:0,rejected:0,failed:0,retryCount:6,stale:true,hasMorePages:false
    }));
    fixture.componentInstance.loadGmailJob();fixture.detectChanges();
    expect(fixture.componentInstance.dashboard?.expenses).toBe(testDashboard.expenses);
    expect(root().querySelector('.import-notice')?.textContent).toContain('solo movimientos registrados');
    expect(root().querySelector('.import-notice')?.textContent).not.toContain('al día');
  });

  it('warns about uninterpreted messages even when the candidate scan has finished', () => {
    services.api.gmailSyncStatus.and.returnValue(of({
      status:'COMPLETED',message:'Lectura completada.',messagesFound:3,messagesProcessed:3,transactionsCreated:0,
      duplicatesSkipped:0,reviewRequired:1,unsupported:2,rejected:0,failed:0,retryCount:0,stale:false,hasMorePages:false
    }));
    fixture.componentInstance.loadGmailJob();fixture.detectChanges();
    const notice=root().querySelector('.import-notice')?.textContent || '';
    expect(notice).toContain('1 correos por revisar');
    expect(notice).toContain('2 no interpretados');
    expect(notice).toContain('no representa todo tu historial bancario');
  });
  it('loads server pages, resets the page for filters and rejects reversed dates', () => {
    services.api.transactionHistory.and.returnValue(of({items:[testTransaction],total:25,page:0,totalPages:3}));
    const c=fixture.componentInstance;c.go('movements');c.nextMovementPage();
    expect(services.api.transactionHistory).toHaveBeenCalledWith(c.movementFilters,1,12);
    c.movementFilters.from='2026-08-01';c.movementFilters.to='2026-10-31';c.updateMovementFilters();
    expect(c.movementPage).toBe(1);
    expect(services.api.transactionHistory).toHaveBeenCalledWith(jasmine.objectContaining({from:'2026-08-01',to:'2026-10-31'}),0,12);
    services.api.transactionHistory.calls.reset();c.movementFilters.from='2027-01-01';c.updateMovementFilters();
    expect(services.api.transactionHistory).not.toHaveBeenCalled();expect(c.movementError).toContain('fecha inicial');
  });
  it('cancels stale history requests and renders loading, error and empty results', () => {
    const first=new Subject<{items:typeof testTransaction[];total:number;page:number;totalPages:number}>();
    services.api.transactionHistory.and.returnValue(first);
    const c=fixture.componentInstance;c.go('movements');fixture.detectChanges();
    expect(c.transactionsLoading).toBeTrue();
    const second=new Subject<{items:typeof testTransaction[];total:number;page:number;totalPages:number}>();
    services.api.transactionHistory.and.returnValue(second);c.updateMovementFilters();
    first.next({items:[testTransaction],total:1,page:0,totalPages:1});expect(c.transactions).toEqual([]);
    second.error({error:{message:'Historial no disponible'}});fixture.detectChanges();
    expect(c.transactionsLoading).toBeFalse();expect(root().querySelector('[role="alert"]')?.textContent).toContain('Historial no disponible');
    services.api.transactionHistory.and.returnValue(of({items:[],total:0,page:0,totalPages:0}));c.loadTransactions();fixture.detectChanges();
    expect(c.movementError).toBe('');expect(root().textContent).toContain('Registra tu primer movimiento');
  });

});
