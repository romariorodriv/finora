import { provideRouter } from '@angular/router';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, EMPTY, throwError } from 'rxjs';
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
    services=premiumTestServices();(services.auth.googleStart as jasmine.Spy).and.returnValue(EMPTY);signedInUser=services.auth.user;
    (services.auth as unknown as {user:AuthService['user']}).user=null;
    TestBed.configureTestingModule({imports:[AppComponent],providers:[provideRouter([]),{provide:ApiService,useValue:services.api},{provide:AuthService,useValue:services.auth}]});
    fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();
  });
  afterEach(()=>history.replaceState({},'',previousUrl));
  it('shows the landing before access and does not request private data',()=>{
    expect(root().querySelector('app-landing')).not.toBeNull();expect(root().querySelector('.auth')).toBeNull();
    expect(root().querySelector('h1')?.textContent).toContain('¿En qué se fue');
    expect(services.api.dashboard).not.toHaveBeenCalled();
    expect(root().querySelector('app-landing')?.textContent).not.toContain('S/ 3,240');
  });
  it('shows Sarela branding in landing and sign-in without changing the layout',()=>{
    const landing=root().querySelector('app-landing') as HTMLElement;
    expect(landing.querySelector('.wordmark')?.textContent?.trim()).toBe('sarela');
    expect(landing.textContent).not.toMatch(/finora/i);
    click('.hero-actions .primary');
    expect(root().querySelector('.auth-wordmark')?.textContent?.trim()).toBe('sarela');
    expect(root().querySelector('.login-card h2')?.textContent).toContain('Sarela');
    expect(root().textContent).not.toMatch(/Finora/);
  });
  it('keeps the root landing visible when session restoration fails',()=>{
    fixture.destroy();
    (services.auth.restoreSession as jasmine.Spy).and.returnValue(throwError(()=>({status:0})));
    fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();
    expect(root().querySelector('app-landing')).not.toBeNull();
    expect(root().querySelector('.auth')).toBeNull();
    expect(services.auth.googleStart).not.toHaveBeenCalled();
    expect(services.api.dashboard).not.toHaveBeenCalled();
  });
  it('keeps an explicitly requested access page and shows its session error',()=>{
    fixture.destroy();
    history.replaceState({},'',location.pathname+location.search+'#acceso');
    (services.auth.restoreSession as jasmine.Spy).and.returnValue(throwError(()=>({status:500})));
    fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();
    expect(root().querySelector('app-landing')).toBeNull();
    expect(root().querySelector('.google-access')).not.toBeNull();
    expect(fixture.componentInstance.error).toContain('No pudimos comprobar');
  });
  it('opens Google access from the main CTA and returns to the landing',()=>{
    click('.hero-actions .primary');expect(root().querySelector('app-landing')).toBeNull();
    expect(fixture.componentInstance.authMode).toBe('register');expect(location.hash).toBe('#registro');
    expect(root().querySelector('.google-access')?.textContent).toContain('Continuar con Google');
    expect(root().querySelector('input[type="password"]')).toBeNull();
    click('.auth-back');expect(root().querySelector('app-landing')).not.toBeNull();expect(location.hash).toBe('');
  });
  it('opens Google access from navigation without public password fields',()=>{
    click('.nav-google');expect(location.hash).toBe('#acceso');
    expect(root().querySelector('.google-access')).not.toBeNull();
    expect(root().querySelector('input[type="password"]')).toBeNull();
  });
  it('restores direct access links without requiring an authenticated session',()=>{
    history.replaceState({},'',location.pathname+location.search+'#registro');fixture.componentInstance.syncPublicView();fixture.detectChanges();
    expect(root().querySelector('app-landing')).toBeNull();expect(root().querySelector('.google-access')?.textContent).toContain('Continuar con Google');
    expect(root().querySelector('input[type="password"]')).toBeNull();
  });
  it('restores a server session and returns to the landing only after logout succeeds',()=>{
    (services.auth as unknown as {user:AuthService['user']}).user=signedInUser;
    (services.auth.logout as jasmine.Spy).and.callFake(()=>{
      (services.auth as unknown as {user:AuthService['user']}).user=null;return of(undefined);
    });
    fixture.detectChanges();
    expect(root().querySelector('.dashboard-view')).not.toBeNull();
    fixture.componentInstance.logout();fixture.detectChanges();expect(root().querySelector('app-landing')).not.toBeNull();
  });
  it('provides working section anchors, legal links and honest Gmail and pricing copy',()=>{
    for(const link of Array.from(root().querySelectorAll<HTMLAnchorElement>('app-landing a[href^="#"]'))) {
      expect(root().querySelector(link.getAttribute('href')!)).withContext(link.textContent||'anchor').not.toBeNull();
    }
    expect(root().querySelector('#precio')?.textContent).toContain('El precio se anunciará antes de cualquier cobro');
    expect(root().querySelector('.flow-note')?.textContent).toContain('Conectarlo es opcional');
    expect(root().querySelector('.permission-card')?.textContent).toContain('No todos tus gastos llegan por correo');
    expect(root().querySelector('a[href="/privacidad.html"]')).not.toBeNull();
    expect(root().querySelector('a[href="/terminos.html"]')).not.toBeNull();
    expect(root().querySelectorAll('figcaption').length).toBe(2);
    for(const caption of Array.from(root().querySelectorAll('figcaption'))) expect(caption.textContent).toContain('Datos de ejemplo');
  });
  it('loads local Google marks without depending on decorative images',async()=>{
    const images=Array.from(root().querySelectorAll<HTMLImageElement>('app-landing img'));
    expect(images.length).toBe(3);
    await Promise.all(images.map(image=>image.decode()));
    for(const image of images) expect(image.naturalWidth).toBeGreaterThan(0);
    expect(root().querySelectorAll('app-landing-preview').length).toBe(3);
  });
  it('starts Google from each conversion action without requesting Gmail consent',()=>{
    for(const selector of ['.nav-google','.hero-actions .primary','.discover-button','.final-cta .primary']) {
      click(selector);
      expect(services.auth.googleStart).toHaveBeenCalled();
      expect(services.api.gmailAuthorizationUrl).not.toHaveBeenCalled();
      click('.auth-back');
    }
    expect(services.auth.googleStart).toHaveBeenCalledTimes(4);
  });
  it('shows a retryable access error when Google start is unavailable',()=>{
    (services.auth.googleStart as jasmine.Spy).and.returnValue(throwError(()=>new Error('Sin conexión')));
    click('.hero-actions .primary');
    expect(root().querySelector('.google-access')).not.toBeNull();
    expect(fixture.componentInstance.authSubmitting).toBeFalse();
    expect(fixture.componentInstance.error).not.toBe('');
  });
  it('fits narrow phones, tablets and desktop with readable stacked sections',async()=>{
    const frame=document.createElement('iframe');frame.style.cssText='position:fixed;left:-10000px;top:0;height:1000px;border:0';
    document.body.appendChild(frame);
    try {
      const doc=frame.contentDocument!;
      for(const style of Array.from(document.head.querySelectorAll('style'))) doc.head.appendChild(style.cloneNode(true));
      doc.body.appendChild(root().querySelector('app-landing')!.cloneNode(true));
      for(const width of [320,390,768,1024,1440]) {
        frame.style.width=width+'px';await new Promise(resolve=>setTimeout(resolve,40));
        expect(doc.documentElement.scrollWidth).withContext('horizontal overflow at '+width).toBeLessThanOrEqual(width+1);
        const steps=Array.from(doc.querySelectorAll('.step')).map(e=>e.getBoundingClientRect());
        if(width<=600) expect(steps[1].top).toBeGreaterThan(steps[0].bottom);
        else expect(Math.abs(steps[0].top-steps[1].top)).toBeLessThan(1);
        for(const button of Array.from(doc.querySelectorAll('button'))) expect(button.getBoundingClientRect().height).toBeGreaterThanOrEqual(40);
      }
    } finally {frame.remove();}
  });
});
