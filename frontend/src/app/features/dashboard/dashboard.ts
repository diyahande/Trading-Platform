import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
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

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard {
  username = 'Trader';
  accountReference: string | null = null;
  summary: DashboardSummary | null = null;
  summaryError: string | null = null;
  watchlist: WatchlistItem[] = [];
  watchlistError: string | null = null;
  recentActivity: RecentActivity[] = [];
  recentActivityError: string | null = null;
  portfolioAllocation: PortfolioAllocation | null = null;
  portfolioAllocationError: string | null = null;
  marketOverview: MarketOverview | null = null;
  marketOverviewError: string | null = null;
  selectedIndex: string = 'NIFTY_50';
  chartData: ChartPoint[] = [];
  chartYAxisLabels: string[] = [];
  topMoversTab: TopMoversTab = 'gainers';
  topGainers: TopMover[] = [];
  topLosers: TopMover[] = [];
  topMoversError: string | null = null;

  constructor(private route: ActivatedRoute, private auth: Auth, private router: Router, private http: HttpClient, private cdr: ChangeDetectorRef) {
    const routeUsername = this.route.snapshot.queryParamMap.get('username');
    if (routeUsername && routeUsername.trim()) {
      this.username = routeUsername;
    }

    void this.loadDashboard();
    void this.loadWatchlist();
    void this.loadRecentActivity();
    void this.loadPortfolioAllocation();
    void this.loadMarketOverview();
    void this.loadTopMovers();
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

  private async loadMarketOverview(): Promise<void> {
    this.marketOverviewError = null;
    try {
      const response = await firstValueFrom(this.http.get<MarketOverview>('/api/market-overview'));
      this.marketOverview = response;
      // Load chart data for the selected index
      await this.updateChartData(this.selectedIndex);
      this.cdr.markForCheck();
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : null;
      if (status === 401) {
        this.marketOverviewError = 'Session expired. Please sign in again.';
      } else if (status === 503) {
        this.marketOverviewError = 'Market data is unavailable.';
      } else {
        this.marketOverviewError = 'Market overview could not be loaded.';
      }
      this.marketOverview = null;
      this.cdr.markForCheck();
    }
  }

  selectIndex(indexKey: string): void {
    this.selectedIndex = indexKey;
    void this.updateChartData(indexKey);
  }

  private async updateChartData(indexKey: string): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.http.get<MarketIndexHistory>(`/api/market-overview/${indexKey}/history`)
      );

      if (!response.history || response.history.length === 0) {
        this.chartData = [];
        this.chartYAxisLabels = [];
        return;
      }

      // Convert to chart points with normalized x coordinates (0 to 1000)
      const minPrice = Math.min(...response.history.map(h => h.price));
      const maxPrice = Math.max(...response.history.map(h => h.price));
      const priceRange = maxPrice - minPrice || 1;

      this.chartData = response.history.map((point, index) => ({
        x: (index / (response.history.length - 1 || 1)) * 1000,
        y: 220 - ((point.price - minPrice) / priceRange) * 180,
      }));

      // Generate SVG path data
      const pathData = this.chartData
        .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
        .join(' ');

      // Store the path for SVG rendering
      this.chartPathData = pathData;

      // Generate Y-axis labels
      const labelCount = 4;
      this.chartYAxisLabels = [];
      for (let i = 0; i < labelCount; i++) {
        const price = minPrice + (priceRange * (labelCount - 1 - i)) / (labelCount - 1);
        this.chartYAxisLabels.push(price.toFixed(0));
      }

      this.cdr.markForCheck();
    } catch (error: any) {
      console.error('Failed to load chart data:', error);
      this.chartData = [];
      this.chartYAxisLabels = [];
    }
  }

  chartPathData: string = '';

  getCurrentIndexQuote(): MarketIndexQuote | undefined {
    return this.marketOverview?.indices[this.selectedIndex];
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
