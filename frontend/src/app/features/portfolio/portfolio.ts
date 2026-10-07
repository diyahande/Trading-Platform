import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';

type PortfolioSummary = {
  accountId: string;
  baseCurrency: string;
  cashBalance: number;
  marketValue: number;
  costBasis: number;
  unrealisedPnl: number;
  unrealisedPnlPercent: number | null;
  realisedPnl: number;
  totalValue: number;
  positionCount: number;
  partial: boolean;
  asOf: string;
};

type PricedPosition = {
  accountId: string;
  symbol: string;
  quantity: number;
  averageCost: number;
  costBasis: number;
  lastPrice: number | null;
  marketValue: number | null;
  unrealisedPnl: number | null;
  unrealisedPnlPercent: number | null;
  currency: string;
  priceAsOf: string | null;
  stale: boolean;
};

type SymbolPnl = {
  symbol: string;
  realisedPnl: number;
  unrealisedPnl: number;
  totalPnl: number;
};

type PnlResponse = {
  accountId: string;
  baseCurrency: string;
  from: string | null;
  to: string | null;
  realisedPnl: number;
  unrealisedPnl: number;
  totalPnl: number;
  bySymbol?: SymbolPnl[];
  asOf: string;
};

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.css',
})
export class Portfolio implements OnInit {
  readonly accountId = signal('');
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly positions = signal<PricedPosition[]>([]);
  readonly pnl = signal<PnlResponse | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly isProfileMenuOpen = signal(false);
  readonly isNavMenuOpen = signal(false);

  constructor(
    private readonly auth: Auth,
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly elementRef: ElementRef<HTMLElement>,
  ) {}

  ngOnInit(): void {
    this.accountId.set(this.readAccountIdFromToken());
    void this.loadPortfolio();
  }

  async refreshNow(): Promise<void> {
    await this.loadPortfolio();
  }

  toggleProfileMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.isProfileMenuOpen.set(!this.isProfileMenuOpen());
  }

  toggleNavMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.isNavMenuOpen.set(!this.isNavMenuOpen());
  }

  viewProfile(): void {
    this.isProfileMenuOpen.set(false);
    this.isNavMenuOpen.set(false);
    void this.router.navigate(['/change-password']);
  }

  signOut(): void {
    this.isProfileMenuOpen.set(false);
    this.isNavMenuOpen.set(false);
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
    if (!this.isProfileMenuOpen() && !this.isNavMenuOpen()) {
      return;
    }

    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.isProfileMenuOpen.set(false);
      this.isNavMenuOpen.set(false);
    }
  }

  formatMoney(value: number | null | undefined, currency = 'USD'): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '--';
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  formatPercent(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '--';
    }
    return `${value.toFixed(2)}%`;
  }

  toSignedMoneyClass(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (value > 0) {
      return 'positive';
    }
    if (value < 0) {
      return 'negative';
    }
    return '';
  }

  private async loadPortfolio(): Promise<void> {
    const accountRef = this.accountId().trim();
    if (!accountRef) {
      this.errorMessage.set('No account is available for this session. Please sign in again.');
      return;
    }

    const validToken = await this.auth.getValidAccessToken();
    if (!validToken) {
      this.errorMessage.set('Your session has expired. Please sign in again.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const summaryCall = this.http.get<PortfolioSummary>(`/api/v1/portfolio/${accountRef}`);
      const positionsCall = this.http.get<PricedPosition[]>(`/api/v1/portfolio/${accountRef}/positions`);
      const pnlCall = this.http.get<PnlResponse>(
        `/api/v1/portfolio/${accountRef}/pnl`,
        {
          params: new HttpParams().set('bySymbol', 'true'),
        },
      );

      const [summary, positions, pnl] = await Promise.all([
        firstValueFrom(summaryCall),
        firstValueFrom(positionsCall),
        firstValueFrom(pnlCall),
      ]);

      this.summary.set(summary);
      this.positions.set(positions ?? []);
      this.pnl.set(pnl);
    } catch (error: any) {
      this.errorMessage.set(this.mapError(error));
      this.summary.set(null);
      this.positions.set([]);
      this.pnl.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  private readAccountIdFromToken(): string {
    const token = this.auth.token;
    if (!token) {
      return '';
    }

    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        return '';
      }
      const payload = JSON.parse(atob(parts[1]));
      const candidate = payload.accountId ?? payload.sub;
      if (typeof candidate === 'number') {
        return String(candidate);
      }
      if (typeof candidate === 'string') {
        return candidate.trim();
      }
      return '';
    } catch {
      return '';
    }
  }

  private mapError(error: any): string {
    const status = error?.status;
    const code = error?.error?.errorCode;

    if (status === 401 || code === 'AUTH-401') {
      return 'Your session has expired. Please sign in again.';
    }
    if (status === 403 || code === 'ACC-403') {
      return 'This account is not accessible with your current session.';
    }
    if (status === 404 || code === 'ACC-404') {
      return 'Account not found.';
    }
    if (status === 503 || code === 'MKT-503') {
      return 'Pricing is currently unavailable. Please try again shortly.';
    }

    return error?.error?.message || 'Portfolio data could not be loaded.';
  }
}
