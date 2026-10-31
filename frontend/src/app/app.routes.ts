import { Routes } from '@angular/router';
import { adminGuard } from './admin/admin.guard';
export const routes: Routes = [{path:'admin',canActivate:[adminGuard],loadComponent:()=>import('./admin/admin.component').then(m=>m.AdminComponent)}];
