import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';
import { OrderControllerService } from '../../generated/api-client/api/orderController.service';
import { OrderHistoryEntry } from '../../generated/api-client/model/orderHistoryEntry';

type BlotterStatus = 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED' | 'UNKNOWN';

const SYMBOL_SUFFIXED_TICKER = /^[A-Z][A-Z0-9]{0,12}\.[A-Z0-9]{1,6}$/;
const SYMBOL_FX = /^FX:[A-Z]{6}$/;
const SYMBOL_PLAIN_TICKER = /^[A-Z][A-Z0-9]{0,19}$/;

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 10;

const BLOTTER_ERROR_MESSAGES: Record<string, string> = {
  'ACC-404': 'The account could not be found.',
  'ACC-403': 'This account is not active, or this session cannot trade it.',
  'AUTH-401': 'Your session has expired. Please sign in again.',
  'VAL-422': 'A request field is not acceptable.',
};

@Component({
  selector: 'app-blotter',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './blotter.html',
  styleUrl: './blotter.css',
})
export class Blotter implements OnInit, OnDestroy {
  readonly accountId = signal('');
  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly workingMessage = signal<string | null>(null);
  readonly isProfileMenuOpen = signal(false);
  readonly isNavMenuOpen = signal(false);

  private pollTimerId: ReturnType<typeof setTimeout> | null = null;
  private pollAttempts = 0;

  constructor(
    private readonly auth: Auth,
    private readonly orderApi: OrderControllerService,
    private readonly router: Router,
    private readonly elementRef: ElementRef<HTMLElement>,
  ) {}

  ngOnInit(): void {
    this.accountId.set(this.readAccountIdFromToken());
    void this.loadOrderHistory('initial');
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  toggleNavMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.isNavMenuOpen.set(!this.isNavMenuOpen());
  }

