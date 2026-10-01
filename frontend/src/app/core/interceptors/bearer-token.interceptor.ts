import { HttpInterceptorFn } from '@angular/common/http';
import { inject, InjectionToken } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { Auth } from '../services/auth';

export type AuthHeaderPolicy = {
  allowedOrigins: string[];
  unauthenticatedAuthPaths: string[];
};

export const AUTH_HEADER_POLICY = new InjectionToken<AuthHeaderPolicy>('AUTH_HEADER_POLICY');

function isAllowedRequest(url: string, policy: AuthHeaderPolicy): boolean {
  let parsed: URL;
  try {
    parsed = new URL(url, window.location.origin);
  } catch {
    return false;
  }

  const originAllowed = policy.allowedOrigins.includes(parsed.origin);
  if (!originAllowed) {
    return false;
  }

  const isUnauthenticatedAuthRoute = policy.unauthenticatedAuthPaths.some((path) => parsed.pathname === path);
  return !isUnauthenticatedAuthRoute;
}

export const bearerTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const policy = inject(AUTH_HEADER_POLICY);
  const auth = inject(Auth);

  if (!isAllowedRequest(req.url, policy)) {
    return next(req);
  }

  return from(auth.getValidAccessToken()).pipe(
    switchMap((token) => {
      if (!token) {
        return next(req);
      }

      return next(
        req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        }),
      );
    }),
  );
};
