import { ChangeDetectorRef, Component, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';

type WatchlistItem = {
  watchlistId: number;
  symbol: string;
  displayName: string;
  currentPrice: number | null;
};

type PriceAlertItem = {
  alertId: number;
  watchlistId: number;
  threshold: number;
  direction: 'ABOVE' | 'BELOW';
  status: 'ACTIVE' | 'TRIGGERED' | 'DEACTIVATED' | 'DELIVERY_FAILED';
  createdAt: string;
  triggeredAt: string | null;
  updatedAt: string;
};

@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './alerts.html',
  styleUrl: './alerts.css',
})
export class Alerts {
  isProfileMenuOpen = false;
  isNavMenuOpen = false;
  watchlist: WatchlistItem[] = [];
  watchlistError: string | null = null;

  alertsByWatchlist: Record<number, PriceAlertItem[]> = {};
  alertsErrorByWatchlist: Record<number, string> = {};
  newAlertThresholdByWatchlist: Record<number, string> = {};
  newAlertDirectionByWatchlist: Record<number, 'ABOVE' | 'BELOW'> = {};

  creatingAlertForWatchlistId: number | null = null;
  removingAlertId: number | null = null;

  constructor(
    private auth: Auth,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private elementRef: ElementRef<HTMLElement>,
  ) {
    void this.loadWatchlist();
  }

  toggleNavMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.isNavMenuOpen = !this.isNavMenuOpen;
  }

  toggleProfileMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  viewProfile(): void {
    this.isProfileMenuOpen = false;
    this.isNavMenuOpen = false;
    void this.router.navigate(['/change-password']);
  }

  signOut(): void {
    this.isProfileMenuOpen = false;
    this.isNavMenuOpen = false;
    this.auth.signOut();
    void this.router.navigate(['/login']);
  }

  isDarkTheme(): boolean {
    return document.documentElement.getAttribute('data-theme') === 'dark';
  }

  toggleTheme(event?: Event): void {
    event?.stopPropagation();
    const nextTheme = this.isDarkTheme() ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', nextTheme);
    try {
      localStorage.setItem('tradex-theme', nextTheme);
    } catch {
      // Ignore storage errors in restricted browser contexts.
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isProfileMenuOpen && !this.isNavMenuOpen) {
      return;
    }

    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.isProfileMenuOpen = false;
      this.isNavMenuOpen = false;
    }
  }

  formatWatchlistPrice(price: number | null | undefined, symbol: string): string {
    return this.formatPriceBySymbol(price, symbol);
  }

  formatPriceBySymbol(price: number | null | undefined, symbol: string | null | undefined): string {
    if (typeof price !== 'number' || Number.isNaN(price)) {
      return '—';
    }
    const currency = this.resolveCurrencyForSymbol(symbol ?? '');
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
      minimumFractionDigits: 2,
    }).format(price);
  }

  getAlertsForWatchlist(watchlistId: number): PriceAlertItem[] {
    return this.alertsByWatchlist[watchlistId] ?? [];
  }

  isAlertLoading(watchlistId: number): boolean {
    return this.alertsByWatchlist[watchlistId] === undefined && !this.alertsErrorByWatchlist[watchlistId];
  }

  alertStatusClass(status: PriceAlertItem['status']): string {
    if (status === 'TRIGGERED') {
      return 'alert-status-triggered';
    }
    if (status === 'DELIVERY_FAILED') {
      return 'alert-status-failed';
    }
    if (status === 'DEACTIVATED') {
      return 'alert-status-deactivated';
    }
    return 'alert-status-active';
  }

  formatAlertSummary(alert: PriceAlertItem): string {
    const symbol = this.watchlist.find((item) => item.watchlistId === alert.watchlistId)?.symbol;
    return `${alert.direction} ${this.formatPriceBySymbol(alert.threshold, symbol)}`;
  }

  formatAlertTime(alert: PriceAlertItem): string {
    if (!alert.triggeredAt) {
      return 'Not triggered yet';
    }
    const dt = new Date(alert.triggeredAt);
    if (Number.isNaN(dt.getTime())) {
      return 'Triggered';
    }
    return `Triggered ${dt.toLocaleString()}`;
  }

  async createAlert(watchlistId: number): Promise<void> {
    const thresholdRaw = (this.newAlertThresholdByWatchlist[watchlistId] ?? '').trim();
    const threshold = Number(thresholdRaw);
    const direction = this.newAlertDirectionByWatchlist[watchlistId] ?? 'ABOVE';

    if (!thresholdRaw || Number.isNaN(threshold) || threshold <= 0) {
      this.alertsErrorByWatchlist[watchlistId] = 'Enter a valid threshold price.';
      this.cdr.markForCheck();
      return;
    }

    this.alertsErrorByWatchlist[watchlistId] = '';
    this.creatingAlertForWatchlistId = watchlistId;
    this.cdr.markForCheck();

    try {
      await firstValueFrom(this.http.post<PriceAlertItem>('/api/v1/alerts', {
        watchlistId,
        threshold,
        direction,
      }));

      this.newAlertThresholdByWatchlist[watchlistId] = '';
      await this.loadAlertsForWatchlist(watchlistId);
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 409) {
        this.alertsErrorByWatchlist[watchlistId] = 'Alert limit reached or duplicate alert.';
      } else if (status === 404) {
        this.alertsErrorByWatchlist[watchlistId] = 'Watchlist item not found.';
      } else if (status === 422) {
        this.alertsErrorByWatchlist[watchlistId] = 'Invalid alert input.';
      } else if (status === 401) {
        this.alertsErrorByWatchlist[watchlistId] = 'Session expired. Please sign in again.';
      } else {
        this.alertsErrorByWatchlist[watchlistId] = 'Could not create alert.';
      }
      this.cdr.markForCheck();
    } finally {
      this.creatingAlertForWatchlistId = null;
      this.cdr.markForCheck();
    }
  }

  async deactivateAlert(alertId: number, watchlistId: number): Promise<void> {
    this.alertsErrorByWatchlist[watchlistId] = '';
    this.removingAlertId = alertId;
    this.cdr.markForCheck();

    try {
      await firstValueFrom(this.http.delete<void>(`/api/v1/alerts/${alertId}`));
      await this.loadAlertsForWatchlist(watchlistId);
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.alertsErrorByWatchlist[watchlistId] = 'Session expired. Please sign in again.';
      } else {
        this.alertsErrorByWatchlist[watchlistId] = 'Could not deactivate alert.';
      }
      this.cdr.markForCheck();
    } finally {
      this.removingAlertId = null;
      this.cdr.markForCheck();
    }
  }

  private async loadWatchlist(): Promise<void> {
    this.watchlistError = null;
    try {
      const response = await firstValueFrom(this.http.get<WatchlistItem[]>('/api/v1/watchlist'));
      this.watchlist = response;
      await Promise.all(response.map((item) => this.loadAlertsForWatchlist(item.watchlistId)));
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.watchlistError = 'Session expired. Please sign in again.';
      } else {
        this.watchlistError = 'Watchlist could not be loaded.';
      }
      this.watchlist = [];
      this.alertsByWatchlist = {};
      this.cdr.markForCheck();
    }
  }

  private async loadAlertsForWatchlist(watchlistId: number): Promise<void> {
    try {
      const alerts = await firstValueFrom(
        this.http.get<PriceAlertItem[]>(`/api/v1/watchlist/${watchlistId}/alerts`)
      );
      this.alertsByWatchlist[watchlistId] = alerts;
      if (!this.newAlertDirectionByWatchlist[watchlistId]) {
        this.newAlertDirectionByWatchlist[watchlistId] = 'ABOVE';
      }
      this.alertsErrorByWatchlist[watchlistId] = '';
    } catch {
      this.alertsByWatchlist[watchlistId] = [];
      this.alertsErrorByWatchlist[watchlistId] = 'Alerts could not be loaded.';
    }
  }

  private resolveCurrencyForSymbol(symbol: string): string {
    const normalized = symbol.trim().toUpperCase();
    if (normalized.endsWith('.NS') || normalized.endsWith('.BO')) {
      return 'INR';
    }
    return 'USD';
  }
}
