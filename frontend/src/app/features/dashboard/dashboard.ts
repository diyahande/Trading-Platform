import { Component, ChangeDetectorRef, ElementRef, HostListener, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';
import { AiChat } from '../../shared/components/ai-chat/ai-chat';
import { ProfileService } from '../../generated/api-client/api/profile.service';
import { SignalCardComponent } from './signal-card';
import type { SignalResponse } from './signal-card';

type DashboardSummary = {
  portfolioValue: number | null;
  todaysPnl: number | null;
  availableCash: number;
  buyingPower: number;
};

type PortfolioSummaryFallback = {
  cashBalance: number;
  totalValue: number;
};

type PnlResponseFallback = {
  unrealisedPnl: number;
};

type WatchlistItem = {
  watchlistId: number;
  symbol: string;
  displayName: string;
  assetClass: string;
  currentPrice: number | null;
  change: number | null;
  changePercent: number | null;
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
  imports: [CommonModule, AiChat, RouterLink, RouterLinkActive, SignalCardComponent],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard implements OnDestroy {
  username = 'Trader';
  isProfileMenuOpen = false;
  isNavMenuOpen = false;
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
  hoveredAssetClass: string | null = null;
  hoveredAssetValue: number | null = null;
  hoveredAssetValueInMoney: number | null = null;
  selectedStockSymbol: string | null = null;
  selectedStockPrice: number | null = null;
  selectedSignalPayload: SignalResponse | null = null;
  signalFetchError: string | null = null;
  showSignalCard = false;
  private previousBodyOverflow = '';

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
    this.isNavMenuOpen = false;
    this.auth.signOut();
    void this.router.navigate(['/login']);
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

  formatChangePercent(changePercent: number | null | undefined): string {
    if (typeof changePercent !== 'number' || Number.isNaN(changePercent)) {
      return '—';
    }
    const sign = changePercent >= 0 ? '▲' : '▼';
    const absValue = Math.abs(changePercent).toFixed(2);
    return `${sign} ${absValue}%`;
  }

  changeClass(changePercent: number | null | undefined): string {
    if (typeof changePercent !== 'number' || Number.isNaN(changePercent)) {
      return '';
    }
    return changePercent >= 0 ? 'positive' : 'negative';
  }

  formatWatchlistPrice(price: number | null | undefined): string {
    if (typeof price !== 'number' || Number.isNaN(price)) {
      return '—';
    }
    return `$${price.toFixed(2)}`;
  }

  private async loadDashboard(): Promise<void> {
    this.summaryError = null;
    try {
      const response = await firstValueFrom(this.http.get<DashboardSummary>('/api/dashboard'));
      const fallback = await this.loadDashboardSummaryFallback(response);

      this.summary = {
        portfolioValue: response.portfolioValue ?? fallback?.portfolioValue ?? null,
        todaysPnl: response.todaysPnl ?? fallback?.todaysPnl ?? null,
        availableCash: response.availableCash ?? fallback?.availableCash ?? 0,
        buyingPower: response.buyingPower ?? fallback?.availableCash ?? 0,
      };
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
  private async loadDashboardSummaryFallback(
    summary: DashboardSummary,
  ): Promise<{ portfolioValue: number | null; todaysPnl: number | null; availableCash: number | null } | null> {
    if (summary.portfolioValue !== null && summary.todaysPnl !== null) {
      return null;
    }

    const token = localStorage.getItem('accessToken');
    if (!token) {
      return null;
    }

    const accountId = this.extractAccountIdFromToken(token);
    if (!accountId) {
      return null;
    }

    const [portfolioResult, pnlResult] = await Promise.allSettled([
      firstValueFrom(this.http.get<PortfolioSummaryFallback>(`/api/v1/portfolio/${accountId}`)),
      firstValueFrom(this.http.get<PnlResponseFallback>(`/api/v1/portfolio/${accountId}/pnl`)),
    ]);

    const portfolioValue = portfolioResult.status === 'fulfilled' ? portfolioResult.value.totalValue ?? null : null;
    const todaysPnl = pnlResult.status === 'fulfilled' ? pnlResult.value.unrealisedPnl ?? null : null;
    const availableCash = portfolioResult.status === 'fulfilled' ? portfolioResult.value.cashBalance ?? null : null;

    if (portfolioValue === null && todaysPnl === null && availableCash === null) {
      return null;
    }

    return { portfolioValue, todaysPnl, availableCash };
  }

  private async loadCurrentUser(): Promise<void> {
    try {
      const user = await firstValueFrom(this.profileService.authControllerMe());
      if (typeof user?.username === 'string' && user.username.trim()) {
        this.username = user.username.trim();
      }
      this.cdr.markForCheck();
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
      // Filter to only FILLED orders, sort by createdOn date (most recent first) and take last 5
      const filtered = response.filter(order => order.status === 'FILLED');
      const sorted = filtered.sort((a, b) => new Date(b.createdOn).getTime() - new Date(a.createdOn).getTime());
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

  getDonutGradient(): string {
    if (!this.portfolioAllocation?.allocationByAssetClass) {
      return 'conic-gradient(#475a6b 0deg 360deg)';
    }

    const allocation = this.portfolioAllocation.allocationByAssetClass;
    const colors: { [key: string]: string } = {
      'EQUITY': '#00c99b',
      'ETF': '#2d82c7',
      'CRYPTO': '#8071e8',
      'CASH': '#718294',
      'FOREX': '#d97706'
    };

    let gradient = 'conic-gradient(';
    let currentDeg = 0;

    const assetClasses = ['EQUITY', 'ETF', 'CRYPTO', 'CASH', 'FOREX'];
    for (const assetClass of assetClasses) {
      const percent = allocation[assetClass] || 0;
      if (percent > 0) {
        const degrees = (percent / 100) * 360;
        const color = colors[assetClass];
        gradient += `${color} ${currentDeg.toFixed(1)}deg ${(currentDeg + degrees).toFixed(1)}deg, `;
        currentDeg += degrees;
      }
    }

    gradient += `#475a6b ${currentDeg.toFixed(1)}deg 360deg)`;
    return gradient;
  }

  onAssetHover(assetClass: string): void {
    const allocation = this.portfolioAllocation?.allocationByAssetClass;
    if (allocation && this.portfolioAllocation) {
      this.hoveredAssetClass = assetClass;
      const percentValue = allocation[assetClass] || 0;
      this.hoveredAssetValue = percentValue;
      // Calculate the actual money value
      this.hoveredAssetValueInMoney = (percentValue / 100) * this.portfolioAllocation.totalPortfolioValue;
    }
  }

  onAssetLeave(): void {
    this.hoveredAssetClass = null;
    this.hoveredAssetValue = null;
    this.hoveredAssetValueInMoney = null;
  }

  getAssetLabel(assetClass: string): string {
    const labels: { [key: string]: string } = {
      'EQUITY': 'Stocks',
      'ETF': 'ETFs',
      'CRYPTO': 'Crypto',
      'CASH': 'Cash',
      'FOREX': 'Forex'
    };
    return labels[assetClass] || assetClass;
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

  async onStockClick(stock: StockPerformance): Promise<void> {
    this.lockBodyScroll();
    this.selectedStockSymbol = stock.symbol;
    this.selectedStockPrice = stock.price;
    this.selectedSignalPayload = null;
    this.signalFetchError = null;
    this.showSignalCard = false;

    try {
      this.selectedSignalPayload = await firstValueFrom(
        this.http.get<SignalResponse>(`/api/v1/signals/${encodeURIComponent(stock.symbol)}`)
      );
    } catch {
      this.signalFetchError = 'Signal data is temporarily unavailable for this symbol.';
    }

    this.showSignalCard = true;
    this.cdr.markForCheck();
  }

  closeSignalCard(): void {
    this.showSignalCard = false;
    this.selectedStockSymbol = null;
    this.selectedStockPrice = null;
    this.selectedSignalPayload = null;
    this.signalFetchError = null;
    this.unlockBodyScroll();
    this.cdr.markForCheck();
  }

  placeOrderFromSignal(side: 'BUY' | 'SELL'): void {
    const symbol = this.selectedStockSymbol;
    if (!symbol) {
      return;
    }

    const queryParams: Record<string, string> = {
      symbol,
      side,
    };

    if (typeof this.selectedStockPrice === 'number' && Number.isFinite(this.selectedStockPrice) && this.selectedStockPrice > 0) {
      queryParams['marketPrice'] = this.selectedStockPrice.toFixed(2);
    }

    this.closeSignalCard();
    void this.router.navigate(['/orders'], { queryParams });
  }

  ngOnDestroy(): void {
    this.unlockBodyScroll();
  }

  private lockBodyScroll(): void {
    if (!this.previousBodyOverflow) {
      this.previousBodyOverflow = document.body.style.overflow;
    }
    document.body.style.overflow = 'hidden';
  }

  private unlockBodyScroll(): void {
    document.body.style.overflow = this.previousBodyOverflow;
    this.previousBodyOverflow = '';
  }
}
