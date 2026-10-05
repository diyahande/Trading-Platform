import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { authGuard, toSafeReturnPath } from './auth-guard';
import { Auth } from '../services/auth';

describe('authGuard', () => {
  const route = {} as any;

  it('the guard blocks unauthenticated navigation and redirects', async () => {
    const authSpy = {
      getValidAccessToken: vi.fn().mockResolvedValue(null),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: authSpy },
        provideRouter([]),
      ],
    });

    const result = await TestBed.runInInjectionContext(() =>
      authGuard(route, { url: '/dashboard?tab=positions' } as any),
    );

    expect(result instanceof UrlTree).toBeTruthy();
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(result as UrlTree)).toBe('/home?returnUrl=%2Fdashboard%3Ftab%3Dpositions');
  });

  it('the guard allows authenticated navigation', async () => {
    const authSpy = {
      getValidAccessToken: vi.fn().mockResolvedValue('access-token'),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: authSpy },
        provideRouter([]),
      ],
    });

    const result = await TestBed.runInInjectionContext(() =>
      authGuard(route, { url: '/dashboard' } as any),
    );

    expect(result).toBe(true);
  });
});

describe('toSafeReturnPath', () => {
  it('an off-origin return address is refused', () => {
    expect(toSafeReturnPath('https://evil.example/phish')).toBe('/dashboard');
    expect(toSafeReturnPath('//evil.example/phish')).toBe('/dashboard');
  });
});