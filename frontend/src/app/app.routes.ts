import { Routes } from '@angular/router';
import { ChangePassword } from './features/auth/change-password/change-password';
import { Login } from './features/auth/login/login';
import { Signup } from './features/auth/signup/signup';
import { Dashboard } from './features/dashboard/dashboard';
import { Admin } from './features/admin/admin';

export const routes: Routes = [
	{ path: 'login', component: Login },
	{ path: 'change-password', component: ChangePassword },
	{ path: 'signup', component: Signup },
	{ path: 'admin', component: Admin },
	{ path: 'dashboard', component: Dashboard },
	{ path: '', redirectTo: 'login', pathMatch: 'full' },
];
