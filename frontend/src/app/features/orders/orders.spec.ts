import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Orders } from './orders';
import { Auth } from '../../core/services/auth';
import { OrderControllerService } from '../../generated/api-client/api/orderController.service';
import { CurrencyConversion } from '../../core/services/currency-conversion';

describe('Orders', () => {
  let fixture: ComponentFixture<Orders>;
  let component: Orders;

  const orderApiSpy = {
    placeOrder: vi.fn(),
  };

  const conversionSpy = {
    quoteTotalToUsd: vi.fn(),
  };

  const authStub = {
    token: makeJwt({ accountId: '1' }),
  };

  beforeEach(async () => {
    orderApiSpy.placeOrder.mockReset();
    conversionSpy.quoteTotalToUsd.mockReset();

    await TestBed.configureTestingModule({
      imports: [Orders, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: Auth, useValue: authStub },
        { provide: OrderControllerService, useValue: orderApiSpy },
        { provide: CurrencyConversion, useValue: conversionSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Orders);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('a valid order submits and shows the returned status', async () => {
    orderApiSpy.placeOrder.mockReturnValue(of({ status: 'NEW' }));

    component.onSymbolInput('AAPL');
    component.side.set('BUY');
    component.onQuantityInput('10');
    component.onPriceInput('125.50');

    await component.submit();

    expect(orderApiSpy.placeOrder).toHaveBeenCalledTimes(1);
    expect(component.successMessage()).toBe('Order accepted with status NEW.');
    expect(component.apiError()).toBeNull();
  });

  it('an invalid quantity is blocked before submission', async () => {
    component.onSymbolInput('AAPL');
    component.onQuantityInput('0');
    component.onPriceInput('101.25');

    await component.submit();

    expect(orderApiSpy.placeOrder).not.toHaveBeenCalled();
    expect(component.validationMessage()).toBe('Quantity must be a whole number greater than zero.');
  });

  it('a business-rule rejection shows the mapped message', async () => {
    orderApiSpy.placeOrder.mockReturnValue(
      throwError(() => ({ status: 409, error: { errorCode: 'ORD-409', message: 'Duplicate idempotency key' } })),
    );

    component.onSymbolInput('AAPL');
    component.onQuantityInput('5');
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
