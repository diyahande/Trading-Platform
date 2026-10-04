import { Component, ChangeDetectorRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';

type DashboardSummary = {
  portfolioValue: number | null;
  todaysPnl: number | null;
  availableCash: number;
  buyingPower: number;
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard {
  username = 'Trader';
  summary: DashboardSummary | null = null;
  summaryError: string | null = null;

  constructor(private route: ActivatedRoute, private auth: Auth, private router: Router, private http: HttpClient, private cdr: ChangeDetectorRef) {
    const routeUsername = this.route.snapshot.queryParamMap.get('username');
    if (routeUsername && routeUsername.trim()) {
      this.username = routeUsername;
    }

    void this.loadDashboard();
  }

  signOut() {
    this.auth.signOut();
    void this.router.navigate(['/login']);
  }

  formatMoney(value: number | null | undefined): string {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      return '—';
    }

    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2,
      minimumFractionDigits: 2,
    }).format(value);
  }

  pnlClass(): string {
    const pnl = this.summary?.todaysPnl;
    if (typeof pnl !== 'number') {
      return '';
    }
    if (pnl > 0) {
      return 'positive';
    }
    if (pnl < 0) {
      return 'negative';
    }
    return '';
  }

  private async loadDashboard(): Promise<void> {
    this.summaryError = null;
    try {
      const response = await firstValueFrom(this.http.get<DashboardSummary>('/api/dashboard'));
      console.log('Dashboard response:', response);
      console.log('availableCash type:', typeof response.availableCash, 'value:', response.availableCash);
      console.log('buyingPower type:', typeof response.buyingPower, 'value:', response.buyingPower);
      this.summary = response;
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.summaryError = 'Session expired. Please sign in again.';
      } else if (status === 503) {
        this.summaryError = 'Market data is unavailable, so dashboard values cannot be calculated right now.';
      } else {
        this.summaryError = 'Dashboard data is temporarily unavailable.';
      }
      this.summary = null;
      this.cdr.markForCheck();
    }
  }
}
