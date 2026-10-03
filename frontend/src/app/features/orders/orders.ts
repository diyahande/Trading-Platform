import { CommonModule } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Auth } from '../../core/services/auth';
import { OrderControllerService } from '../../generated/api-client/api/orderController.service';
import { PlaceOrderRequest } from '../../generated/api-client/model/placeOrderRequest';
import { CurrencyConversion, QuoteToUsdEstimate } from '../../core/services/currency-conversion';

type TradeCurrency = 'USD' | 'INR' | 'UNKNOWN';

const SYMBOL_PLAIN_TICKER = /^[A-Z][A-Z0-9]{0,19}$/;
const SYMBOL_SUFFIXED_TICKER = /^[A-Z][A-Z0-9]{0,12}\.[A-Z0-9]{1,6}$/;
const SYMBOL_FX = /^FX:[A-Z]{6}$/;
const SYMBOL_CRYPTO = /^X:[A-Z0-9]{2,15}$/;

const ORDER_ERROR_MESSAGES: Record<string, string> = {
  'ACC-404': 'The account could not be found.',
  'ACC-403': 'This account is not active, or this session cannot trade it.',
  'INS-404': 'The instrument is not one that can be traded.',
  'ORD-400': 'There is not enough cash for this order.',
  'ORD-409': 'Not enough holdings to sell, or this order was already placed.',
  'VAL-422': 'A field value is not acceptable. Check quantity, price, and symbol.',
  'AUTH-401': 'The session has expired or sign-in was refused.',
  'AUTH-409': 'That username is already taken.',
};

type UiError = {
  code: string;
  message: string;
  detail: string | null;
};

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './orders.html',
  styleUrls: ['./orders.css'],
})
export class Orders {
  accountId = signal('');
  symbol = signal('');
  side = signal<PlaceOrderRequest.SideEnum>(PlaceOrderRequest.SideEnum.Buy);
  quantity = signal('');
  price = signal('');

  submitting = signal(false);
  validationMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  apiError = signal<UiError | null>(null);
  resultPopupOpen = signal(false);
  resultPopupTitle = signal('');
  resultPopupMessage = signal('');
  resultPopupCode = signal<string | null>(null);
  resultPopupDetail = signal<string | null>(null);

  quantityFieldError = signal<string | null>(null);
  priceFieldError = signal<string | null>(null);

  conversionLoading = signal(false);
  conversionNote = signal<string | null>(null);
  usdEstimate = signal<QuoteToUsdEstimate | null>(null);

  readonly tradeCurrency = computed<TradeCurrency>(() => this.inferTradeCurrency(this.symbol().trim().toUpperCase()));

