import { ExecutionContext, HttpException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import { LoginThrottleGuard } from './login-throttle.guard';

describe('LoginThrottleGuard', () => {
  let guard: LoginThrottleGuard;

  const makeConfigService = (overrides?: Record<string, string>) =>
    ({
      get: (key: string) => overrides?.[key],
    } as ConfigService);

  const makeContext = (ip = '127.0.0.1', xForwardedFor?: string | string[]) =>
    ({
      switchToHttp: () => ({
        getRequest: () => ({
          ip,
          headers: {
            'x-forwarded-for': xForwardedFor,
          },
        }),
      }),
    } as unknown as ExecutionContext);

  beforeEach(() => {
    guard = new LoginThrottleGuard(
      makeConfigService({
        LOGIN_THROTTLE_MAX_ATTEMPTS: '2',
        LOGIN_THROTTLE_WINDOW_MS: '60000',
        LOGIN_THROTTLE_BLOCK_MS: '300000',
      }),
    );
  });

  beforeEach(() => {
    console.info(`[TEST START] ${expect.getState().currentTestName}`);
  });

  afterEach(() => {
    console.info(`[TEST END] ${expect.getState().currentTestName}`);
  });

  it('allows requests until max attempts is exceeded', () => {
    expect(guard.canActivate(makeContext())).toBe(true);
    expect(guard.canActivate(makeContext())).toBe(true);
  });

  it('blocks request when attempts exceed threshold', () => {
    guard.canActivate(makeContext());
    guard.canActivate(makeContext());

    expect(() => guard.canActivate(makeContext())).toThrow(HttpException);
  });

  it('tracks attempts by forwarded client ip', () => {
    guard.canActivate(makeContext('127.0.0.1', '10.0.0.1'));
    guard.canActivate(makeContext('127.0.0.1', '10.0.0.1'));

    expect(() =>
      guard.canActivate(makeContext('127.0.0.1', '10.0.0.1')),
    ).toThrow(HttpException);
  });

  it('does not share throttle state between different clients', () => {
    guard.canActivate(makeContext('127.0.0.1', '10.0.0.1'));
    guard.canActivate(makeContext('127.0.0.1', '10.0.0.1'));

    expect(() =>
      guard.canActivate(makeContext('127.0.0.1', '10.0.0.1')),
    ).toThrow(HttpException);

    expect(guard.canActivate(makeContext('127.0.0.1', '10.0.0.2'))).toBe(true);
  });

  it('falls back to request.ip when x-forwarded-for is missing', () => {
    expect(guard.canActivate(makeContext('192.168.1.10'))).toBe(true);
    expect(guard.canActivate(makeContext('192.168.1.10'))).toBe(true);
    expect(() => guard.canActivate(makeContext('192.168.1.10'))).toThrow(HttpException);
  });

  it('uses defaults when config values are invalid', () => {
    const defaultedGuard = new LoginThrottleGuard(
      makeConfigService({
        LOGIN_THROTTLE_MAX_ATTEMPTS: 'invalid',
        LOGIN_THROTTLE_WINDOW_MS: '-1',
        LOGIN_THROTTLE_BLOCK_MS: '0',
      }),
    );

    expect(defaultedGuard.canActivate(makeContext('172.16.0.1'))).toBe(true);
  });
});
