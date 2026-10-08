import { Routes } from '@angular/router';
import { ChangePassword } from './features/auth/change-password/change-password';
import { Login } from './features/auth/login/login';
import { Signup } from './features/auth/signup/signup';
import { Dashboard } from './features/dashboard/dashboard';
import { Admin } from './features/admin/admin';
import { authGuard } from './core/guards/auth-guard';
import { Orders } from './features/orders/orders';
import { Blotter } from './features/blotter/blotter';
import { Holdings } from './features/holdings/holdings';
import { Portfolio } from './features/portfolio/portfolio';
import { Home, RootRedirect } from './features/home/home';
import { Alerts } from './features/alerts/alerts';
import { Preferences } from './features/preferences/preferences';
import { Profile } from './features/profile/profile';

export const routes: Routes = [
	{ path: '', component: RootRedirect, pathMatch: 'full' },
	{ path: 'home', component: Home },
	{ path: 'login', component: Login },
	{ path: 'change-password', component: ChangePassword, canActivate: [authGuard] },
	{ path: 'signup', component: Signup },
	{ path: 'admin', component: Admin, canActivate: [authGuard] },
	{ path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
	{ path: 'orders', component: Orders, canActivate: [authGuard] },
	{ path: 'blotter', component: Blotter, canActivate: [authGuard] },
	{ path: 'holdings', component: Holdings, canActivate: [authGuard] },
	{ path: 'portfolio', component: Portfolio, canActivate: [authGuard] },
	{ path: 'alerts', component: Alerts, canActivate: [authGuard] },
	{ path: 'preferences', component: Preferences, canActivate: [authGuard] },
	{ path: 'profile', component: Profile, canActivate: [authGuard] },
	{ path: '**', redirectTo: 'dashboard' },

];
