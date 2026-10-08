import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnDestroy, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ToastrService } from 'ngx-toastr';
import { Auth } from '../../core/services/auth';
import { OrderControllerService } from '../../generated/api-client/api/orderController.service';
import { PlaceOrderRequest } from '../../generated/api-client/model/placeOrderRequest';
import { CurrencyConversion, QuoteToUsdEstimate } from '../../core/services/currency-conversion';
import { AccountControllerService } from '../../generated/api-client/api/accountController.service';
import { PositionResponse } from '../../generated/api-client/model/positionResponse';

type TradeCurrency = 'USD' | 'INR' | 'UNKNOWN';

const SYMBOL_PLAIN_TICKER = /^[A-Z][A-Z0-9]{0,19}$/;
const SYMBOL_SUFFIXED_TICKER = /^[A-Z][A-Z0-9]{0,12}\.[A-Z0-9]{1,6}$/;
const SYMBOL_FX = /^FX:[A-Z]{6}$/;
const SYMBOL_CRYPTO = /^X:[A-Z0-9]{2,15}$/;
const SYMBOL_CRYPTO_DASH = /^[A-Z0-9]{2,15}-[A-Z]{3,6}$/;

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

type SignalQuoteResponse = {
  metrics?: {
    price?: number;
  } | null;
};

type MarketPerformanceVisualizationResponse = {
  stocks?: Array<{
    symbol: string;
    price: number;
  }>;
};

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './orders.html',
  styleUrls: ['./orders.css'],
})
export class Orders implements OnDestroy {
  private static readonly DEFAULT_MAX_QUANTITY = 100;

  accountId = signal('');
  symbol = signal('');
  side = signal<PlaceOrderRequest.SideEnum>(PlaceOrderRequest.SideEnum.Buy);
  quantity = signal(1);
  price = signal('');
  marketPrice = signal<number | null>(null);
  instrumentDisplayName = signal<string | null>(null);
  availableCash = signal<number | null>(null);
  availableHoldingQuantity = signal<number | null>(null);
  positionLoading = signal(false);
  balanceLoading = signal(false);

  submitting = signal(false);
  validationMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  apiError = signal<UiError | null>(null);
  resultPopupOpen = signal(false);
  resultPopupTitle = signal('');
  resultPopupMessage = signal('');
  resultPopupCode = signal<string | null>(null);
  resultPopupDetail = signal<string | null>(null);
  isProfileMenuOpen = signal(false);
  isNavMenuOpen = signal(false);

  quantityFieldError = signal<string | null>(null);
  priceFieldError = signal<string | null>(null);

  conversionLoading = signal(false);
  conversionNote = signal<string | null>(null);
  usdEstimate = signal<QuoteToUsdEstimate | null>(null);

  readonly tradeCurrency = computed<TradeCurrency>(() => this.inferTradeCurrency(this.symbol().trim().toUpperCase()));
  readonly sideLabel = computed(() => this.side() === PlaceOrderRequest.SideEnum.Buy ? 'BUY' : 'SELL');
  readonly submitLabel = computed(() =>
    this.submitting()
      ? 'Placing order...'
      : `PLACE ${this.side() === PlaceOrderRequest.SideEnum.Buy ? 'BUY' : 'SELL'} ORDER`
  );
  readonly orderIntentHint = computed(() =>
    this.side() === PlaceOrderRequest.SideEnum.Buy
      ? 'Buy orders attempt to execute at the limit price or better.'
      : 'Sell orders attempt to execute at the limit price or better for you as the seller.'
  );
  readonly isSubmitDisabled = computed(() => this.submitting() || !!this.validateOrder());

