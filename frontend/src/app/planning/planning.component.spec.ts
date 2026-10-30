import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { PlanningComponent } from './planning.component';
import { ApiService } from '../core/services/api.service';
import { PlanningState } from '../core/models/api.models';

describe('Planning dashboard', () => {
  let api: jasmine.SpyObj<ApiService>;
  const state: PlanningState = {
    profile: { confirmedBalance: 1000, reserve: 50, nextIncomeDate: '2099-01-01' },
    payments: [{id: 7,name: 'Alquiler',dueDate: '2098-12-31',amount: 300}],
    projection: {confirmedBalance: 1000,reserve: 50,available: 650,committedPayments: 300,dailyLimit: 65,days: 10,deficit: false,note: 'Confirmado'},
    alerts: ['Pago próximo: Alquiler']
  };
  beforeEach(() => {
    api=jasmine.createSpyObj('ApiService',['getPlanningState','savePlanningProfile','addPlanningPayment','deletePlanningPayment']);
    api.getPlanningState.and.returnValue(of(state));
    TestBed.configureTestingModule({imports: [PlanningComponent],providers: [{provide: ApiService,useValue: api}]});
  });
  it('restores the persisted profile, payments, projection and alerts in the dashboard', () => {
    const fixture=TestBed.createComponent(PlanningComponent);fixture.detectChanges();
    expect(fixture.componentInstance.planningBalance).toBe(1000);
    expect(fixture.nativeElement.textContent).toContain('Alquiler');
    expect(fixture.nativeElement.textContent).toContain('650.00');
    expect(fixture.nativeElement.textContent).toContain('Pago próximo: Alquiler');
  });
  it('rejects invalid amounts before sending mutations', () => {
    const component=new PlanningComponent(api);
    component.planningBalance=-1;component.previewPlanning();
    component.planningNew={name:'Pago',amount:0,dueDate:'2099-01-01'};component.addObligation();
    expect(api.savePlanningProfile).not.toHaveBeenCalled();
    expect(api.addPlanningPayment).not.toHaveBeenCalled();
    expect(component.planningError).toBeTruthy();
  });
  it('prevents duplicate payment requests and reports failures without clearing saved payments', () => {
    const pending=new Subject<PlanningState>();api.addPlanningPayment.and.returnValue(pending);
    const component=new PlanningComponent(api);component.ngOnInit();
    component.planningNew={name:'Internet',amount:50,dueDate:'2099-01-01'};
    component.addObligation();component.addObligation();
    expect(api.addPlanningPayment).toHaveBeenCalledTimes(1);
    pending.error({error:{message:'Sin conexión'}});
    expect(component.planningLoading).toBeFalse();
    expect(component.planningError).toBe('Sin conexión');
    expect(component.planningObligations[0].id).toBe(7);
  });
  it('cancels pending reads when leaving the account view', () => {
    const pending=new Subject<PlanningState>();api.getPlanningState.and.returnValue(pending);
    const component=new PlanningComponent(api);component.ngOnInit();component.ngOnDestroy();pending.next(state);
    expect(component.planningBalance).toBeNull();
    expect(component.planningObligations).toEqual([]);
  });
  it('retains payments and reports an authorization failure when deletion is rejected', () => {
    api.deletePlanningPayment.and.returnValue(throwError(() => ({error:{message:'Pago no encontrado'}})));
    const component=new PlanningComponent(api);component.ngOnInit();component.removeObligation(0);
    expect(api.deletePlanningPayment).toHaveBeenCalledWith(7);
    expect(component.planningObligations.length).toBe(1);
    expect(component.planningError).toBe('Pago no encontrado');
  });
});
