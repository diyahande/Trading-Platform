import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Signup } from './features/auth/signup/signup';
import { Dashboard } from './features/dashboard/dashboard';

export const routes: Routes = [
	{ path: 'login', component: Login },
	{ path: 'signup', component: Signup },
	{ path: 'dashboard', component: Dashboard },
	{ path: '', redirectTo: 'login', pathMatch: 'full' },
];