  private conversionDebounceId: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly auth: Auth,
    private readonly orderApi: OrderControllerService,
    private readonly http: HttpClient,
    private readonly accountApi: AccountControllerService,
    private readonly conversion: CurrencyConversion,
    private readonly toastr: ToastrService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly elementRef: ElementRef<HTMLElement>,
  ) {
    this.accountId.set(this.readAccountIdFromToken());
    this.applyRoutePrefill();
    void this.loadPositionForSymbol();
    void this.loadBalance();
    void this.loadMarketPriceFromSignal();
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

  onSymbolInput(value: string): void {
    this.clearUiMessages();
    this.symbol.set(value.toUpperCase().trim());
    this.marketPrice.set(null);
    this.instrumentDisplayName.set(null);
    this.availableHoldingQuantity.set(null);
    void this.loadPositionForSymbol();
    void this.loadMarketPriceFromSignal();
    this.scheduleUsdEstimateRefresh();
  }

  onQuantityInput(value: string | number): void {
    this.clearUiMessages();
    const parsed = this.parseQuantity(value);
    this.quantity.set(parsed);
    this.quantityFieldError.set(this.validateQuantityField(parsed));
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
    this.quantityFieldError.set(this.validateQuantityField(this.quantity()));
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
    const quantity = this.quantity();
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
        }, 'body', false, { httpHeaderAccept: 'application/json' as any }),
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
      this.toastr.success(
        'A confirmation email will be sent to your registered address.',
        'Order placed successfully',
      );
    } catch (error: any) {
      const mappedError = await this.mapErrorToMessage(error);
      this.apiError.set(mappedError);
      this.resultPopupTitle.set('Order Not Placed');
      this.resultPopupMessage.set(mappedError.message);
      this.resultPopupCode.set(mappedError.code);
      this.resultPopupDetail.set(mappedError.detail);
      this.resultPopupOpen.set(true);
      this.toastr.error(mappedError.message, 'Order failed');
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

  getCurrentMarketPriceText(): string {
    const livePrice = this.marketPrice();
    if (typeof livePrice === 'number' && Number.isFinite(livePrice) && livePrice > 0) {
      return this.formatMoney(livePrice);
    }
    return 'Unavailable';
  }

  hasCurrentMarketPrice(): boolean {
    const livePrice = this.marketPrice();
    return typeof livePrice === 'number' && Number.isFinite(livePrice) && livePrice > 0;
  }

  estimateVisible(): boolean {
    return this.tradeCurrency() !== 'USD' && this.tradeCurrency() !== 'UNKNOWN' && this.orderTotal() > 0;
  }

  orderTotal(): number {
    const qty = this.quantity();
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
    if (quantityError) {
      return quantityError;
    }

    const priceError = this.validatePriceField(this.price());
    if (priceError) {
      return priceError;
    }

    return null;
  }

  private applyRoutePrefill(): void {
    const params = this.route.snapshot.queryParamMap;
    const symbol = (params.get('symbol') ?? '').trim().toUpperCase();
    const side = (params.get('side') ?? '').trim().toUpperCase();
    const marketPriceRaw = (params.get('marketPrice') ?? '').trim();

    if (this.isSupportedSymbolShape(symbol)) {
      this.symbol.set(symbol);
    }

    if (side === PlaceOrderRequest.SideEnum.Buy || side === PlaceOrderRequest.SideEnum.Sell) {
      this.side.set(side as PlaceOrderRequest.SideEnum);
    }

    const marketPrice = Number(marketPriceRaw);
    if (Number.isFinite(marketPrice) && marketPrice > 0) {
      const rounded = Number(marketPrice.toFixed(2));
      this.marketPrice.set(rounded);
      this.price.set(rounded.toFixed(2));
    }
  }

  private async loadPositionForSymbol(): Promise<void> {
    const accountRef = this.accountId().trim();
    const symbol = this.symbol().trim().toUpperCase();
    if (!accountRef || !symbol) {
      this.availableHoldingQuantity.set(null);
      this.instrumentDisplayName.set(null);
      return;
    }

    this.positionLoading.set(true);
    try {
      const response = await firstValueFrom(this.accountApi.getPositions(accountRef));
      const positions = this.normalizePositionsResponse(response);
      const match = positions.find((position) =>
        (position.symbol ?? '').toUpperCase() === symbol
      );

      if (!match) {
        this.availableHoldingQuantity.set(0);
        this.instrumentDisplayName.set(null);
      } else {
        const quantity = Number(match.quantity ?? 0);
        const holdingQty = Number.isFinite(quantity) && quantity > 0 ? Math.floor(quantity) : 0;
        this.availableHoldingQuantity.set(holdingQty);
        this.instrumentDisplayName.set(match.symbol ?? null);
      }
    } catch {
      this.availableHoldingQuantity.set(null);
      this.instrumentDisplayName.set(null);
    } finally {
      this.positionLoading.set(false);
      this.quantityFieldError.set(this.validateQuantityField(this.quantity()));
    }
  }

  private async loadBalance(): Promise<void> {
    const accountRef = this.accountId().trim();
    if (!accountRef) {
      return;
    }

    this.balanceLoading.set(true);
    try {
      const response = await firstValueFrom(this.accountApi.getBalance(accountRef));
      const cash = Number(response.cashBalance);
      this.availableCash.set(Number.isFinite(cash) ? cash : null);
    } catch {
      this.availableCash.set(null);
    } finally {
      this.balanceLoading.set(false);
      this.quantityFieldError.set(this.validateQuantityField(this.quantity()));
    }
  }

  private async loadMarketPriceFromSignal(): Promise<void> {
    const symbol = this.symbol().trim().toUpperCase();
    if (!symbol) {
      this.marketPrice.set(null);
      return;
    }

    if (this.marketPrice() !== null) {
      return;
    }

    try {
      const signal = await firstValueFrom(
        this.http.get<SignalQuoteResponse>(`/api/v1/signals/${encodeURIComponent(symbol)}`)
      );
      const signalPrice = Number(signal?.metrics?.price);
      if (Number.isFinite(signalPrice) && signalPrice > 0) {
        const rounded = Number(signalPrice.toFixed(2));
        this.marketPrice.set(rounded);
        if (!this.price().trim()) {
          this.price.set(rounded.toFixed(2));
        }
        return;
      }

      await this.loadMarketPriceFromMarketPerformance(symbol);
    } catch {
      await this.loadMarketPriceFromMarketPerformance(symbol);
    }
  }

  private async loadMarketPriceFromMarketPerformance(symbol: string): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.http.get<MarketPerformanceVisualizationResponse>('/api/market-performance/visualization')
      );

      const stock = (response?.stocks ?? []).find((item) =>
        (item.symbol ?? '').toUpperCase() === symbol
      );

      const stockPrice = Number(stock?.price);
      if (Number.isFinite(stockPrice) && stockPrice > 0) {
        const rounded = Number(stockPrice.toFixed(2));
        this.marketPrice.set(rounded);
        if (!this.price().trim()) {
          this.price.set(rounded.toFixed(2));
        }
      } else {
        this.marketPrice.set(null);
      }
    } catch {
      this.marketPrice.set(null);
    }
  }

  private parseQuantity(value: string | number): number {
    if (typeof value === 'number') {
      return Number.isFinite(value) ? Math.floor(value) : 0;
    }
    const trimmed = value.trim();
    if (!trimmed) {
      return 0;
    }
    const parsed = Number(trimmed);
    if (!Number.isFinite(parsed)) {
      return 0;
    }
    return Math.floor(parsed);
  }

  private isSupportedSymbolShape(symbol: string): boolean {
    if (!symbol || symbol.length > 20) {
      return false;
    }
    return (
      SYMBOL_PLAIN_TICKER.test(symbol) ||
      SYMBOL_SUFFIXED_TICKER.test(symbol) ||
      SYMBOL_FX.test(symbol) ||
      SYMBOL_CRYPTO.test(symbol) ||
      SYMBOL_CRYPTO_DASH.test(symbol)
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

    if (SYMBOL_CRYPTO_DASH.test(symbol)) {
      const quote = symbol.split('-')[1];
      if (quote === 'USD') {
        return 'USD';
      }
      if (quote === 'INR') {
        return 'INR';
      }
      return 'UNKNOWN';
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

  private async mapErrorToMessage(error: any): Promise<UiError> {
    if (error?.status === 0) {
      return {
        code: 'NETWORK-0',
        message: 'Could not reach the trade service.',
        detail: 'This is usually a service outage or a CORS configuration issue.',
      };
    }

    const parsedError = await this.parseErrorPayload(error?.error);
    const backendMessage =
      typeof parsedError?.message === 'string' && parsedError.message.trim().length > 0
        ? parsedError.message.trim()
        : null;

    const code = typeof parsedError?.errorCode === 'string' ? parsedError.errorCode : null;
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

  private async parseErrorPayload(payload: unknown): Promise<{ errorCode?: string; message?: string } | null> {
    if (!payload) {
      return null;
    }

    if (payload instanceof Blob) {
      try {
        const text = await payload.text();
        return this.parseErrorPayload(text);
      } catch {
        return null;
      }
    }

    if (typeof payload === 'string') {
      const trimmed = payload.trim();
      if (!trimmed) {
        return null;
      }
      try {
        const parsed = JSON.parse(trimmed) as { errorCode?: string; message?: string };
        return parsed;
      } catch {
        return { message: trimmed };
      }
    }

    if (typeof payload === 'object') {
      const obj = payload as { errorCode?: unknown; message?: unknown };
      return {
        errorCode: typeof obj.errorCode === 'string' ? obj.errorCode : undefined,
        message: typeof obj.message === 'string' ? obj.message : undefined,
      };
    }

    return null;
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

  private normalizePositionsResponse(response: unknown): PositionResponse[] {
    if (!response) {
      return [];
    }

    if (Array.isArray(response)) {
      return response as PositionResponse[];
    }

    if (response instanceof Blob) {
      return [];
    }

    if (typeof response === 'string') {
      try {
        const parsed = JSON.parse(response);
        return this.normalizePositionsResponse(parsed);
      } catch {
        return [];
      }
    }

    if (typeof response === 'object') {
      const record = response as Record<string, unknown>;
      const wrappedKeys = ['positions', 'items', 'data', 'content', 'results', 'payload', 'body'];
      for (const key of wrappedKeys) {
        const candidate = record[key];
        if (Array.isArray(candidate)) {
          return candidate as PositionResponse[];
        }
      }
    }

    return [];
  }

  private validateQuantityField(quantityValue: number): string | null {
    if (!Number.isInteger(quantityValue) || quantityValue < 1) {
      return 'Quantity must be a whole number greater than zero.';
    }

    if (this.side() === PlaceOrderRequest.SideEnum.Sell) {
      const holdingQty = this.availableHoldingQuantity();
      if (typeof holdingQty === 'number' && quantityValue > holdingQty) {
        return `Quantity cannot exceed available holdings (${holdingQty}).`;
      }
      if (typeof holdingQty === 'number' && holdingQty <= 0) {
        return 'No holdings available to sell for this instrument.';
      }
    }

    if (this.side() === PlaceOrderRequest.SideEnum.Buy) {
      const cash = this.availableCash();
      const px = Number(this.price());
      if (typeof cash === 'number' && Number.isFinite(px) && px > 0) {
        const total = Number((quantityValue * px).toFixed(2));
        if (total > cash) {
          return 'Quantity exceeds available cash for this limit price.';
        }
      }
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

  ngOnDestroy(): void {
    if (this.conversionDebounceId) {
      clearTimeout(this.conversionDebounceId);
      this.conversionDebounceId = null;
    }
  }
}
