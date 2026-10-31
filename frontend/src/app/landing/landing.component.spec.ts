import { provideRouter } from '@angular/router';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';
import { AppComponent } from '../app.component';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { premiumTestServices } from '../premium.test-fixtures';

describe('Public Sarela landing and access flow', () => {
  let fixture: ComponentFixture<AppComponent>;
  let services: ReturnType<typeof premiumTestServices>;
  let signedInUser: AuthService['user'];
  let previousUrl: string;
  const root=()=>fixture.nativeElement as HTMLElement;
  const click=(selector:string)=>{(root().querySelector(selector) as HTMLButtonElement).click();fixture.detectChanges();};
  beforeEach(()=>{
    previousUrl=location.pathname+location.search+location.hash;
    history.replaceState({},'',location.pathname+location.search);
    services=premiumTestServices();signedInUser=services.auth.user;
    (services.auth as unknown as {user:AuthService['user']}).user=null;
    TestBed.configureTestingModule({imports:[AppComponent],providers:[provideRouter([]),{provide:ApiService,useValue:services.api},{provide:AuthService,useValue:services.auth}]});
    fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();
  });
  afterEach(()=>history.replaceState({},'',previousUrl));
  it('shows the landing before access and does not request private data',()=>{
    expect(root().querySelector('app-landing')).not.toBeNull();expect(root().querySelector('.auth')).toBeNull();
    expect(root().querySelector('h1')?.textContent).toContain('Tu dinero hoy');
    expect(services.api.dashboard).not.toHaveBeenCalled();expect(services.api.getPlanningState).not.toHaveBeenCalled();
    expect(root().querySelector('app-landing')?.textContent).not.toContain('S/ 3,240');
  });
  it('opens registration from the main CTA and returns to the landing',()=>{
    click('.hero-actions .primary');expect(root().querySelector('app-landing')).toBeNull();
    expect(fixture.componentInstance.authMode).toBe('register');expect(location.hash).toBe('#registro');
    expect(root().querySelector('input[name="confirmPassword"]')).not.toBeNull();
    click('.auth-back');expect(root().querySelector('app-landing')).not.toBeNull();expect(location.hash).toBe('');
  });
  it('opens login from navigation and retains the registration switch',()=>{
    click('.nav-actions .login-link');expect(fixture.componentInstance.authMode).toBe('login');expect(location.hash).toBe('#acceso');
    expect(root().querySelector('input[name="password"]')).not.toBeNull();
    click('.auth-tabs button:nth-child(2)');expect(fixture.componentInstance.authMode).toBe('register');expect(location.hash).toBe('#registro');
  });
  it('restores direct access links without requiring an authenticated session',()=>{
    history.replaceState({},'',location.pathname+location.search+'#registro');fixture.componentInstance.syncPublicView();fixture.detectChanges();
    expect(root().querySelector('app-landing')).toBeNull();expect(root().querySelector('input[name="confirmPassword"]')).not.toBeNull();
  });
  it('retains the dashboard after login and shows the landing on logout',()=>{
    (services.auth.login as jasmine.Spy).and.callFake(()=>{(services.auth as unknown as {user:AuthService['user']}).user=signedInUser;return of(signedInUser);});
    (services.auth.logout as jasmine.Spy).and.callFake(()=>{(services.auth as unknown as {user:AuthService['user']}).user=null;});
    click('.nav-actions .login-link');fixture.componentInstance.login();fixture.detectChanges();
    expect(root().querySelector('.dashboard-view')).not.toBeNull();expect(root().querySelector('app-landing')).toBeNull();expect(location.hash).toBe('');
    fixture.componentInstance.logout();fixture.detectChanges();expect(root().querySelector('app-landing')).not.toBeNull();
  });
  it('uses working section anchors and native accessible FAQ controls',()=>{
    for(const link of Array.from(root().querySelectorAll<HTMLAnchorElement>('app-landing a[href^="#"]'))) {
      expect(root().querySelector(link.getAttribute('href')!)).withContext(link.textContent||'anchor').not.toBeNull();
    }
    const details=root().querySelector('details')!;(details.querySelector('summary') as HTMLElement).click();expect(details.open).toBeTrue();
    expect(details.textContent).toContain('Gmail es una conexión opcional');
  });
  it('loads the local optimized hero and editorial images without broken assets', async()=>{
    const images=Array.from(root().querySelectorAll<HTMLImageElement>('app-landing img'));
    expect(images.length).toBe(2);
    for(const image of images) image.loading='eager';
    await Promise.all(images.map(image=>image.decode()));
    for(const image of images) {
      expect(image.naturalWidth).toBeGreaterThan(0);
      expect(image.currentSrc).toContain('images/sarela-');
      expect(image.alt.length).toBeGreaterThan(10);
    }
  });
});
