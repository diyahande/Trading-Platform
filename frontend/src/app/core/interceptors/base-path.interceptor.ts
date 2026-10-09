import { HttpInterceptorFn } from '@angular/common/http';
import { APP_ENV } from '../config/app-env.generated';

const TRADE_PATH_PREFIXES = [
  '/api/v1',
  '/api/dashboard',
  '/api/portfolio-allocation',
  '/api/market-overview',
  '/api/market-performance',
  '/api/top-movers',
];

/** Backend base URL that serves the given path, or undefined when no backend owns it. */
function baseUrlForPath(path: string): string | undefined {
  if (path.startsWith('/auth')) return APP_ENV.authApiUrl;
  if (TRADE_PATH_PREFIXES.some((prefix) => path.startsWith(prefix))) return APP_ENV.tradeApiUrl;
  return undefined;
}

/**
 * Routes API requests to the correct backend service based on the endpoint path.
 *
 * - Auth endpoints (/auth/*) → AUTH_SERVICE_URL (auth-service)
 * - Trade endpoints (/api/v1/*, /api/dashboard, /api/portfolio-allocation, /api/market-overview, /api/market-performance, /api/top-movers) → TRADE_API_URL (trade-api)
 */
export const basePathInterceptor: HttpInterceptorFn = (req, next) => {
  let modifiedUrl = req.url;

  if (req.url.startsWith('/')) {
    // Relative URL: prefix with the owning backend
    const base = baseUrlForPath(req.url);
    if (base) modifiedUrl = `${base}${req.url}`;
  } else if (req.url.startsWith(APP_ENV.authApiUrl) || req.url.startsWith(APP_ENV.tradeApiUrl)) {
    // Already routed to a backend, keep as is
  } else if (req.url.includes('localhost')) {
    // Other local URLs: re-route by path
    const urlObj = new URL(req.url);
    const base = baseUrlForPath(urlObj.pathname);
    if (base) modifiedUrl = `${base}${urlObj.pathname}${urlObj.search}`;
  }

  if (modifiedUrl !== req.url) {
    req = req.clone({ url: modifiedUrl });
  }

  return next(req);
};
