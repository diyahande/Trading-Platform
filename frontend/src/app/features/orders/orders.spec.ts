import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ToastrService } from 'ngx-toastr';

import { Orders } from './orders';
import { Auth } from '../../core/services/auth';
import { OrderControllerService } from '../../generated/api-client/api/orderController.service';
import { CurrencyConversion } from '../../core/services/currency-conversion';
import { AccountControllerService } from '../../generated/api-client/api/accountController.service';
import { PlaceOrderRequest } from '../../generated/api-client/model/placeOrderRequest';

describe('Orders', () => {
  let fixture: ComponentFixture<Orders>;
  let component: Orders;

  const orderApiSpy = {
    placeOrder: vi.fn(),
  };

  const conversionSpy = {
    quoteTotalToUsd: vi.fn(),
  };

  const accountApiSpy = {
    getPositions: vi.fn(),
    getBalance: vi.fn(),
  };

  const toastrSpy = {
    success: vi.fn(),
    error: vi.fn(),
  };

  const authStub = {
    token: makeJwt({ accountId: '1' }),
  };

  beforeEach(async () => {
    orderApiSpy.placeOrder.mockReset();
    conversionSpy.quoteTotalToUsd.mockReset();
    accountApiSpy.getPositions.mockReset();
    accountApiSpy.getBalance.mockReset();
    toastrSpy.success.mockReset();
    toastrSpy.error.mockReset();

    accountApiSpy.getPositions.mockReturnValue(of([{ symbol: 'AAPL', quantity: 40, averageCost: 120 }]));
    accountApiSpy.getBalance.mockReturnValue(of({ cashBalance: 10_000 }));

    await TestBed.configureTestingModule({
      imports: [Orders, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: Auth, useValue: authStub },
        { provide: OrderControllerService, useValue: orderApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => ({
                  symbol: 'CVX',
                  side: 'BUY',
                  marketPrice: '207.20',
                } as Record<string, string | null>)[key] ?? null,
              },
            },
          },
        },
        { provide: AccountControllerService, useValue: accountApiSpy },
        { provide: CurrencyConversion, useValue: conversionSpy },
        { provide: ToastrService, useValue: toastrSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Orders);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('prefills symbol, side, and market/limit price from route query params', () => {
    expect(component.symbol()).toBe('CVX');
    expect(component.side()).toBe('BUY');
    expect(component.marketPrice()).toBe(207.2);
    expect(component.price()).toBe('207.20');
  });

  it('a valid order submits and shows the returned status', async () => {
    orderApiSpy.placeOrder.mockReturnValue(of({ status: 'NEW' }));

    component.onSymbolInput('AAPL');
    component.side.set('BUY');
    component.onQuantityInput(10);
    component.onPriceInput('125.50');

    await component.submit();

    expect(orderApiSpy.placeOrder).toHaveBeenCalledTimes(1);
    expect(orderApiSpy.placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        symbol: 'AAPL',
        side: PlaceOrderRequest.SideEnum.Buy,
        quantity: 10,
        price: 125.5,
      }),
      'body',
      false,
      { httpHeaderAccept: 'application/json' }
    );
    expect(component.successMessage()).toBe('Order has been placed');
    expect(component.apiError()).toBeNull();
  });

  it('an invalid quantity is blocked before submission', async () => {
    component.onSymbolInput('AAPL');
    component.onQuantityInput(0);
    component.onPriceInput('101.25');

    await component.submit();

    expect(orderApiSpy.placeOrder).not.toHaveBeenCalled();
    expect(component.validationMessage()).toBe('Quantity must be a whole number greater than zero.');
  });

  it('invalid limit price is blocked before submission', async () => {
    component.onSymbolInput('AAPL');
    component.onQuantityInput(5);
    component.onPriceInput('0');

    await component.submit();

    expect(orderApiSpy.placeOrder).not.toHaveBeenCalled();
    expect(component.validationMessage()).toBe('Price must be greater than zero with at most two decimal places.');
  });

  it('SELL order is capped by holdings and submits correct payload', async () => {
    orderApiSpy.placeOrder.mockReturnValue(of({ status: 'NEW' }));
    accountApiSpy.getPositions.mockReturnValue(of([{ symbol: 'CVX', quantity: 8, averageCost: 200 }]));

    component.onSymbolInput('CVX');
    component.onSideInput(PlaceOrderRequest.SideEnum.Sell);
    component.onPriceInput('205.00');
    component.onQuantityInput(12);

    expect(component.quantity()).toBe(8);

    await component.submit();

    expect(orderApiSpy.placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        symbol: 'CVX',
        side: PlaceOrderRequest.SideEnum.Sell,
        quantity: 8,
        price: 205,
      }),
      'body',
      false,
      { httpHeaderAccept: 'application/json' }
    );
  });

  it('blocks duplicate submissions while request is in flight', async () => {
    const neverSettlingPromise = new Promise<never>(() => {
      // Intentionally unresolved to keep submitting state true during this assertion.
    });
    orderApiSpy.placeOrder.mockReturnValue(of(neverSettlingPromise as any));

    component.onSymbolInput('AAPL');
    component.onQuantityInput(3);
    component.onPriceInput('100.00');

    const first = component.submit();
    const second = component.submit();

    await Promise.all([first, second]);

    expect(orderApiSpy.placeOrder).toHaveBeenCalledTimes(1);
  });

  it('a business-rule rejection shows the mapped message', async () => {
    orderApiSpy.placeOrder.mockReturnValue(
      throwError(() => ({ status: 409, error: { errorCode: 'ORD-409', message: 'Duplicate idempotency key' } })),
    );

    component.onSymbolInput('AAPL');
    component.onQuantityInput(5);
    component.onPriceInput('99.90');

    await component.submit();

    expect(component.apiError()).toEqual({
      code: 'ORD-409',
      message: 'Not enough holdings to sell, or this order was already placed.',
      detail: 'Duplicate idempotency key',
    });
    expect(component.successMessage()).toBeNull();
  });
});

function makeJwt(payload: Record<string, unknown>): string {
  const encode = (value: string) =>
    btoa(value)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/g, '');

  return `${encode(JSON.stringify({ alg: 'none', typ: 'JWT' }))}.${encode(
    JSON.stringify(payload),
  )}.sig`;
}
