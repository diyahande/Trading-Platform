import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Blotter } from './blotter';
import { Auth } from '../../core/services/auth';
import { OrderControllerService } from '../../generated/api-client/api/orderController.service';

describe('Blotter', () => {
  let fixture: ComponentFixture<Blotter>;
  let component: Blotter;

  const orderApiSpy = {
    getOrders: vi.fn(),
  };

  const authStub = {
    token: makeJwt({ accountId: 'ACC-1001' }),
    getValidAccessToken: vi.fn().mockResolvedValue('token-value'),
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    orderApiSpy.getOrders.mockReset();

    await TestBed.configureTestingModule({
      imports: [Blotter, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: Auth, useValue: authStub },
        { provide: OrderControllerService, useValue: orderApiSpy },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('order history renders newest first including rejections', async () => {
    orderApiSpy.getOrders.mockReturnValue(
      of([
        {
          orderId: 'old-filled',
          createdOn: '2026-10-01T09:00:00Z',
          status: 'FILLED',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 10,
          price: 100,
        },
        {
          orderId: 'new-rejected',
          createdOn: '2026-10-03T09:00:00Z',
          status: 'REJECTED',
          symbol: 'INFY.NS',
          side: 'SELL',
          quantity: 5,
          price: 200,
        },
      ]),
    );

    fixture = TestBed.createComponent(Blotter);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    await flushMicrotasks();
    await flushMicrotasks();
    await flushMicrotasks();
    fixture.detectChanges();

    expect(component.orders().map((order) => order.orderId)).toEqual(['new-rejected', 'old-filled']);

    const content = fixture.nativeElement.textContent as string;
    expect(content).toContain('REJECTED');
  });

  it('an order at NEW is shown as still working', async () => {
    orderApiSpy.getOrders.mockReturnValue(
      of([
        {
          orderId: 'pending-order',
          createdOn: '2026-10-03T09:00:00Z',
          status: 'NEW',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 1,
          price: 50,
        },
      ]),
    );

    fixture = TestBed.createComponent(Blotter);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    await flushMicrotasks();
    await flushMicrotasks();
    await flushMicrotasks();
    fixture.detectChanges();

    expect(component.workingMessage()).toContain('Auto refreshes every');
    expect(fixture.nativeElement.textContent as string).toContain('Auto refreshes every');
  });

  it('the re-read stops when nothing is at NEW', async () => {
    orderApiSpy.getOrders.mockReturnValue(
      of([
        {
          orderId: 'done-order',
          createdOn: '2026-10-03T09:00:00Z',
          status: 'FILLED',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 1,
          price: 50,
        },
      ]),
    );

    fixture = TestBed.createComponent(Blotter);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    await flushMicrotasks();
    await flushMicrotasks();
    await flushMicrotasks();

    expect(orderApiSpy.getOrders).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(20_000);
    await flushMicrotasks();

    expect(orderApiSpy.getOrders).toHaveBeenCalledTimes(1);
    expect(component.workingMessage()).toBeNull();
  });

  it('empty history is handled gracefully', async () => {
    orderApiSpy.getOrders.mockReturnValue(of([]));

    fixture = TestBed.createComponent(Blotter);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    await flushMicrotasks();
    await flushMicrotasks();
    await flushMicrotasks();
    fixture.detectChanges();

    expect(component.orders()).toEqual([]);
    expect(fixture.nativeElement.textContent as string).toContain('No orders yet');
  });
});

function makeJwt(payload: Record<string, unknown>): string {
  const encode = (value: string) =>
    btoa(value)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/g, '');

  return `${encode(JSON.stringify({ alg: 'none', typ: 'JWT' }))}.${encode(JSON.stringify(payload))}.sig`;
}

function flushMicrotasks(): Promise<void> {
  return Promise.resolve();
}
