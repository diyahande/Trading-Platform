import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';
import { AccountControllerService } from '../../generated/api-client/api/accountController.service';
import { PositionResponse } from '../../generated/api-client/model/positionResponse';

type Holding = {
  accountId?: string;
  symbol?: string;
  quantity?: number;
  averageCost?: number;
  totalValue?: number;
};

const SYMBOL_SUFFIXED_TICKER = /^[A-Z][A-Z0-9]{0,12}\.[A-Z0-9]{1,6}$/;
const SYMBOL_FX = /^FX:[A-Z]{6}$/;
const SYMBOL_PLAIN_TICKER = /^[A-Z][A-Z0-9]{0,19}$/;

const HOLDINGS_ERROR_MESSAGES: Record<string, string> = {
  'ACC-404': 'The account could not be found.',
  'ACC-403': 'This account is not active, or this session cannot trade it.',
  'AUTH-401': 'Your session has expired. Please sign in again.',
};

@Component({
  selector: 'app-holdings',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './holdings.html',
  styleUrl: './holdings.css',
})
export class Holdings implements OnInit {
  readonly accountId = signal('');
  readonly holdings = signal<Holding[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly isProfileMenuOpen = signal(false);

  constructor(
    private readonly auth: Auth,
    private readonly accountApi: AccountControllerService,
  ) {}

  ngOnInit(): void {
    this.accountId.set(this.readAccountIdFromToken());
    void this.loadHoldings();
  }

  async loadHoldings(): Promise<void> {
    const accountRef = this.accountId().trim();
    console.log('[Holdings] loadHoldings called, accountRef:', accountRef);

    if (!accountRef) {
      this.holdings.set([]);
      this.errorMessage.set('No account is available for this session. Please sign in again.');
      return;
    }

    const validToken = await this.auth.getValidAccessToken();
    if (!validToken) {
      this.holdings.set([]);
      this.errorMessage.set('Your session has expired. Please sign in again.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      console.log('[Holdings] Calling getPositions with accountRef:', accountRef);
      const response = await firstValueFrom(this.accountApi.getPositions(accountRef));
      console.log('[Holdings] Raw API Response received');
      console.log('[Holdings] Response type:', typeof response);
      console.log('[Holdings] Is array?', Array.isArray(response));
      console.log('[Holdings] Response keys:', response && typeof response === 'object' ? Object.keys(response) : 'N/A');
      console.log('[Holdings] Full response:', JSON.stringify(response, null, 2));

      const holdings = await this.normalizeHoldingsResponse(response);
      console.log('[Holdings] After normalizeHoldingsResponse, holdings length:', holdings.length);
      console.log('[Holdings] Normalized holdings:', holdings);

      const processed: Holding[] = holdings.map(position => {
        const quantity = Number(position.quantity) || 0;
        const averageCost = Number(position.averageCost) || 0;
        const totalValue = quantity * averageCost;
        console.log('[Holdings] Processing:', position.symbol, '- quantity:', quantity, 'averageCost:', averageCost, 'totalValue:', totalValue);
        return {
          ...position,
          quantity,
          averageCost,
          totalValue,
        };
      });

      console.log('[Holdings] Final processed holdings:', processed);
      this.holdings.set(processed);
      console.log('[Holdings] Holdings set, length:', this.holdings().length);
    } catch (error: any) {
      console.error('[Holdings] Error loading holdings:', error);
      this.errorMessage.set(this.mapHoldingsError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async refreshNow(): Promise<void> {
    await this.loadHoldings();
  }

  toggleProfileMenu(event: Event): void {
    event.preventDefault();
    this.isProfileMenuOpen.set(!this.isProfileMenuOpen());
  }

  viewProfile(): void {
    this.isProfileMenuOpen.set(false);
  }

  signOut(): void {
    this.isProfileMenuOpen.set(false);
    this.auth.signOut();
  }

  formatMoney(value: number | null | undefined, symbol?: string): string {
    if (value === null || value === undefined) {
      return '—';
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

  formatNumber(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 8,
      maximumFractionDigits: 8,
    }).format(value);
  }

  private readAccountIdFromToken(): string {
    const token = this.auth.token;
    if (!token) {
      console.warn('[Holdings] No token available');
      return '';
    }

    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        console.warn('[Holdings] Invalid token format');
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
      console.warn('[Holdings] accountId not found in token payload:', payload);
      return '';
    } catch (error) {
      console.error('[Holdings] Error parsing token:', error);
      return '';
    }
  }

  private mapHoldingsError(error: any): string {
    console.log('[Holdings] Mapping error:', error);

    if (error?.status === 401) {
      return 'Your session has expired. Please sign in again.';
    }
    if (error?.status === 403) {
      return 'You do not have permission to view these holdings.';
    }
    if (error?.status === 404) {
      return 'Account not found. Please check your session.';
    }

    const errorCode = error?.error?.errorCode || error?.error?.code || '';
    const message = HOLDINGS_ERROR_MESSAGES[errorCode];
    if (message) {
      return message;
    }

    const errorMsg = error?.error?.message || error?.message || 'Failed to load holdings. Please try again.';
    return errorMsg;
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

  private async normalizeHoldingsResponse(response: unknown): Promise<any[]> {
    console.log('[Holdings] normalizeHoldingsResponse called with:', response);

    if (response instanceof Blob) {
      try {
        const text = await response.text();
        console.log('[Holdings] Blob converted to text:', text);
        return this.normalizeHoldingsResponse(JSON.parse(text));
      } catch (e) {
        console.error('[Holdings] Error parsing Blob:', e);
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
        console.log('[Holdings] String parsed to:', parsed);
        return this.normalizeHoldingsResponse(parsed);
      } catch (e) {
        console.error('[Holdings] Error parsing string:', e);
        return [];
      }
    }

    if (Array.isArray(response)) {
      console.log('[Holdings] Response is already an array, length:', response.length);
      return response as any[];
    }

    if (response && typeof response === 'object') {
      const record = response as Record<string, unknown>;
      const wrappedKeys = ['holdings', 'positions', 'items', 'data', 'content', 'results', 'payload', 'body'];

      for (const key of wrappedKeys) {
        const candidate = record[key];
        console.log('[Holdings] Checking wrapped key:', key, 'value:', candidate);
        if (typeof candidate !== 'undefined') {
          const normalized = await this.normalizeHoldingsResponse(candidate);
          if (normalized.length > 0 || Array.isArray(candidate)) {
            console.log('[Holdings] Found data in wrapped key:', key);
            return normalized;
          }
        }
      }

      // Fallback: Check if object values are holding objects
      const values = Object.values(record);
      if (values.length > 0 && values.every((value: unknown) => value && typeof value === 'object')) {
        const mapped = values as any[];
        if (mapped.some((entry) => typeof entry.symbol === 'string')) {
          console.log('[Holdings] Found holdings in object values');
          return mapped;
        }
      }

      // Check if the object itself is a single holding
      if (typeof record['symbol'] === 'string' && (record['quantity'] || record['averageCost'])) {
        console.log('[Holdings] Object is a single holding');
        return [record];
      }
    }

    console.warn('[Holdings] Could not normalize response, returning empty array');
    return [];
  }
}
