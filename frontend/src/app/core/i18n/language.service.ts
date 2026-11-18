import esPE from '@angular/common/locales/es-PE';
import { registerLocaleData, DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { es } from './es';
import { en } from './en';

registerLocaleData(esPE);

export type Language = 'es' | 'en';
export const LANGUAGE_KEY = 'sarela.language';
type Params = readonly unknown[];
const normalize = (value: string) => value.replace(/\s+/g, ' ').trim();

/** Source-language keys keep copy searchable. Never pass user content through this service. */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Language>('es');
  readonly language = this.current.asReadonly();
  readonly locale = computed(() => this.current() === 'es' ? 'es-PE' : 'en-US');
  private readonly patterns = Object.keys(es).filter(key => /\{\d+\}/.test(key)).map(key => ({
    key,
    regex: new RegExp('^' + key.split(/\{\d+\}/).map(part => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('(.*?)') + '$')
  }));

  constructor() {
    const window = this.document.defaultView;
    let saved: string | null = null;
    try { saved = window?.localStorage.getItem(LANGUAGE_KEY) ?? null; } catch { /* storage may be blocked */ }
    const query = window ? new URL(window.location.href).searchParams.get('lang') ?? (window.location.pathname === '/en.html' ? 'en' : null) : null;
    this.setLanguage(query === 'en' || query === 'es' ? query : saved === 'en' ? 'en' : 'es');
    const onStorage = (event: StorageEvent) => {
      if (event.key === LANGUAGE_KEY) this.setLanguage(event.newValue === 'en' ? 'en' : 'es');
    };
    window?.addEventListener('storage', onStorage);
    inject(DestroyRef).onDestroy(() => window?.removeEventListener('storage', onStorage));
  }

  setLanguage(language: Language): void {
    if (language !== 'es' && language !== 'en') return;
    this.current.set(language);
    const window = this.document.defaultView;
    try { window?.localStorage.setItem(LANGUAGE_KEY, language); } catch { /* current page still works */ }
    if (window) {
      const url = new URL(window.location.href);
      // Keep OAuth parameters/hash intact. The language belongs to Sarela, never to Google scopes.
      if (url.searchParams.has('lang') || url.pathname === '/en.html') {
        url.searchParams.set('lang', language);
        window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
      }
    }
    if (window && ['/', '/en.html'].includes(window.location.pathname)) {
      const canonical = 'https://sarelaapp.com/' + (language === 'en' ? 'en.html' : '');
      this.document.querySelector('link[rel="canonical"]')?.setAttribute('href', canonical);
      this.document.querySelector('meta[property="og:url"]')?.setAttribute('content', canonical);
      for (const script of Array.from(this.document.querySelectorAll('script[type="application/ld+json"]'))) {
        try {
          const data = JSON.parse(script.textContent || '{}');
          for (const entity of data['@graph'] ?? []) if (entity['@type'] === 'WebSite') {
            entity.inLanguage = language === 'en' ? 'en' : 'es-PE'; entity.url = canonical;
          }
          script.textContent = JSON.stringify(data);
        } catch { /* unrelated malformed structured data must not block language selection */ }
      }
    }
    this.document.documentElement.lang = language;
    this.document.title = this.t('Sarela | Controla tus gastos personales y entiende tu dinero');
    for (const [selector, key] of [
      ['meta[name="description"]', 'Descubre en qué se va tu dinero. Sarela organiza movimientos reconocidos de notificaciones financieras compatibles de Gmail y ofrece calculadoras gratuitas.'],
      ['meta[property="og:title"]', 'Sarela | Controla tus gastos personales y entiende tu dinero'],
      ['meta[property="og:description"]', 'Entiende tus gastos reconocidos por comercio y categoría. Calculadoras y guías de finanzas personales gratuitas en Perú.'],
      ['meta[property="og:image:alt"]', 'Sarela: tus gastos en orden']
    ]) this.document.querySelector(selector)?.setAttribute('content', this.t(key));
    this.document.querySelector('meta[property="og:locale"]')?.setAttribute('content', language === 'es' ? 'es_PE' : 'en_US');
  }

  t(key: string, params: Params = []): string {
    const source = normalize(key);
    const dictionary: Record<string, string> = this.language() === 'en' ? en : es;
    return (dictionary[source] ?? source).replace(/\{(\d+)\}/g, (match, index) =>
      Number(index) < params.length ? String(params[Number(index)] ?? '') : match);
  }

  /** Existing state messages can contain counts; translate only complete catalogued patterns. */
  text(value: unknown): string {
    const source = normalize(String(value ?? ''));
    if (source in es || this.language() === 'es') return this.t(source);
    for (const pattern of this.patterns) {
      const match = source.match(pattern.regex);
      if (match) return this.t(pattern.key, match.slice(1));
    }
    return source;
  }

  errorText(value: unknown): string {
    const source = normalize(String(value ?? ''));
    const translated = this.text(source);
    if (this.language() === 'es' || source in es || translated !== source) return translated;
    return this.t('No pudimos completar la operación. Vuelve a intentarlo.');
  }

  /** API errors use stable codes. Unknown English errors use a useful generic message. */
  error(error: any): string {
    if (error?.status === 0) return 'No pudimos conectarnos con Sarela. Intenta nuevamente.';
    const codes: Record<string, string> = {
      GMAIL_RECONNECT_REQUIRED: 'La autorización de Gmail venció o fue revocada. Vuelve a conectar Gmail para continuar.',
      EMAIL_ALREADY_REGISTERED: 'Ya existe una cuenta con este correo.',
      UNAUTHORIZED: 'Tu sesión venció. Inicia sesión nuevamente.',
      FORBIDDEN: 'No tienes permiso para realizar esta acción.',
      NOT_FOUND: 'Recurso no encontrado.',
      TOO_MANY_REQUESTS: 'Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.'
    };
    const message = error?.error?.message;
    if (codes[error?.error?.code]) return codes[error.error.code];
    if (typeof message === 'string') return message;
    return 'No pudimos completar la operación. Vuelve a intentarlo.';
  }
}
