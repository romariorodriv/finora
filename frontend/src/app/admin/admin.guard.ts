import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { of, map, catchError } from 'rxjs';
import { AuthService } from '../core/services/auth.service';
export const adminGuard:CanActivateFn=()=>{
 const auth=inject(AuthService),router=inject(Router);
 const denied=()=>router.createUrlTree(['/'],{fragment:'acceso',queryParams:{returnUrl:'/admin',access:'admin-required'}});
 if(!auth.user)return denied();
 return auth.refreshCurrent().pipe(map(profile=>profile.role==='ADMIN'?true:denied()),catchError(()=>of(denied())));
};
