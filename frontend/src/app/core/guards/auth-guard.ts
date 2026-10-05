import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from '../services/auth';

export const DEFAULT_POST_LOGIN_PATH = '/dashboard';

export function toSafeReturnPath(
  candidate: string | null | undefined,
  fallback: string = DEFAULT_POST_LOGIN_PATH,
): string {
  const value = candidate?.trim();
  if (!value) {
    return fallback;
  }

  if (value.startsWith('/')) {
    if (value.startsWith('//')) {
      return fallback;
    }
    return value;
  }

  try {
    const parsed = new URL(value, window.location.origin);
    if (parsed.origin !== window.location.origin) {
      return fallback;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return fallback;
  }
}

export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(Auth);
  const router = inject(Router);

  return auth.getValidAccessToken().then((token) => {
    if (token) {
      return true;
    }

    const returnUrl = toSafeReturnPath(state.url);
    return router.createUrlTree(['/home'], { queryParams: { returnUrl } });
  });
};
