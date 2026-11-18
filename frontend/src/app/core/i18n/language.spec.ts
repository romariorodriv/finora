import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LanguageService, LANGUAGE_KEY } from './language.service';
import { AppComponent } from '../../app.component';
import { ApiService } from '../services/api.service';
import { AuthService } from '../services/auth.service';
import { premiumTestServices } from '../../premium.test-fixtures';
import { of } from 'rxjs';
import { AdminComponent } from '../../admin/admin.component';
import { AdminService } from '../../admin/admin.service';

describe('Sarela ES/EN integration', () => {
  let originalUrl: string;
  beforeEach(() => { originalUrl = location.pathname + location.search + location.hash; localStorage.removeItem(LANGUAGE_KEY); history.replaceState({}, '', '/'); });
  afterEach(() => { TestBed.inject(LanguageService).setLanguage('es'); localStorage.removeItem(LANGUAGE_KEY); history.replaceState({}, '', originalUrl); });
  function setup(anonymous = false) {
    const services = premiumTestServices();
    if (anonymous) (services.auth as any).user = null;
    TestBed.configureTestingModule({imports:[AppComponent],providers:[provideRouter([]),{provide:ApiService,useValue:services.api},{provide:AuthService,useValue:services.auth}]});
    const fixture = TestBed.createComponent(AppComponent); fixture.detectChanges();
    return { fixture, services, lang: TestBed.inject(LanguageService), root: fixture.nativeElement as HTMLElement };
  }
  it('defaults to Spanish, updates OnPush landing live, and persists an explicit selection', () => {
    const {fixture,lang,root} = setup(true);
    expect(lang.language()).toBe('es');
    (root.querySelector('app-language-switcher button[lang="en"]') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(root.querySelector('h1')?.textContent).toContain('Where did');
    expect(root.querySelector('.nav-google')?.textContent).toContain('Continue with Google');
    expect(document.documentElement.lang).toBe('en');
    expect(document.title).toContain('Track your personal expenses');
    expect(localStorage.getItem(LANGUAGE_KEY)).toBe('en');
    lang.setLanguage('es');fixture.detectChanges();
    expect(root.querySelector('h1')?.textContent).toContain('¿En qué se fue');
  });
  it('restores English from storage after reload or OAuth callback and leaves callback parameters intact', () => {
    localStorage.setItem(LANGUAGE_KEY,'en');history.replaceState({},'', '/?auth=connected&gmail=connected#registro');
    const lang = TestBed.inject(LanguageService);
    expect(lang.language()).toBe('en');
    expect(location.search).toContain('auth=connected');expect(location.hash).toBe('#registro');
  });
  it('supports explicit language links and safely ignores an invalid language', () => {
    localStorage.setItem(LANGUAGE_KEY,'en');history.replaceState({},'', '/?lang=es&auth=connected');
    const lang=TestBed.inject(LanguageService);expect(lang.language()).toBe('es');
    lang.setLanguage('fr' as any);expect(lang.language()).toBe('es');expect(location.search).toContain('auth=connected');
  });
  it('keeps dashboard, transactions, Gmail consent and feedback in the selected language without mutating data', () => {
    const {fixture,services,lang,root}=setup();lang.setLanguage('en');fixture.detectChanges();
    expect(root.querySelector('.sidebar')?.textContent).toContain('Transactions');
    expect(root.querySelector('h1')?.textContent).toContain('Hello, Cuenta');
    expect(root.textContent).toContain('Comercio QA');
    fixture.componentInstance.go('movements');fixture.detectChanges();
    expect(root.querySelector('h1')?.textContent).toContain('Your transactions');
    fixture.componentInstance.openMovementEditor();fixture.detectChanges();
    fixture.componentInstance.txForm.description='Descripción escrita por mí';
    lang.setLanguage('es');fixture.detectChanges();lang.setLanguage('en');fixture.detectChanges();
    expect(fixture.componentInstance.txForm.description).toBe('Descripción escrita por mí');
    fixture.componentInstance.closeMovementEditor();fixture.componentInstance.go('import');
    fixture.componentInstance.gmailConsentOpen=true;fixture.detectChanges();
    expect(root.querySelector('.sarela-consent-dialog')?.textContent).toContain('read-only access to your entire Gmail');
    expect(services.api.gmailAuthorizationUrl).not.toHaveBeenCalled();
    fixture.componentInstance.gmailConsentOpen=false;fixture.componentInstance.go('feedback');fixture.detectChanges();
    expect(root.querySelector('h1')?.textContent).toContain('Your feedback helps us improve');
  });
  it('translates error codes and unknown errors without exposing Spanish server copy in English', () => {
    const lang=TestBed.inject(LanguageService);lang.setLanguage('en');
    expect(lang.errorText(lang.error({error:{code:'GMAIL_RECONNECT_REQUIRED'}}))).toContain('Gmail authorization expired');
    expect(lang.errorText(lang.error({error:{code:'FUTURE_CODE',message:'Texto desconocido del servidor'}}))).toContain('could not complete');
    expect(lang.t('Página {0} de {1}',[0,2])).toBe('Page 0 of 2');
  });
  it('formats dates for the selected locale and keeps currency and amounts unchanged', () => {
    const {fixture,lang}=setup();const c=fixture.componentInstance;
    expect(c.movementDate('2026-01-23')).toContain('ene');lang.setLanguage('en');
    expect(c.movementDate('2026-01-23')).toContain('Jan');expect(c.money(123.45,'PEN')).toContain('123.45');expect(c.money(123.45,'PEN')).toContain('S/');
    expect(c.money(123.45,'USD')).toContain('$');
  });
  it('shows admin tabs, forms and scope summaries in English while preserving custom category names', () => {
    const page={items:[],totalElements:0,totalPages:0,page:0};
    const api={options:()=>of([{id:1,name:'Mi categoría personal',active:true}]),categories:()=>of(page)};
    TestBed.configureTestingModule({imports:[AdminComponent],providers:[{provide:AdminService,useValue:api}]});
    const fixture=TestBed.createComponent(AdminComponent);const lang=TestBed.inject(LanguageService);lang.setLanguage('en');fixture.detectChanges();
    const root=fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h1')?.textContent).toBe('Administration');
    fixture.componentInstance.editCategory();fixture.detectChanges();expect(root.textContent).toContain('Create category');
    expect(fixture.componentInstance.categoryName(1)).toBe('Mi categoría personal');
    expect(fixture.componentInstance.scopeLabel('{}')).toContain('All accounts');
  });
  it('uses the localized irreversible confirmation word without deleting on a wrong word', () => {
    const {fixture,lang,services}=setup();lang.setLanguage('en');const prompt=spyOn(window,'prompt').and.returnValue('ELIMINAR');
    fixture.componentInstance.requestDataDeletion();expect(prompt.calls.mostRecent().args[0]).toContain('Type DELETE');expect(services.api.requestDataDeletion).not.toHaveBeenCalled();
  });
});
