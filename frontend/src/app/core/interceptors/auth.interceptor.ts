import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const base = environment.apiBaseUrl.replace(/\/$/, '');
  return next(req.url === base || req.url.startsWith(base + '/')
    ? req.clone({withCredentials: true}) : req);
};
