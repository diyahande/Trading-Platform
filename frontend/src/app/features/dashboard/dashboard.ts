import { Component, ChangeDetectorRef, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';
import { AiChat } from '../../shared/components/ai-chat/ai-chat';
import { ProfileService } from '../../generated/api-client/api/profile.service';

type DashboardSummary = {
  portfolioValue: number | null;
  todaysPnl: number | null;
  availableCash: number;
  buyingPower: number;
};

type WatchlistItem = {
  watchlistId: number;
  symbol: string;
  displayName: string;
  assetClass: string;
  currentPrice: number;
  change: number;
  changePercent: number;
  addedAt: string;
};

type RecentActivity = {
  orderId: string;
  accountId: string;
  symbol: string;
  side: string;
  quantity: number;
  price: number;
  executedPrice: number;
  status: string;
  createdOn: string;
};

type PortfolioAllocation = {
  totalPortfolioValue: number;
  cashValue: number;
  allocationByAssetClass: { [key: string]: number };
};

type MarketIndexQuote = {
  indexKey: string;
  price: number;
  change: number | null;
  changePercent: number | null;
  currency: string;
};

type MarketOverview = {
  indices: { [key: string]: MarketIndexQuote };
};

type MarketIndexHistory = {
  indexKey: string;
  history: Array<{ price: number; timestamp: number }>;
};

type ChartPoint = {
  x: number;
  y: number;
};

type TopMover = {
  symbol: string;
  displayName: string;
  assetClass: string;
  price: number;
  change: number;
  changePercent: number;
};

type TopMoversTab = 'gainers' | 'losers';

type StockPerformance = {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  lastUpdated: number;
};

type StockPerformanceVisualization = {
  stocks: StockPerformance[];
  statistics: {
    total: number;
    gainers: number;
    losers: number;
    averageChangePercent: number;
  };
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, AiChat],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard {
  username = 'Trader';
  isProfileMenuOpen = false;
  accountReference: string | null = null;
  summary: DashboardSummary | null = null;
  summaryError: string | null = null;
  watchlist: WatchlistItem[] = [];
  watchlistError: string | null = null;
  recentActivity: RecentActivity[] = [];
  recentActivityError: string | null = null;
  portfolioAllocation: PortfolioAllocation | null = null;
  portfolioAllocationError: string | null = null;
  stockPerformance: StockPerformanceVisualization | null = null;
  stockPerformanceError: string | null = null;
  topMoversTab: TopMoversTab = 'gainers';
  topGainers: TopMover[] = [];
  topLosers: TopMover[] = [];
  topMoversError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private auth: Auth,
    private router: Router,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private elementRef: ElementRef<HTMLElement>,
    private profileService: ProfileService,
  ) {
    const routeUsername = this.route.snapshot.queryParamMap.get('username');
    if (routeUsername && routeUsername.trim()) {
      this.username = routeUsername;
    }

    void this.loadCurrentUser();

    void this.loadDashboard();
    void this.loadWatchlist();
    void this.loadRecentActivity();
    void this.loadPortfolioAllocation();
    void this.loadStockPerformance();
    void this.loadTopMovers();
  }

  signOut() {
    this.isProfileMenuOpen = false;
    this.auth.signOut();
    void this.router.navigate(['/login']);
  }

  toggleProfileMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  viewProfile(): void {
    this.isProfileMenuOpen = false;
    void this.router.navigate(['/change-password']);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isProfileMenuOpen) {
      return;
    }

    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.isProfileMenuOpen = false;
    }
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

  formatChangePercent(changePercent: number): string {
    const sign = changePercent >= 0 ? '▲' : '▼';
    const absValue = Math.abs(changePercent).toFixed(2);
    return `${sign} ${absValue}%`;
  }

  changeClass(changePercent: number): string {
    return changePercent >= 0 ? 'positive' : 'negative';
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

  private async loadCurrentUser(): Promise<void> {
    try {
      const user = await firstValueFrom(this.profileService.authControllerMe());
      if (user?.username?.trim()) {
        this.username = user.username.trim();
        this.cdr.markForCheck();
      }
    } catch {
      // Keep existing username fallback when profile endpoint is unavailable.
    }
  }

  private async loadWatchlist(): Promise<void> {
    this.watchlistError = null;
    try {
      const response = await firstValueFrom(this.http.get<WatchlistItem[]>('/api/v1/watchlist'));
      this.watchlist = response;
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.watchlistError = 'Session expired. Please sign in again.';
      } else {
        this.watchlistError = 'Watchlist could not be loaded.';
      }
      this.watchlist = [];
      this.cdr.markForCheck();
    }
  }

  private async loadRecentActivity(): Promise<void> {
    this.recentActivityError = null;
    try {
      // Get account reference from localStorage (stored by auth service)
      const token = localStorage.getItem('accessToken');
      if (!token) {
        this.recentActivityError = 'Authentication required.';
        return;
      }

      // Decode JWT to get accountId claim
      const accountId = this.extractAccountIdFromToken(token);
      if (!accountId) {
        this.recentActivityError = 'Account ID not found.';
        return;
      }

      const response = await firstValueFrom(
        this.http.get<RecentActivity[]>(`/api/v1/accounts/${accountId}/orders`)
      );
      // Sort by createdOn date (most recent first) and take last 5
      const sorted = response.sort((a, b) => new Date(b.createdOn).getTime() - new Date(a.createdOn).getTime());
      this.recentActivity = sorted.slice(0, 5);
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.recentActivityError = 'Session expired. Please sign in again.';
      } else {
        this.recentActivityError = 'Recent activity could not be loaded.';
      }
      this.recentActivity = [];
      this.cdr.markForCheck();
    }
  }

  private extractAccountIdFromToken(token: string): string | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;

      const payload = parts[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.accountId || decoded.sub;
    } catch (error) {
      console.error('Failed to decode JWT:', error);
      return null;
    }
  }

  getActivityDescription(activity: RecentActivity): string {
    const action = activity.side === 'BUY' ? 'Bought' : 'Sold';
    return `${action} ${activity.quantity} shares of ${activity.symbol}`;
  }

  getActivityIcon(activity: RecentActivity): string {
    return activity.side === 'BUY' ? 'buy' : 'sell';
  }

  getActivityIconSymbol(activity: RecentActivity): string {
    return activity.side === 'BUY' ? '↗' : '↘';
  }

  getRelativeTime(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) return 'Just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    const days = Math.floor(hours / 24);
    return `${days} day${days > 1 ? 's' : ''} ago`;
  }

  private async loadPortfolioAllocation(): Promise<void> {
    this.portfolioAllocationError = null;
    try {
      const response = await firstValueFrom(this.http.get<PortfolioAllocation>('/api/portfolio-allocation'));
      this.portfolioAllocation = response;
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.portfolioAllocationError = 'Session expired. Please sign in again.';
      } else {
        this.portfolioAllocationError = 'Portfolio allocation could not be loaded.';
      }
      this.portfolioAllocation = null;
      this.cdr.markForCheck();
    }
  }

  private async loadStockPerformance(): Promise<void> {
    this.stockPerformanceError = null;
    try {
      const response = await firstValueFrom(
        this.http.get<StockPerformanceVisualization>('/api/market-performance/visualization')
      );
      this.stockPerformance = response;
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.stockPerformanceError = 'Session expired. Please sign in again.';
      } else if (status === 503) {
        this.stockPerformanceError = 'Market data is unavailable.';
      } else {
        this.stockPerformanceError = 'Market performance data could not be loaded.';
      }
      this.stockPerformance = null;
      this.cdr.markForCheck();
    }
  }

  private async loadTopMovers(): Promise<void> {
    this.topMoversError = null;
    try {
      const [gainersResponse, losersResponse] = await Promise.all([
        firstValueFrom(this.http.get<TopMover[]>('/api/top-movers/gainers')),
        firstValueFrom(this.http.get<TopMover[]>('/api/top-movers/losers')),
      ]);
      this.topGainers = gainersResponse;
      this.topLosers = losersResponse;
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.topMoversError = 'Session expired. Please sign in again.';
      } else if (status === 503) {
        this.topMoversError = 'Market data is unavailable.';
      } else {
        this.topMoversError = 'Top movers could not be loaded.';
      }
      this.topGainers = [];
      this.topLosers = [];
      this.cdr.markForCheck();
    }
  }

  selectTopMoversTab(tab: TopMoversTab): void {
    this.topMoversTab = tab;
    this.cdr.markForCheck();
  }

  getTopMovers(): TopMover[] {
    return this.topMoversTab === 'gainers' ? this.topGainers : this.topLosers;
  }

  getTopMoverChangeClass(changePercent: number): string {
    return changePercent >= 0 ? 'positive' : 'negative';
  }
}
