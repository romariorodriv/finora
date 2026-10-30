# Revisión npm — Sarela 2.2

Auditoría ejecutada el 30 de octubre de 2026 contra registry.npmjs.org. No se usó `npm audit fix --force` ni `--legacy-peer-deps`.

## Resultado

| Alcance | Antes | Después |
|---|---:|---:|
| Total | 42 | 38 |
| Críticas | 4 | 0 |
| Altas | 21 | 20 |
| Moderadas | 15 | 16 |
| Bajas | 2 | 2 |

`npm audit --omit=dev`: 7 hallazgos (4 altos y 3 moderados), ninguno crítico. Los conteos incluyen paquetes afectados de forma transitiva; no equivalen a CVE independientes.

## Correcciones aplicadas y compatibilidad

| Cadena de desarrollo | Parche | Evaluación |
|---|---|---|
| Angular build → piscina | 4.8.0 → 4.9.4 | Misma versión mayor; API de workers conservada y compilaciones Angular/Karma verificadas. Angular fija 4.8.0, por eso se usa override explícito. |
| webpack-dev-server → express → proxy-addr | 2.0.7 → 2.0.8 | Parche de la misma versión mayor; corrige suplantación de IP. |
| launch-editor → shell-quote | 1.10.0 → 1.11.0 | Misma versión mayor; corrige inyección de comandos. |
| Angular CLI → pacote → tar | pacote 20.0.0 → 20.0.1; tar 6.2.1 → 7.5.22 | Se actualiza pacote mediante un parche publicado que ya declara tar ^7.5.10; no se fuerza tar 7 sobre el contrato tar 6 del pacote anterior. |

Se fijan estos cuatro overrides en package.json y se registra el árbol en package-lock.json. La instalación no reportó conflictos de peers. TypeScript permanece en 5.7.3 y Angular en 19.2.25 (CLI/build 19.2.27).

Los cuatro críticos originales pertenecían a herramientas de desarrollo, no al backend Java. Su impacto potencial incluía ejecución de código, suplantación de IP y denegación de servicio.

