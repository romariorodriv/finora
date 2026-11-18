/* Standalone pages use real ES/EN documents; preference is shared with Angular. */
(() => {
  const key = 'sarela.language';
  const path = location.pathname;
  const english = path.startsWith('/en-');
  const spanishPath = english ? '/' + path.slice(4) : path;
  const englishPath = '/en-' + spanishPath.slice(1);
  const url = new URL(location.href);
  const requested = url.searchParams.get('lang');
  let saved = null;
  try { saved = localStorage.getItem(key); } catch { /* navigation still works */ }
  const language = requested === 'es' || requested === 'en' ? requested : saved === 'en' ? 'en' : english ? 'en' : 'es';
  try { localStorage.setItem(key, language); } catch { /* optional persistence */ }
  if ((language === 'en') !== english) {
    url.pathname = language === 'en' ? englishPath : spanishPath;
    url.searchParams.delete('lang');
    location.replace(url.pathname + url.search + url.hash);
    return;
  }
  const nav = document.querySelector('.public-language-switcher');
  nav?.querySelectorAll('a[lang]').forEach(a => {
    a.hash = location.hash;
    a.addEventListener('click', () => {try {localStorage.setItem(key,a.lang);} catch {}});
  });
})();
