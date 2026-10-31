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
  it('renders balance, income, expense, daily chart and recurring charges from API data', () => {
    expect(root().querySelector('.balance-amount')?.textContent).toContain('1,380.00');
    expect(root().querySelector('.metrics')?.textContent).toContain('1,500.00');
    expect(root().querySelector('.line-plot polyline')?.getAttribute('points')).toContain(',');
    expect(root().querySelector('.recurring-panel')?.textContent).toContain('Comercio QA');
    expect(root().querySelector('.planning-feature')?.textContent).toContain('650.00');
  });
  it('keeps all five mobile destinations wired to working views and current-page states', () => {
    for (const [index,view] of ['dashboard','planning','movements','import','feedback'].entries()) {
      click(`.mobile-nav button:nth-child(${index+1})`);
      expect(fixture.componentInstance.view).toBe(view as typeof fixture.componentInstance.view);
      expect(root().querySelectorAll('.mobile-nav [aria-current="page"]').length).toBe(1);
    }
    expect(services.api.transactions).toHaveBeenCalled();expect(services.api.gmailStatus).toHaveBeenCalled();
  });
  it('reloads persisted planning state when returning from the planner', () => {
    click('.sidebar nav button:nth-child(2)');
    expect(root().querySelector('app-planning .available-amount')?.textContent).toContain('650.00');
    services.api.getPlanningState.and.returnValue(of({...testPlan,projection: {...testPlan.projection!,available: 725}}));
    click('.sidebar nav button:nth-child(1)');
    expect(root().querySelector('.planning-feature')?.textContent).toContain('725.00');
  });
  it('preserves category and merchant drilldowns, closes with Escape and restores focus', () => {
    const category=root().querySelector('.category-legend button') as HTMLButtonElement;category.focus();category.click();fixture.detectChanges();
    expect(services.api.categoryDetail).toHaveBeenCalledWith('SUSCRIPCIONES');
    expect(root().querySelector('[role="dialog"]')?.textContent).toContain('Software');
    expect(document.activeElement?.className).toBe('drawer-close');
    (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));
    expect(document.activeElement?.className).toBe('drawer-close');
    document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));fixture.detectChanges();
    expect(root().querySelector('[role="dialog"]')).toBeNull();expect(document.activeElement).toBe(category);
    click('.merchant-legend button');expect(services.api.merchantDetail).toHaveBeenCalledWith('Comercio QA');
    click('.drawer-close');expect(root().querySelector('[role="dialog"]')).toBeNull();
  });
  it('preserves movement filtering, editing, recurring state and deletion', () => {
    fixture.componentInstance.go('movements');fixture.detectChanges();
    fixture.componentInstance.movementFilters.search='does-not-match';fixture.detectChanges();expect(root().querySelectorAll('.tx-actions').length).toBe(0);
    fixture.componentInstance.resetMovementFilters();fixture.detectChanges();expect(root().querySelectorAll('.tx-actions').length).toBe(1);
    click('.tx-actions .ghost');expect(fixture.componentInstance.txForm.recurring).toBeTrue();
    fixture.componentInstance.saveTransaction();expect(services.api.updateTransaction).toHaveBeenCalledWith(7,jasmine.objectContaining({recurring:true}));
    click('.tx-actions .danger');expect(services.api.deleteTransaction).toHaveBeenCalledWith(7);
  });
  it('preserves Gmail synchronization and its status confirmation', () => {
    click('.connection-banner button');expect(services.api.gmailSync).toHaveBeenCalled();
    expect(fixture.componentInstance.gmailUpdateStatus).toContain('Todo esta al dia');
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
    expect(root().querySelector('.line-plot')).toBeNull();expect(root().querySelector('.feature-amount')).toBeNull();expect(root().textContent).toContain('Crear mi plan');
  });
  it('shows loading and retry states for actual read failures', () => {
    const dashboard=new Subject<DashboardResponse>();services.api.dashboard.and.returnValue(dashboard);
    fixture.componentInstance.loadDashboard();expect(fixture.componentInstance.dashboardLoading).toBeTrue();
    dashboard.error({error:{message:'No disponible'}});fixture.detectChanges();expect(root().querySelector('[role="alert"]')?.textContent).toContain('No disponible');
    services.api.getPlanningState.and.returnValue(throwError(()=>new Error('offline')));fixture.componentInstance.loadPlanningSummary();fixture.detectChanges();expect(root().querySelector('.summary-error')?.textContent).toContain('Reintentar');
  });
  it('cancels a pending summary and clears planning data when logging out', () => {
    const request=new Subject<PlanningState>();services.api.getPlanningState.and.returnValue(request);fixture.componentInstance.loadPlanningSummary();
    fixture.componentInstance.logout();request.next(testPlan);expect(services.auth.logout).toHaveBeenCalled();expect(fixture.componentInstance.planningSummary).toBeUndefined();
  });
  it('preserves registration password confirmation and login error handling', () => {
    fixture.componentInstance.registerForm={name:'QA',email:'qa@example.test',password:'first123',confirmPassword:'other123'};
    fixture.componentInstance.register();expect(services.auth.register).not.toHaveBeenCalled();expect(fixture.componentInstance.error).toContain('no coinciden');
    spyOn(console,'error');(services.auth.login as jasmine.Spy).and.returnValue(throwError(()=>({status:401})));
    fixture.componentInstance.login();expect(fixture.componentInstance.error).toContain('Revisa tu correo');
  });
  it('prevents repeated transaction writes while the server is processing them', () => {
    const pending = new Subject<typeof testTransaction>();services.api.createTransaction.and.returnValue(pending);
    fixture.componentInstance.saveTransaction();fixture.componentInstance.saveTransaction();
    expect(services.api.createTransaction).toHaveBeenCalledTimes(1);expect(fixture.componentInstance.savingTransaction).toBeTrue();
    pending.next(testTransaction);pending.complete();expect(fixture.componentInstance.savingTransaction).toBeFalse();
  });
});
