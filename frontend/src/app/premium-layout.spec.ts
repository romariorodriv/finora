import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { ApiService } from './core/services/api.service';
import { AuthService } from './core/services/auth.service';
import { premiumTestServices } from './premium.test-fixtures';

describe('Premium UI responsive layout in real browser viewports', () => {
  for (const width of [1440,1024,768,390]) {
    it(`keeps landing, dashboard, planning, movements, Gmail, feedback and authentication within ${width}px`, async () => {
      const services=premiumTestServices();
      TestBed.configureTestingModule({imports: [AppComponent],providers: [provideRouter([]),{provide: ApiService,useValue:services.api},{provide: AuthService,useValue:services.auth}]});
      const fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();await fixture.whenStable();
      const frame=document.createElement('iframe');frame.title=`Layout QA ${width}`;
      frame.style.cssText=`position:fixed;left:0;top:0;width:${width}px;height:1000px;border:0;visibility:hidden;pointer-events:none`;
      document.body.appendChild(frame);
      try {
        const doc=frame.contentDocument!, win=frame.contentWindow!;
        doc.open();doc.write('<!doctype html><html><head></head><body></body></html>');doc.close();
        doc.body.appendChild(fixture.nativeElement);
        const copyStyles=() => {
          doc.head.replaceChildren();
          for (const sheet of Array.from(document.styleSheets)) {
            const style=doc.createElement('style');
            style.textContent=Array.from(sheet.cssRules).map(rule=>rule.cssText).join('\n');
            doc.head.appendChild(style);
          }
        };
        for (const view of ['dashboard','planning','movements','import','feedback'] as const) {
          fixture.componentInstance.go(view);fixture.detectChanges();await fixture.whenStable();copyStyles();
          await new Promise<void>(resolve=>requestAnimationFrame(()=>resolve()));
          expect(doc.documentElement.scrollWidth).withContext(`${view} at ${width}px`).toBeLessThanOrEqual(width+1);
          expect(win.innerWidth).toBe(width);
          const sidebar=win.getComputedStyle(doc.querySelector('.sidebar')!);
          const mobile=win.getComputedStyle(doc.querySelector('.mobile-nav')!);
          expect(sidebar.display).toBe(width<=900 ? 'none' : 'flex');
          expect(mobile.display).toBe(width<=900 ? 'grid' : 'none');
          if (width<=900) {
            const nav=doc.querySelector('.mobile-nav')!;
            expect(nav.querySelectorAll('button').length).toBe(5);
            for (const button of Array.from(nav.querySelectorAll('button'))) expect(button.getBoundingClientRect().width).toBeGreaterThanOrEqual(44);
          }
          if(view==='dashboard') {
            const donut=doc.querySelector('.native-donut')!.getBoundingClientRect();
            expect(Math.abs(donut.width-donut.height)).toBeLessThan(1);
          }
        }
        (services.auth as unknown as {user:null}).user=null;
        fixture.componentInstance.returnToLanding();fixture.detectChanges();await fixture.whenStable();copyStyles();
        expect(doc.documentElement.scrollWidth).withContext(`landing at ${width}px`).toBeLessThanOrEqual(width+1);
        expect(doc.querySelector('app-landing')).not.toBeNull();
        fixture.componentInstance.openAccess('login');fixture.detectChanges();await fixture.whenStable();copyStyles();
        expect(doc.documentElement.scrollWidth).withContext(`login at ${width}px`).toBeLessThanOrEqual(width+1);
        fixture.componentInstance.authMode='register';fixture.detectChanges();await fixture.whenStable();
        expect(doc.documentElement.scrollWidth).withContext(`register at ${width}px`).toBeLessThanOrEqual(width+1);
      } finally { fixture.destroy();frame.remove(); }
    });
  }
});
