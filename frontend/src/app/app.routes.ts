import { Routes } from '@angular/router';
import { ChangePassword } from './features/auth/change-password/change-password';
import { Login } from './features/auth/login/login';
import { Signup } from './features/auth/signup/signup';
import { Dashboard } from './features/dashboard/dashboard';
import { Admin } from './features/admin/admin';
import { authGuard } from './core/guards/auth-guard';

export const routes: Routes = [
	{ path: 'login', component: Login },
	{ path: 'change-password', component: ChangePassword, canActivate: [authGuard] },
	{ path: 'signup', component: Signup, canActivate: [authGuard] },
	{ path: 'admin', component: Admin, canActivate: [authGuard] },
	{ path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
	{ path: '', redirectTo: 'login', pathMatch: 'full' },
];