  toggleProfileMenu(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.isProfileMenuOpen.set(!this.isProfileMenuOpen());
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

  async refreshNow(): Promise<void> {
    this.pollAttempts = 0;
    await this.loadOrderHistory('manual');
  }

  async loadOrderHistory(trigger: 'initial' | 'manual' | 'poll'): Promise<void> {
    const accountRef = this.accountId().trim();
    if (!accountRef) {
      this.orders.set([]);
      this.errorMessage.set('No account is available for this session. Please sign in again.');
      this.stopPolling();
      return;
    }

    const validToken = await this.auth.getValidAccessToken();
    if (!validToken) {
      this.orders.set([]);
      this.errorMessage.set('Your session has expired. Please sign in again.');
      this.stopPolling();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const response = await firstValueFrom(this.orderApi.getOrders(accountRef));
      const orders = await this.normalizeOrderHistoryResponse(response);
      const sorted = [...orders].sort((left, right) => {
        const leftTime = this.toTimestamp(left.createdOn);
        const rightTime = this.toTimestamp(right.createdOn);
        return rightTime - leftTime;
      });

      this.orders.set(sorted);
      this.updatePollingState(trigger);
    } catch (error: any) {
      this.errorMessage.set(this.mapOrderHistoryError(error));
      this.stopPolling();
    } finally {
      this.loading.set(false);
    }
  }

  statusText(order: OrderHistoryEntry): BlotterStatus {
    const raw = (order.status ?? '').toUpperCase();
    if (raw === 'NEW' || raw === 'FILLED' || raw === 'REJECTED' || raw === 'CANCELLED') {
      return raw;
    }
    return 'UNKNOWN';
  }

  statusClass(order: OrderHistoryEntry): string {
    return `status-${this.statusText(order).toLowerCase()}`;
  }

  hasWorkingOrders(): boolean {
    return this.orders().some((order) => this.statusText(order) === 'NEW');
  }

  formatDate(value: string | undefined): string {
    if (!value) {
      return '--';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '--';
    }

    return new Intl.DateTimeFormat('en-IN', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  }

  formatPrice(value: number | undefined, symbol?: string): string {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      return '--';
    }

    const currency = this.inferDisplayCurrency(symbol);
    if (!currency) {
      return new Intl.NumberFormat('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value);
    }

    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  formatQuantity(value: number | undefined): string {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      return '--';
    }

    return new Intl.NumberFormat('en-US', {
      maximumFractionDigits: 0,
    }).format(value);
  }

  private updatePollingState(trigger: 'initial' | 'manual' | 'poll'): void {
    if (!this.hasWorkingOrders()) {
      this.workingMessage.set(null);
      this.stopPolling();
      return;
    }

    if (trigger === 'poll') {
      this.pollAttempts += 1;
    }

    if (this.pollAttempts >= MAX_POLL_ATTEMPTS) {
      this.workingMessage.set('Order is still working. Press refresh to check again.');
      this.stopPolling();
      return;
    }

    this.workingMessage.set(
      `Auto refreshes every ${Math.floor(POLL_INTERVAL_MS / 1000)} seconds.`,
    );
    this.scheduleNextPoll();
  }

  private scheduleNextPoll(): void {
    this.clearPollTimer();
    this.pollTimerId = setTimeout(() => {
      void this.loadOrderHistory('poll');
    }, POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    this.pollAttempts = 0;
    this.clearPollTimer();
  }

  private clearPollTimer(): void {
    if (this.pollTimerId !== null) {
      clearTimeout(this.pollTimerId);
      this.pollTimerId = null;
    }
  }

  private toTimestamp(value: string | undefined): number {
    if (!value) {
      return 0;
    }

    const timestamp = new Date(value).getTime();
    return Number.isNaN(timestamp) ? 0 : timestamp;
  }

  private async normalizeOrderHistoryResponse(response: unknown): Promise<OrderHistoryEntry[]> {
    if (response instanceof Blob) {
      try {
        const text = await response.text();
        return this.normalizeOrderHistoryResponse(JSON.parse(text));
      } catch (e) {
        return [];
      }
    }

    if (typeof response === 'string') {
      const trimmed = response.trim();
      if (!trimmed) {
        return [];
      }

      try {
        const parsed = JSON.parse(trimmed);
        return this.normalizeOrderHistoryResponse(parsed);
      } catch (e) {
        return [];
      }
    }

    if (Array.isArray(response)) {
      return response as OrderHistoryEntry[];
    }

    if (response && typeof response === 'object') {
      const record = response as Record<string, unknown>;
      const wrappedKeys = ['orders', 'items', 'data', 'content', 'results', 'payload', 'body'];

      for (const key of wrappedKeys) {
        const candidate = record[key];
        if (typeof candidate !== 'undefined') {
          const normalized = await this.normalizeOrderHistoryResponse(candidate);
          if (normalized.length > 0 || Array.isArray(candidate)) {
            return normalized;
          }
        }
      }

      const values = Object.values(record);
      if (values.length > 0 && values.every((value: unknown) => value && typeof value === 'object')) {
        const mapped = values as OrderHistoryEntry[];
        if (mapped.some((entry) => typeof entry.orderId === 'string')) {
          return mapped;
        }
      }

      if (typeof record['orderId'] === 'string') {
        return [record as unknown as OrderHistoryEntry];
      }
    }

    return [];
  }

  private mapOrderHistoryError(error: any): string {
    const status = typeof error?.status === 'number' ? error.status : null;
    const errorCode =
      typeof error?.error?.errorCode === 'string' && error.error.errorCode.trim().length > 0
        ? error.error.errorCode.trim()
        : null;
    const backendMessage =
      typeof error?.error?.message === 'string' && error.error.message.trim().length > 0
        ? error.error.message.trim()
        : null;
    const transportMessage = typeof error?.message === 'string' && error.message.trim().length > 0 ? error.message.trim() : null;

    if (status === 401 || errorCode === 'AUTH-401') {
      return 'Your session has expired. Please sign in again.';
    }

    if (status === 0) {
      return 'Could not reach order history. Check service health and CORS configuration.';
    }

    if (errorCode && BLOTTER_ERROR_MESSAGES[errorCode]) {
      return BLOTTER_ERROR_MESSAGES[errorCode];
    }

    const suffix = [errorCode, status !== null ? `HTTP ${status}` : null, backendMessage, transportMessage]
      .filter((part) => !!part)
      .join(' | ');

    return suffix ? `Order history could not be loaded right now. (${suffix})` : 'Order history could not be loaded right now.';
  }

  private readAccountIdFromToken(): string {
    const token = this.auth.token;
    if (!token) {
      return '';
    }

    const payload = this.readJwtPayload(token);
    if (!payload) {
      return '';
    }

    const candidate = payload['accountId'] ?? payload['sub'];
    if (typeof candidate === 'number') {
      return String(candidate);
    }
    if (typeof candidate === 'string') {
      return candidate.trim();
    }

    return '';
  }

  private readJwtPayload(token: string): Record<string, unknown> | null {
    const parts = token.split('.');
    if (parts.length < 2) {
      return null;
    }

    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
      const json = atob(padded);
      return JSON.parse(json) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private inferDisplayCurrency(symbol: string | null | undefined): 'USD' | 'INR' | null {
    const normalized = symbol?.trim().toUpperCase() ?? '';
    if (!normalized) {
      return 'USD';
    }

    if (SYMBOL_SUFFIXED_TICKER.test(normalized)) {
      if (normalized.endsWith('.NS') || normalized.endsWith('.BO')) {
        return 'INR';
      }
      return null;
    }

    if (SYMBOL_FX.test(normalized)) {
      const pair = normalized.slice(3);
      const quote = pair.slice(3);
      if (quote === 'USD') {
        return 'USD';
      }
      if (quote === 'INR') {
        return 'INR';
      }
      return null;
    }

    if (SYMBOL_PLAIN_TICKER.test(normalized)) {
      return 'USD';
    }

    return null;
  }
}
