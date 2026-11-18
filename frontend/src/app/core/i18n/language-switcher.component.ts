import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LanguageService } from './language.service';

@Component({
  selector: 'app-language-switcher', standalone: true,
  template: `<nav class="language-switcher" [attr.aria-label]="lang.language() === 'es' ? 'Idioma' : 'Language'">
    <button type="button" lang="es" aria-label="Español" [attr.aria-pressed]="lang.language() === 'es'" (click)="lang.setLanguage('es')">ES</button>
    <span aria-hidden="true">|</span>
    <button type="button" lang="en" aria-label="English" [attr.aria-pressed]="lang.language() === 'en'" (click)="lang.setLanguage('en')">EN</button>
  </nav>`,
  styles: `:host{display:block}.language-switcher{display:flex;align-items:center;gap:4px;background:#fff;border:1px solid #b6d7c4;border-radius:24px;padding:3px;color:#0f3d30}.language-switcher button{font:600 12px/1.2 system-ui;min-width:36px;min-height:34px;border:0;border-radius:18px;background:transparent;color:inherit;cursor:pointer}.language-switcher button[aria-pressed=true]{background:#0f3d30;color:#fff}.language-switcher button:focus-visible{outline:3px solid #d59537;outline-offset:2px}`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LanguageSwitcherComponent { readonly lang = inject(LanguageService); }