  private conversionDebounceId: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly auth: Auth,
    private readonly orderApi: OrderControllerService,
    private readonly conversion: CurrencyConversion,
  ) {
    this.accountId.set(this.readAccountIdFromToken());
  }

  onSymbolInput(value: string): void {
    this.clearUiMessages();
    this.symbol.set(value.toUpperCase());
    this.scheduleUsdEstimateRefresh();
  }

  onQuantityInput(value: string): void {
    this.clearUiMessages();
    this.quantity.set(value);
    this.quantityFieldError.set(this.validateQuantityField(value));
    this.scheduleUsdEstimateRefresh();
  }

  onPriceInput(value: string): void {
    this.clearUiMessages();
    this.price.set(value);
    this.priceFieldError.set(this.validatePriceField(value));
    this.scheduleUsdEstimateRefresh();
  }

  onSideInput(value: PlaceOrderRequest.SideEnum): void {
    this.clearUiMessages();
    this.side.set(value);
  }

  async submit(): Promise<void> {
    this.validationMessage.set(null);
    this.apiError.set(null);
    this.successMessage.set(null);

    const validationError = this.validateOrder();
    if (validationError) {
      this.validationMessage.set(validationError);
      return;
    }

    const accountId = this.accountId().trim();
    const symbol = this.symbol().trim().toUpperCase();
    const quantity = Number(this.quantity());
    const price = Number(this.price());

    this.submitting.set(true);
    try {
      const response = await firstValueFrom(
        this.orderApi.placeOrder({
          accountId,
          symbol,
          side: this.side(),
          quantity,
          price,
          idempotencyKey: this.generateIdempotencyKey(),
        }),
      );

      const returnedStatus = response?.status ?? 'UNKNOWN';
      const backendMessage = typeof response?.message === 'string' ? response.message.trim() : '';
      const suffix = backendMessage ? ` ${backendMessage}` : '';
      this.successMessage.set(`Order has been placed`);
      this.resultPopupTitle.set('Order Submitted');
      this.resultPopupMessage.set(`Order has been placed.`);
      this.resultPopupCode.set(returnedStatus);
      this.resultPopupDetail.set(backendMessage || null);
      this.resultPopupOpen.set(true);
    } catch (error: any) {
      const mappedError = this.mapErrorToMessage(error);
      this.apiError.set(mappedError);
      this.resultPopupTitle.set('Order Not Placed');
      this.resultPopupMessage.set(mappedError.message);
      this.resultPopupCode.set(mappedError.code);
      this.resultPopupDetail.set(mappedError.detail);
      this.resultPopupOpen.set(true);
    } finally {
      this.submitting.set(false);
    }
  }

  closeResultPopup(): void {
    this.resultPopupOpen.set(false);
  }

  formatMoney(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2,
      minimumFractionDigits: 2,
    }).format(value);
  }

  estimateVisible(): boolean {
    return this.tradeCurrency() !== 'USD' && this.tradeCurrency() !== 'UNKNOWN' && this.orderTotal() > 0;
  }

  orderTotal(): number {
    const qty = Number(this.quantity());
    const px = Number(this.price());
    if (!Number.isFinite(qty) || !Number.isFinite(px)) {
      return 0;
    }
    return qty * px;
  }

  private validateOrder(): string | null {
    if (!this.accountId().trim()) {
      return 'No account is available in this session. Please sign in again.';
    }

    const symbol = this.symbol().trim().toUpperCase();
    if (!this.isSupportedSymbolShape(symbol)) {
      return 'Symbol format is not valid.';
    }

    const quantityError = this.validateQuantityField(this.quantity());
    this.quantityFieldError.set(quantityError);
    if (quantityError) {
      return quantityError;
    }

    const priceError = this.validatePriceField(this.price());
    this.priceFieldError.set(priceError);
    if (priceError) {
      return priceError;
    }

    return null;
  }

  private isSupportedSymbolShape(symbol: string): boolean {
    if (!symbol || symbol.length > 20) {
      return false;
    }
    return (
      SYMBOL_PLAIN_TICKER.test(symbol) ||
      SYMBOL_SUFFIXED_TICKER.test(symbol) ||
      SYMBOL_FX.test(symbol) ||
      SYMBOL_CRYPTO.test(symbol)
    );
  }

  private inferTradeCurrency(symbol: string): TradeCurrency {
    if (!symbol) {
      return 'UNKNOWN';
    }

    if (SYMBOL_SUFFIXED_TICKER.test(symbol)) {
      if (symbol.endsWith('.NS') || symbol.endsWith('.BO')) {
        return 'INR';
      }
      return 'UNKNOWN';
    }

    if (SYMBOL_FX.test(symbol)) {
      const pair = symbol.slice(3);
      const quote = pair.slice(3);
      if (quote === 'USD') {
        return 'USD';
      }
      if (quote === 'INR') {
        return 'INR';
      }
      return 'UNKNOWN';
    }

    if (SYMBOL_PLAIN_TICKER.test(symbol)) {
      return 'USD';
    }

    return 'UNKNOWN';
  }

  private scheduleUsdEstimateRefresh(): void {
    if (this.conversionDebounceId) {
      clearTimeout(this.conversionDebounceId);
    }

    this.conversionDebounceId = setTimeout(() => {
      void this.refreshUsdEstimate();
    }, 250);
  }

  private async refreshUsdEstimate(): Promise<void> {
    this.usdEstimate.set(null);
    this.conversionNote.set(null);

    if (!this.estimateVisible()) {
      return;
    }

    const currency = this.tradeCurrency();
    if (currency === 'USD' || currency === 'UNKNOWN') {
      return;
    }

    this.conversionLoading.set(true);
    try {
      const estimate = await this.conversion.quoteTotalToUsd(this.orderTotal(), currency);
      this.usdEstimate.set(estimate);
    } catch {
      this.conversionNote.set('USD estimate is temporarily unavailable. You can still place the order.');
    } finally {
      this.conversionLoading.set(false);
    }
  }

  private mapErrorToMessage(error: any): UiError {
    if (error?.status === 0) {
      return {
        code: 'NETWORK-0',
        message: 'Could not reach the trade service.',
        detail: 'This is usually a service outage or a CORS configuration issue.',
      };
    }

    const backendMessage =
      typeof error?.error?.message === 'string' && error.error.message.trim().length > 0
        ? error.error.message.trim()
        : null;

    const code = error?.error?.errorCode;
    if (typeof code === 'string' && ORDER_ERROR_MESSAGES[code]) {
      return {
        code,
        message: ORDER_ERROR_MESSAGES[code],
        detail: backendMessage,
      };
    }

    return {
      code: typeof code === 'string' ? code : `HTTP-${error?.status ?? 'UNKNOWN'}`,
      message: 'The order could not be placed right now.',
      detail: backendMessage,
    };
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

  private generateIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `oid-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  }

  private clearUiMessages(): void {
    this.validationMessage.set(null);
    this.apiError.set(null);
    this.successMessage.set(null);
  }

  private validateQuantityField(rawValue: string): string | null {
    const quantityRaw = rawValue.trim();
    if (!quantityRaw) {
      return null;
    }

    if (!/^\d+$/.test(quantityRaw)) {
      return 'Quantity must be a whole number greater than zero.';
    }

    const quantityValue = Number(quantityRaw);
    if (!Number.isInteger(quantityValue) || quantityValue <= 0) {
      return 'Quantity must be a whole number greater than zero.';
    }

    return null;
  }

  private validatePriceField(rawValue: string): string | null {
    const priceRaw = rawValue.trim();
    if (!priceRaw) {
      return null;
    }

    if (!/^\d+(\.\d{1,2})?$/.test(priceRaw)) {
      return 'Price must be greater than zero with at most two decimal places.';
    }

    const priceValue = Number(priceRaw);
    if (!Number.isFinite(priceValue) || priceValue <= 0) {
      return 'Price must be greater than zero with at most two decimal places.';
    }

    return null;
  }
}