- [piscina: piscina: Prototype-pollution gadget in ThreadPool.options allows RCE via execArgv / loadBalancer / env](https://github.com/advisories/GHSA-67c8-pqhq-4rmx)
- [proxy-addr: proxy-addr vulnerable to IP spoofing via IPv4-mapped IPv6 trust subnet](https://github.com/advisories/GHSA-jqcg-44mw-7w3h)
- [shell-quote: shell-quote: `quote()` command injection via a line terminator in a token after a `{ comment }` token](https://github.com/advisories/GHSA-pqg4-j6r4-53mv)
- [tar: node-tar: Decompression/parse DoS via unlimited input](https://github.com/advisories/GHSA-23hp-3jrh-7fpw)

## Hallazgos pendientes por paquete

La migración de Angular a una versión con parches de seguridad requiere revisar las versiones de Node, TypeScript, zone.js y los builders en conjunto. No se introduce ese cambio mayor en esta integración. Los paquetes de ejecución pendientes son Angular common/compiler/core/forms/platform-browser/platform-browser-dynamic/router. La aplicación actual es cliente, sin SSR; esto reduce exposición a fallos específicos de SSR/hidratación, pero no elimina los avisos de sanitización/XSS.

Las vulnerabilidades de tooling deben remediarse también: no forman parte del JavaScript servido, pero afectan estaciones de desarrollo y CI. Para producción queda pendiente una actualización coordinada de Angular y sus herramientas, con pruebas de regresión y auditoría nueva.

| Paquete | Severidad | Alcance | Causa o avisos |
|---|---|---|---|
| `@angular-devkit/build-angular` | high | desarrollo | @angular/build; @angular/compiler-cli; @babel/core; @ngtools/webpack; copy-webpack-plugin; esbuild; http-proxy-middleware; postcss; webpack-dev-middleware; webpack-dev-server |
| `@angular/build` | moderate | desarrollo | @angular/compiler; @angular/compiler-cli; @babel/core; esbuild; vite |
| `@angular/cli` | high | desarrollo | pacote |
| `@angular/common` | high | ejecución | @angular/core; [@angular/common: Denial of Service (DoS) via OOM in Date Formatting (formatDate)](https://github.com/advisories/GHSA-48r7-hpm6-gfxm); [@angular/common: Weak 32-Bit Cache Key Hashing in `HttpTransferCache` Leading to Cross-Request Data Leakage and State Poisoning](https://github.com/advisories/GHSA-39pv-4j6c-2g6v); [Angular: Cache-Key Ambiguity in HttpTransferCache Leading to Cross-Request Response Reuse and State Poisoning](https://github.com/advisories/GHSA-jhpw-976m-542j); [Angular: Information Leak via `HttpTransferCache` Bypass When Using `withRequestsMadeViaParent`](https://github.com/advisories/GHSA-p297-fm68-3q8c); [@angular/common: XSRF Token Leakage via URL Parsing Discrepancy in Angular HTTP Client (Incomplete fix for CVE-2025-66035)](https://github.com/advisories/GHSA-jfgx-762w-99vr) |
| `@angular/compiler` | high | ejecución | [@angular/compiler: Two-Way Property Binding Sanitization Bypass (XSS)](https://github.com/advisories/GHSA-58w9-8g37-x9v5); [Angular i18n: Cross-Site Scripting (XSS) via event-handler attributes](https://github.com/advisories/GHSA-jj27-h5hq-8x99); [Angular: Sanitization bypass via directive host bindings on concrete host elements in @angular/core and @angular/compiler](https://github.com/advisories/GHSA-hh8m-fm6v-7cvg) |
| `@angular/compiler-cli` | moderate | desarrollo | @angular/compiler; @babel/core |
| `@angular/core` | high | ejecución | [Angular Client Hydration DOM Clobbering & Response-Cache Poisoning](https://github.com/advisories/GHSA-rgjc-h3x7-9mwg); [Angular i18n: Cross-Site Scripting (XSS) via event-handler attributes](https://github.com/advisories/GHSA-jj27-h5hq-8x99); [Angular: Sanitization bypass via directive host bindings on concrete host elements in @angular/core and @angular/compiler](https://github.com/advisories/GHSA-hh8m-fm6v-7cvg) |
| `@angular/forms` | moderate | ejecución | @angular/common; @angular/core; @angular/platform-browser |
| `@angular/platform-browser` | moderate | ejecución | @angular/common; @angular/core |
| `@angular/platform-browser-dynamic` | moderate | ejecución | @angular/common; @angular/compiler; @angular/core; @angular/platform-browser |
| `@angular/router` | high | ejecución | @angular/common; @angular/core; @angular/platform-browser; [Angular Server-Side Rendering (SSR): Denial of Service via Numeric URL Matrix Parameters](https://github.com/advisories/GHSA-ff3f-86qr-9cv3); [@angular/router: Denial of Service via Unmatched Empty-Path Outlet Route Matching in Server-Side Rendering (SSR)](https://github.com/advisories/GHSA-62vg-58rm-qff7) |
| `@babel/core` | low | desarrollo | [@babel/core: Arbitrary File Read via sourceMappingURL Comment](https://github.com/advisories/GHSA-4x5r-pxfx-6jf8) |
| `@ngtools/webpack` | moderate | desarrollo | @angular/compiler-cli |
| `@sigstore/core` | moderate | desarrollo | [@sigstore/core has DSSE payloadType type-binding failure](https://github.com/advisories/GHSA-jfc7-64v2-mr8c) |
| `@sigstore/sign` | moderate | desarrollo | @sigstore/core |
| `@sigstore/verify` | moderate | desarrollo | @sigstore/core |
| `body-parser` | high | desarrollo | [body-parser vulnerable to Denial of Service via trailing bytes after a compressed request body](https://github.com/advisories/GHSA-27r3-jxmh-p828) |
| `brace-expansion` | high | desarrollo | [brace-expansion: Quadratic-time expansion of the `{a},b}` rewrite causes CPU denial of service](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr); [brace-expansion: Quadratic-time expansion of the `{a},b}` rewrite causes CPU denial of service](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr); [brace-expansion: DoS via uncontrolled recursion on nested brace groups causing stack exhaustion](https://github.com/advisories/GHSA-qhr7-859c-m2p7); [brace-expansion: DoS via uncontrolled recursion on nested brace groups causing stack exhaustion](https://github.com/advisories/GHSA-qhr7-859c-m2p7); [brace-expansion: DoS via uncontrolled recursion in parseCommaParts causing stack exhaustion](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p); [brace-expansion: DoS via uncontrolled recursion in parseCommaParts causing stack exhaustion](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p) |
| `compression` | high | desarrollo | [compression vulnerable to Denial of Service via memory leak on premature response close](https://github.com/advisories/GHSA-vc2v-76pw-4v95) |
| `copy-webpack-plugin` | moderate | desarrollo | serialize-javascript |
| `esbuild` | low | desarrollo | [esbuild allows arbitrary file read when running the development server on Windows](https://github.com/advisories/GHSA-g7r4-m6w7-qqqr) |
| `fast-uri` | moderate | desarrollo | [fast-uri vulnerable to inconsistent host case normalization via percent-encoded octets](https://github.com/advisories/GHSA-hrr3-gc8f-f4qj); [fast-uri vulnerable to URI scheme spoofing via percent-encoded scheme decoding](https://github.com/advisories/GHSA-w74x-v6f9-j3xg); [fast-uri vulnerable to improper path equivalence in relative reference normalization](https://github.com/advisories/GHSA-xhq7-xmqj-769h); [fast-uri vulnerable to host confusion via unchecked WHATWG host canonicalization](https://github.com/advisories/GHSA-wjr9-j795-jxcr); [fast-uri vulnerable to scheme injection via an unescaped first-segment colon in suffix references](https://github.com/advisories/GHSA-333x-w4r6-6crp) |
| `follow-redirects` | high | desarrollo | [follow-redirects array-form headers bypass cross-origin credential stripping](https://github.com/advisories/GHSA-q3jh-wrc6-jgq8); [follow-redirects sensitiveHeaders regex escaping can expose credential headers](https://github.com/advisories/GHSA-8r9p-f939-6h3c) |
| `http-proxy-middleware` | high | desarrollo | [http-proxy-middleware: multipart/form-data field injection via unescaped CRLF in `fixRequestBody`](https://github.com/advisories/GHSA-gcq2-9pq2-cxqm); [http-proxy-middleware `router` host+path substring matching allows Host-header-driven backend routing bypass](https://github.com/advisories/GHSA-64mm-vxmg-q3vj) |
| `ip-address` | moderate | desarrollo | [ip-address: isInSubnet() and isHostInSubnet() compare addresses of different families as if they shared an address space, allowing an allowlist check to admit an address outside its range](https://github.com/advisories/GHSA-j6r3-76f7-8jcv); [ip-address: Address6 builds a parse diagnostic proportional to the input with no length bound, allowing a single long string to stall or crash the process](https://github.com/advisories/GHSA-h3mg-xc3c-68pw); [ip-address: Address6 matches a zone identifier in quadratic time ahead of its length limit, allowing a single long string to stall the process](https://github.com/advisories/GHSA-rxp7-6hxx-x8q5) |
| `launch-editor` | high | desarrollo | [launch-editor: escapeCmdArgs() omits % escaping on Windows](https://github.com/advisories/GHSA-698v-4r3x-3vcv); [launch-editor: Editor CLI flag misinterpretation causing arbitrary command execution for some editors](https://github.com/advisories/GHSA-r825-w8mm-5pxw); [launch-editor: `fs.existsSync()` existence check can be bypassed on Windows](https://github.com/advisories/GHSA-587h-7wmj-h5pm) |
| `node-forge` | high | desarrollo | [node-forge RSA PKCS#1 v1.5 signature verification accepts extra nested DigestAlgorithm elements](https://github.com/advisories/GHSA-86w9-cpqp-85rv) |
| `pacote` | high | desarrollo | [pacote is vulnerable to Denial of Service (DoS) via the addGitSha function](https://github.com/advisories/GHSA-w4pp-8pjf-rmxw); sigstore |
| `postcss` | high | desarrollo | [PostCSS: incomplete fix of GHSA-6g55-p6wh-862q — attacker-controlled sourceMappingURL reads arbitrary .map files when `from` is unset](https://github.com/advisories/GHSA-fxqj-rqcc-2cmp); [PostCSS: Path Traversal in Previous Source Map Auto-Loading (sourceMappingURL) leads to Arbitrary .map File Disclosure](https://github.com/advisories/GHSA-r28c-9q8g-f849) |
| `qs` | moderate | desarrollo | [qs array-limit bypass via bracket-key comma parsing](https://github.com/advisories/GHSA-x5fp-wj9c-mxmx); [qs: Denial of Service via Attacker Controlled isBuffer](https://github.com/advisories/GHSA-4mjr-xmp4-gh2g); [qs : qs.stringify with arrayFormat 'comma' throws an uncaught TypeError on array elements carrying a non-callable own toString (DoS)](https://github.com/advisories/GHSA-6pq8-jhm2-2vcw) |
| `serialize-javascript` | high | desarrollo | [Serialize JavaScript is Vulnerable to RCE via RegExp.flags and Date.prototype.toISOString()](https://github.com/advisories/GHSA-5c6j-r48x-rmvq); [Serialize JavaScript has CPU Exhaustion Denial of Service via crafted array-like objects](https://github.com/advisories/GHSA-qj8w-gfj5-8c6v); [Serialize JavaScript: Cross-site scripting (XSS) via unescaped </script at the end of a serialized function body](https://github.com/advisories/GHSA-m5mm-p8h7-m29v) |
| `sigstore` | high | desarrollo | @sigstore/core; @sigstore/sign; @sigstore/verify; [sigstore's `certificateOIDs` verification constraints are silently dropped and never enforced](https://github.com/advisories/GHSA-52v5-jr5w-gjxr) |
| `sockjs` | moderate | desarrollo | uuid |
| `source-map-js` | high | desarrollo | [source-map-js allows event-loop denial of service through indexed source-map section offsets](https://github.com/advisories/GHSA-68fv-2mgg-jv7q) |
| `uuid` | moderate | desarrollo | [uuid: Missing buffer bounds check in v3/v5/v6 when buf is provided](https://github.com/advisories/GHSA-w5hq-g745-h8pq) |
| `vite` | high | desarrollo | [launch-editor: NTLMv2 hash disclosure via UNC path handling on Windows](https://github.com/advisories/GHSA-v6wh-96g9-6wx3); [launch-editor: escapeCmdArgs() omits % escaping on Windows](https://github.com/advisories/GHSA-698v-4r3x-3vcv); [launch-editor: Editor CLI flag misinterpretation causing arbitrary command execution for some editors](https://github.com/advisories/GHSA-r825-w8mm-5pxw); [launch-editor: `fs.existsSync()` existence check can be bypassed on Windows](https://github.com/advisories/GHSA-587h-7wmj-h5pm); [Vite: Cross origin script execution through Vite custom middleware](https://github.com/advisories/GHSA-9jrq-w75r-8gcw); [Vite: Files outside `server.fs.allow` could be served when project paths match system paths on non-Windows machine](https://github.com/advisories/GHSA-rq7h-c2jc-7f22); [vite: `server.fs.deny` bypass on Windows alternate paths](https://github.com/advisories/GHSA-fx2h-pf6j-xcff) |
| `webpack-dev-middleware` | high | desarrollo | [webpack-dev-middleware vulnerable to Path Traversal via non-slash-terminated publicPath](https://github.com/advisories/GHSA-g84c-rxfj-3j2c) |
| `webpack-dev-server` | moderate | desarrollo | [webpack-dev-server vulnerable to cross-origin source code exposure on non-HTTPS origins](https://github.com/advisories/GHSA-79cf-xcqc-c78w); [webpack-dev-server vulnerable to HMR WebSocket interception via permissive user proxies](https://github.com/advisories/GHSA-mx8g-39q3-5c79); [webpack-dev-server vulnerable to cross-site request forgery via internal developer endpoints](https://github.com/advisories/GHSA-f5vj-f2hx-8m93); [webpack-dev-server vulnerable to denial of service via a malformed Host or Origin header](https://github.com/advisories/GHSA-m28w-2pqf-7qgj); sockjs |

## Reproducción

```powershell
cd frontend
npm ci
npm audit
npm audit --omit=dev
$env:CHROME_BIN='C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
npm test -- --watch=false --browsers=ChromeHeadless
npm run build -- --configuration production
```
